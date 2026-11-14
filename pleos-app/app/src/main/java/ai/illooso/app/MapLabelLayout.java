package ai.illooso.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic screen-space label placement; the map camera is never involved. */
public final class MapLabelLayout {
  private MapLabelLayout() {}

  public static final class Bounds {
    public final float left, top, right, bottom;

    public Bounds(float left, float top, float right, float bottom) {
      if (!Float.isFinite(left)
          || !Float.isFinite(top)
          || !Float.isFinite(right)
          || !Float.isFinite(bottom)
          || right < left
          || bottom < top) throw new IllegalArgumentException("Invalid bounds");
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
    }

    public float width() {
      return right - left;
    }

    public float height() {
      return bottom - top;
    }

    public boolean contains(float x, float y) {
      return x >= left && x <= right && y >= top && y <= bottom;
    }

    public boolean contains(Bounds other) {
      return other.left >= left
          && other.top >= top
          && other.right <= right
          && other.bottom <= bottom;
    }

    public boolean intersects(Bounds other) {
      return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
    }
  }

  public static final class Anchor {
    public final String id;
    public final float x, y, width, height;
    public final boolean priority;

    public Anchor(String id, float x, float y, float width, float height, boolean priority) {
      this.id = Objects.requireNonNull(id, "id");
      if (!Float.isFinite(x)
          || !Float.isFinite(y)
          || !Float.isFinite(width)
          || !Float.isFinite(height)
          || width <= 0
          || height <= 0) throw new IllegalArgumentException("Invalid label anchor");
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.priority = priority;
    }
  }

  public static final class Label {
    public final String id;
    public final Bounds bounds;

    private Label(String id, Bounds bounds) {
      this.id = id;
      this.bounds = bounds;
    }
  }

  /**
   * Places priority labels first, retaining input order within each group. All dimensions,
   * including gap, use the caller's screen-coordinate units. Obstacles can include controls,
   * destination text, and marker bounds. A label that cannot fit without overlap is omitted; its
   * map marker can remain.
   */
  public static List<Label> layout(
      List<Anchor> anchors, Bounds viewport, List<Bounds> obstacles, float gap) {
    Objects.requireNonNull(anchors, "anchors");
    Objects.requireNonNull(viewport, "viewport");
    Objects.requireNonNull(obstacles, "obstacles");
    if (!Float.isFinite(gap) || gap < 0) throw new IllegalArgumentException("Invalid gap");
    List<Anchor> ordered = new ArrayList<>(anchors);
    ordered.sort(Comparator.comparing(anchor -> !anchor.priority));
    List<Bounds> occupied = new ArrayList<>(obstacles);
    // Even when callers omit marker bounds, no label may cover a candidate's exact location.
    for (Anchor anchor : anchors) occupied.add(new Bounds(anchor.x, anchor.y, anchor.x, anchor.y));
    List<Label> result = new ArrayList<>();
    for (Anchor anchor : ordered) {
      if (anchor.width > viewport.width() || anchor.height > viewport.height()) continue;
      Bounds bounds = place(anchor, viewport, occupied, gap);
      if (bounds == null) continue;
      result.add(new Label(anchor.id, bounds));
      occupied.add(bounds);
    }
    return List.copyOf(result);
  }

  private static Bounds place(Anchor anchor, Bounds viewport, List<Bounds> occupied, float gap) {
    float w = anchor.width, h = anchor.height, x = anchor.x, y = anchor.y;
    Bounds[] nearby = {
      box(x - w / 2, y - h - gap, w, h),
      box(x - w / 2, y + gap, w, h),
      box(x - w - gap, y - h / 2, w, h),
      box(x + gap, y - h / 2, w, h)
    };
    for (Bounds bounds : nearby) if (available(bounds, viewport, occupied, gap)) return bounds;

    // Obstacle edges contain the nearest useful packing positions. Their cross-product also finds
    // clear rows above/below a dense cluster, without a screen-size-dependent pixel scan.
    List<Float> xs = new ArrayList<>(), ys = new ArrayList<>();
    addPosition(xs, viewport.left, viewport.left, viewport.right - w);
    addPosition(xs, viewport.right - w, viewport.left, viewport.right - w);
    addPosition(xs, x - w / 2, viewport.left, viewport.right - w);
    addPosition(ys, viewport.top, viewport.top, viewport.bottom - h);
    addPosition(ys, viewport.bottom - h, viewport.top, viewport.bottom - h);
    addPosition(ys, y - h / 2, viewport.top, viewport.bottom - h);
    for (Bounds obstacle : occupied) {
      addPosition(xs, obstacle.left - w - gap, viewport.left, viewport.right - w);
      addPosition(xs, obstacle.right + gap, viewport.left, viewport.right - w);
      addPosition(ys, obstacle.top - h - gap, viewport.top, viewport.bottom - h);
      addPosition(ys, obstacle.bottom + gap, viewport.top, viewport.bottom - h);
    }
    Bounds best = null;
    double bestDistance = Double.POSITIVE_INFINITY;
    for (float left : xs)
      for (float top : ys) {
        Bounds candidate = box(left, top, w, h);
        if (!available(candidate, viewport, occupied, gap)) continue;
        double dx = (candidate.left + candidate.right) / 2 - x;
        double dy = (candidate.top + candidate.bottom) / 2 - y;
        double distance = dx * dx + dy * dy;
        if (distance < bestDistance) {
          best = candidate;
          bestDistance = distance;
        }
      }
    return best;
  }

  private static void addPosition(List<Float> values, float value, float min, float max) {
    float clamped = Math.max(min, Math.min(max, value));
    if (!values.contains(clamped)) values.add(clamped);
  }

  private static Bounds box(float left, float top, float width, float height) {
    return new Bounds(left, top, left + width, top + height);
  }

  private static boolean available(
      Bounds candidate, Bounds viewport, List<Bounds> occupied, float gap) {
    if (!viewport.contains(candidate)) return false;
    for (Bounds obstacle : occupied)
      if (candidate.left < obstacle.right + gap
          && candidate.right > obstacle.left - gap
          && candidate.top < obstacle.bottom + gap
          && candidate.bottom > obstacle.top - gap) return false;
    return true;
  }
}
