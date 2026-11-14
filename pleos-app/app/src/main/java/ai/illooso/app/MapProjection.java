package ai.illooso.app;

/**
 * Web Mercator coordinates in a north-up, 256-pixel tile world.
 *
 * <p>Longitude is clamped to [-180, 180], not wrapped. Latitude is clamped to the Mercator limit.
 * Inverse coordinates are also clamped to the world bounds. The eastern and southern edges are the
 * inclusive pixel value {@code 256 * 2^zoom}; callers choosing tile indices must cap them at {@code
 * 2^zoom - 1}. Zoom levels 0 through 30 are supported.
 */
public final class MapProjection {
  public static final double MAX_LATITUDE = 85.05112878;

  private MapProjection() {}

  public static double worldX(double longitude, int zoom) {
    return (clampFinite(longitude, -180, 180) + 180) / 360 * worldSize(zoom);
  }

  public static double worldY(double latitude, int zoom) {
    double size = worldSize(zoom);
    double radians = Math.toRadians(clampFinite(latitude, -MAX_LATITUDE, MAX_LATITUDE));
    double mercator = Math.log(Math.tan(Math.PI / 4 + radians / 2));
    return clampFinite((1 - mercator / Math.PI) / 2 * size, 0, size);
  }

  public static double longitude(double worldX, int zoom) {
    double size = worldSize(zoom);
    return clampFinite(worldX, 0, size) / size * 360 - 180;
  }

  public static double latitude(double worldY, int zoom) {
    double size = worldSize(zoom);
    double mercator = Math.PI * (1 - 2 * clampFinite(worldY, 0, size) / size);
    return Math.toDegrees(Math.atan(Math.sinh(mercator)));
  }

  private static double worldSize(int zoom) {
    if (zoom < 0 || zoom > 30) throw new IllegalArgumentException("Zoom must be between 0 and 30");
    return Math.scalb(256.0, zoom);
  }

  private static double clampFinite(double value, double min, double max) {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("Coordinate must be finite");
    return Math.max(min, Math.min(max, value));
  }
}
