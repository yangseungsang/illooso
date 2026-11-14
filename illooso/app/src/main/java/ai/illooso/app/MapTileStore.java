package ai.illooso.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * On-demand OSM raster tiles. Call {@link #get} only for tiles in the visible map viewport.
 *
 * <p>No region download, adjacent-tile prefetch or location request is performed. All disk and
 * network work is off the UI thread. Cached tiles are retained for at least seven days; expired
 * tiles are conditionally revalidated while the previous bitmap remains usable.
 */
public final class MapTileStore implements AutoCloseable {
  private static final String TILE_URL = "https://tile.openstreetmap.org/";
  private static final String USER_AGENT = "IlloosoPleosPrototype/1.6 (ai.illooso.app)";
  private static final long CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000;
  private static final long RETRY_BASE_MS = 30_000;
  private static final long MAX_RETRY_MS = 15 * 60_000;
  private static final int MAX_PENDING = 32;
  private static final int MAX_TILE_BYTES = 2 * 1024 * 1024;

  private final Object lock = new Object();
  private final File cacheDirectory;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Runnable onChanged;
  private final Runnable notifyChanged;
  private final Set<String> pending = new HashSet<>();
  private final Map<String, Failure> failures = new HashMap<>();
  private final Set<HttpURLConnection> connections = new HashSet<>();
  private final ExecutorService executor =
      Executors.newFixedThreadPool(
          2,
          task -> {
            Thread worker = new Thread(task, "illooso-map-tile");
            worker.setDaemon(true);
            return worker;
          });
  private final LruCache<String, Bitmap> memory =
      new LruCache<>(32 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
          return value.getAllocationByteCount();
        }
      };
  private boolean closed;

  public MapTileStore(Context context, Runnable onChanged) {
    // getCacheDir supplies the path; tile files and metadata are read/written by workers only.
    cacheDirectory = new File(context.getApplicationContext().getCacheDir(), "osm-raster-v1");
    this.onChanged = onChanged;
    notifyChanged =
        () -> {
          synchronized (lock) {
            if (closed) return;
          }
          if (this.onChanged != null) this.onChanged.run();
        };
  }

  /** Returns a cached bitmap immediately, or null while the requested visible tile loads. */
  public Bitmap get(int zoom, int x, int y) {
    if (zoom < 0 || zoom > 19) return null;
    int count = 1 << zoom;
    if (y < 0 || y >= count) return null;
    int wrappedX = Math.floorMod(x, count);
    String key = zoom + "_" + wrappedX + "_" + y;
    synchronized (lock) {
      if (closed) return null;
      Bitmap cached = memory.get(key);
      Failure failure = failures.get(key);
      boolean retryReady = failure == null || SystemClock.elapsedRealtime() >= failure.retryAt;
      if ((cached == null || failure != null)
          && retryReady
          && !pending.contains(key)
          && pending.size() < MAX_PENDING) {
        pending.add(key);
        try {
          executor.execute(() -> load(key, zoom, wrappedX, y));
        } catch (RejectedExecutionException ignored) {
          pending.remove(key);
        }
      }
      return cached;
    }
  }

  public boolean loading() {
    synchronized (lock) {
      return !closed && !pending.isEmpty();
    }
  }

  /** A previous tile request failed; cached map portions can still be available. */
  public boolean hasFailure() {
    synchronized (lock) {
      return !closed && !failures.isEmpty();
    }
  }

  /** A user-initiated retry. Subsequent get calls retry only the currently visible failed tiles. */
  public void retryFailed() {
    synchronized (lock) {
      if (closed) return;
      for (Map.Entry<String, Failure> entry : failures.entrySet())
        entry.setValue(new Failure(entry.getValue().attempts, 0));
    }
    changed();
  }

  private void load(String key, int zoom, int x, int y) {
    HttpURLConnection connection = null;
    long retryAfterMs = 0;
    try {
      if (isClosed()) return;
      File bitmapFile = new File(cacheDirectory, key + ".png");
      File metadataFile = new File(cacheDirectory, key + ".properties");
      Properties metadata = readMetadata(metadataFile);
      Bitmap cached = decodeFile(bitmapFile);
      if (cached != null) {
        put(key, cached);
        changed();
        long validatedAt = number(metadata.getProperty("validatedAt"), bitmapFile.lastModified());
        long age = System.currentTimeMillis() - validatedAt;
        if (age >= 0 && age < CACHE_TTL_MS) {
          succeeded(key);
          return;
        }
      }

      if (isClosed() || Thread.currentThread().isInterrupted()) return;
      connection =
          (HttpURLConnection)
              new URL(TILE_URL + zoom + "/" + x + "/" + y + ".png").openConnection();
      connection.setConnectTimeout(8_000);
      connection.setReadTimeout(10_000);
      connection.setRequestProperty("User-Agent", USER_AGENT);
      connection.setRequestProperty("Accept", "image/png");
      connection.setUseCaches(true);
      connection.setInstanceFollowRedirects(false);
      if (cached != null) {
        String etag = metadata.getProperty("etag", "");
        if (!etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);
        long modified = number(metadata.getProperty("lastModified"), 0);
        if (modified > 0) connection.setIfModifiedSince(modified);
      }
      synchronized (lock) {
        if (closed) return;
        connections.add(connection);
      }
      int status = connection.getResponseCode();
      if (status == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) {
        metadata.setProperty("validatedAt", Long.toString(System.currentTimeMillis()));
        saveMetadata(metadataFile, metadata);
        succeeded(key);
        return;
      }
      if (status != HttpURLConnection.HTTP_OK) {
        retryAfterMs = retryDelay(connection);
        throw new IOException("Tile HTTP " + status);
      }
      if (connection.getContentLengthLong() > MAX_TILE_BYTES)
        throw new IOException("Tile response is too large");
      byte[] bytes;
      try (InputStream input = connection.getInputStream()) {
        bytes = readTile(input);
      }
      Bitmap tile = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
      if (tile == null || tile.getWidth() != 256 || tile.getHeight() != 256)
        throw new IOException("Tile image is invalid");
      put(key, tile);
      metadata.setProperty("validatedAt", Long.toString(System.currentTimeMillis()));
      metadata.setProperty("etag", value(connection.getHeaderField("ETag")));
      metadata.setProperty("lastModified", Long.toString(connection.getLastModified()));
      // A cache write failure does not discard a successfully downloaded image.
      try {
        if (cacheDirectory.isDirectory() || cacheDirectory.mkdirs()) {
          writeAtomic(bitmapFile, bytes);
          saveMetadata(metadataFile, metadata);
        }
      } catch (IOException ignored) {
        // Android may reclaim its cache directory at any time.
      }
      succeeded(key);
    } catch (IOException | RuntimeException failure) {
      synchronized (lock) {
        if (!closed) {
          Failure previous = failures.get(key);
          int attempts = previous == null ? 1 : Math.min(8, previous.attempts + 1);
          long backoff = Math.min(MAX_RETRY_MS, RETRY_BASE_MS * (1L << (attempts - 1)));
          failures.put(
              key,
              new Failure(
                  attempts, SystemClock.elapsedRealtime() + Math.max(backoff, retryAfterMs)));
        }
      }
    } finally {
      if (connection != null) {
        synchronized (lock) {
          connections.remove(connection);
        }
        connection.disconnect();
      }
      synchronized (lock) {
        pending.remove(key);
      }
      changed();
    }
  }

  private Bitmap decodeFile(File file) {
    if (!file.isFile()) return null;
    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
    return bitmap != null && bitmap.getWidth() == 256 && bitmap.getHeight() == 256 ? bitmap : null;
  }

  private void put(String key, Bitmap bitmap) {
    synchronized (lock) {
      if (!closed) memory.put(key, bitmap);
    }
  }

  private void succeeded(String key) {
    synchronized (lock) {
      failures.remove(key);
    }
  }

  private boolean isClosed() {
    synchronized (lock) {
      return closed;
    }
  }

  private void changed() {
    synchronized (lock) {
      if (closed) return;
    }
    main.post(notifyChanged);
  }

  private static byte[] readTile(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
    byte[] buffer = new byte[8192];
    int size;
    while ((size = input.read(buffer)) != -1) {
      if (Thread.currentThread().isInterrupted()) throw new IOException("Tile request cancelled");
      if (output.size() + size > MAX_TILE_BYTES)
        throw new IOException("Tile response is too large");
      output.write(buffer, 0, size);
    }
    return output.toByteArray();
  }

  private static Properties readMetadata(File file) {
    Properties properties = new Properties();
    try (FileInputStream input = new AtomicFile(file).openRead()) {
      properties.load(input);
    } catch (IOException ignored) {
      // Missing or reclaimed metadata is a normal first-use condition.
    }
    return properties;
  }

  private static void saveMetadata(File file, Properties properties) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    properties.store(bytes, "OpenStreetMap tile cache validators");
    writeAtomic(file, bytes.toByteArray());
  }

  private static void writeAtomic(File file, byte[] bytes) throws IOException {
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream output = null;
    try {
      output = atomic.startWrite();
      output.write(bytes);
      atomic.finishWrite(output);
    } catch (IOException failure) {
      if (output != null) atomic.failWrite(output);
      throw failure;
    }
  }

  private static long retryDelay(HttpURLConnection connection) {
    String value = connection.getHeaderField("Retry-After");
    long seconds = number(value, -1);
    if (seconds >= 0) return Math.min(24 * 60 * 60_000L, seconds * 1000L);
    long date = connection.getHeaderFieldDate("Retry-After", 0);
    return Math.max(0, Math.min(24 * 60 * 60_000L, date - System.currentTimeMillis()));
  }

  private static long number(String value, long fallback) {
    try {
      return value == null ? fallback : Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }

  @Override
  public void close() {
    Set<HttpURLConnection> active;
    synchronized (lock) {
      if (closed) return;
      closed = true;
      active = new HashSet<>(connections);
      connections.clear();
      pending.clear();
      failures.clear();
      memory.evictAll();
    }
    main.removeCallbacks(notifyChanged);
    executor.shutdownNow();
    for (HttpURLConnection connection : active) connection.disconnect();
  }

  private static final class Failure {
    final int attempts;
    final long retryAt;

    Failure(int attempts, long retryAt) {
      this.attempts = attempts;
      this.retryAt = retryAt;
    }
  }
}
