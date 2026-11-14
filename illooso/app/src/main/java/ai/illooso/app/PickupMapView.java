package ai.illooso.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real OSM raster geography with coordinate-aligned, separately labelled example assessments. */
public final class PickupMapView extends View {
  public interface OnPlaceSelected {
    void onSelect(String id);
  }

  private static final int BLUE = 0xff2563eb, GREEN = 0xff0d9c83, INK = 0xff172334;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final Path path = new Path();
  private final UiIconDrawable pickupIcon = new UiIconDrawable("car", GREEN);
  private final UiIconDrawable selectedPickupIcon = new UiIconDrawable("car", Color.WHITE);
  private final float density, textDensity;
  private final MapTileStore tiles;
  private final ScaleGestureDetector scaleDetector;
  private final List<Place> places = new ArrayList<>();
  private List<RoadOverlayData.Road> roads = Collections.emptyList();
  private String selectedId, recommendedId, pendingClickId, pressedId;
  private final Map<String, MapLabelLayout.Bounds> labelBounds = new HashMap<>();
  private String destinationName = PlaceRepository.DEFAULT_DESTINATION;
  private double destinationLat = PlaceRepository.DEST_LAT,
      destinationLon = PlaceRepository.DEST_LON;
  private double centerLat = destinationLat, centerLon = destinationLon, zoom = 16;
  private float contentTopInsetDp = 96, contentBottomInsetDp = 330;
  private boolean initialFitPending = true, initialFitScheduled;
  private float cameraOriginY;
  private boolean obscured, routeActive, roadColors = true, moved, missingTiles;
  private boolean geometryFailed;
  private float downX, downY, lastX, lastY;
  private int accessibilityFocusId = -1, hoveredVirtualId = -1;
  private OnPlaceSelected listener;
  private final AccessibilityNodeProvider nodes = new MapAccessibilityProvider();

  public PickupMapView(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    textDensity = getResources().getDisplayMetrics().scaledDensity;
    tiles = new MapTileStore(context, this::postInvalidate);
    try {
      roads = RoadOverlayData.load(context);
    } catch (Exception failed) {
      geometryFailed = true;
    }
    scaleDetector =
        new ScaleGestureDetector(
            context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScale(ScaleGestureDetector detector) {
                moved = true;
                zoom =
                    Math.max(
                        13, Math.min(19, zoom + Math.log(detector.getScaleFactor()) / Math.log(2)));
                invalidate();
                return true;
              }
            });
    setFocusable(true);
    setClickable(true);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setContentDescription(
        "평거동 실제 OpenStreetMap 지도. 도로 색상과 승하차 장소는 예시 평가입니다. 지도를 이동하거나 확대할 수 있습니다.");
  }

  public void close() {
    tiles.close();
  }

  public void setListener(OnPlaceSelected value) {
    listener = value;
  }

  public void setPlaces(List<Place> values) {
    places.clear();
    if (values != null) places.addAll(values);
    accessibilityFocusId = -1;
    labelBounds.clear();
    invalidate();
    sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
  }

  public void setSelected(String id) {
    selectedId = id;
    invalidate();
  }

  /** Recommendation comes from eligible candidates, never from an appended ongoing target. */
  public void setRecommendedPlace(String id) {
    recommendedId = id;
    invalidate();
  }

  public void setDestination(String name, double latitude, double longitude) {
    destinationName = name == null ? "목적지" : name;
    destinationLat = latitude;
    destinationLon = longitude;
    invalidate();
  }

  public void setRouteActive(boolean active) {
    routeActive = active;
    invalidate();
  }

  public void setMapObscured(boolean value) {
    obscured = value;
    setImportantForAccessibility(
        value || !isEnabled()
            ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            : IMPORTANT_FOR_ACCESSIBILITY_YES);
    invalidate();
  }

  public void zoomBy(int steps) {
    if (!isEnabled() || obscured) return;
    zoom = Math.max(13, Math.min(19, zoom + steps * .5));
    invalidate();
    sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
  }

  public void retryTiles() {
    tiles.retryFailed();
    invalidate();
  }

  public void setRoadColors(boolean value) {
    roadColors = value;
    invalidate();
  }

  public boolean hasRoadColors() {
    return roadColors;
  }

  public void recenter() {
    centerLat = destinationLat;
    centerLon = destinationLon;
    if (getWidth() == 0 || getHeight() == 0) {
      initialFitPending = true;
      return;
    }
    initialFitPending = false;
    cameraOriginY = (contentTop() + dp(86) + contentBottom()) / 2;
    double minX = MapProjection.worldX(destinationLon, 18), maxX = minX;
    double minY = MapProjection.worldY(destinationLat, 18), maxY = minY;
    for (Place place : places) {
      double x = MapProjection.worldX(place.longitude, 18),
          y = MapProjection.worldY(place.latitude, 18);
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minY = Math.min(minY, y);
      maxY = Math.max(maxY, y);
    }
    centerLon = MapProjection.longitude((minX + maxX) / 2, 18);
    centerLat = MapProjection.latitude((minY + maxY) / 2, 18);
    double sx = Math.max(dp(120), getWidth() - dp(180)) / (Math.max(200, maxX - minX) * dp(2));
    double sy =
        Math.max(dp(120), contentBottom() - contentTop() - dp(220))
            / (Math.max(200, maxY - minY) * dp(2));
    zoom = Math.max(13, Math.min(18, 18 + Math.log(Math.min(sx, sy)) / Math.log(2)));
    invalidate();
  }

  public void setContentInsets(float topDp, float bottomDp) {
    if (!Float.isFinite(topDp) || !Float.isFinite(bottomDp)) return;
    if (contentTopInsetDp == topDp && contentBottomInsetDp == bottomDp) return;
    contentTopInsetDp = Math.max(0, topDp);
    contentBottomInsetDp = Math.max(0, bottomDp);
    scheduleInitialFit();
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    scheduleInitialFit();
  }

  private void scheduleInitialFit() {
    if (!initialFitPending || initialFitScheduled) return;
    initialFitScheduled = true;
    post(
        () -> {
          initialFitScheduled = false;
          if (initialFitPending && getWidth() > 0 && getHeight() > 0) recenter();
        });
  }

  private float dp(float value) {
    return density * value;
  }

  private float contentTop() {
    return Math.min(dp(contentTopInsetDp), Math.max(0, getHeight() - dp(160)));
  }

  private float contentBottom() {
    return Math.min(
        getHeight(), Math.max(contentTop() + dp(160), getHeight() - dp(contentBottomInsetDp)));
  }

  private float mapCenterY() {
    // Overlay changes must not move the projected map, even when their height changes.
    return initialFitPending ? getHeight() / 2f : cameraOriginY;
  }

  private int tileZoom() {
    // Use the next detail level during fractional zoom instead of enlarging low-detail tiles.
    return (int) Math.ceil(zoom);
  }

  private double scale() {
    return dp(2) * Math.pow(2, zoom - tileZoom());
  }

  private float screenX(double lon) {
    return (float)
        (getWidth() / 2.0
            + (MapProjection.worldX(lon, tileZoom()) - MapProjection.worldX(centerLon, tileZoom()))
                * scale());
  }

  private float screenY(double lat) {
    return (float)
        (mapCenterY()
            + (MapProjection.worldY(lat, tileZoom()) - MapProjection.worldY(centerLat, tileZoom()))
                * scale());
  }

  private void fill(int color) {
    paint.reset();
    paint.setAntiAlias(true);
    paint.setColor(color);
    paint.setStyle(Paint.Style.FILL);
  }

  private void line(int color, float width) {
    fill(color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(width);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
  }

  private void text(
      Canvas canvas,
      String value,
      float x,
      float y,
      float size,
      int color,
      boolean bold,
      Paint.Align align) {
    fill(color);
    paint.setTextSize(size * textDensity);
    paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    paint.setTextAlign(align);
    canvas.drawText(value, x, y - (paint.ascent() + paint.descent()) / 2, paint);
  }

  private float textWidth(String value, float size, boolean bold) {
    fill(INK);
    paint.setTextSize(size * textDensity);
    paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    return paint.measureText(value);
  }

  private String fitText(String value, float maxWidth, float size, boolean bold) {
    if (textWidth(value, size, bold) <= maxWidth) return value;
    int end = value.length();
    while (end > 0 && textWidth(value.substring(0, end) + "…", size, bold) > maxWidth) end--;
    return value.substring(0, end) + "…";
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.drawColor(0xffedf2f6);
    // The expanded decision card covers the map; do not fetch hidden map tiles.
    if (obscured) return;
    canvas.save();
    canvas.clipRect(0, contentTop(), getWidth(), contentBottom());
    drawTiles(canvas);
    if (roadColors) drawRoads(canvas);
    layoutLabels();
    drawLabelLeaders(canvas);
    drawDestination(canvas);
    for (int i = 0; i < places.size(); i++)
      if (!places.get(i).id.equals(selectedId)) drawMarker(canvas, places.get(i), i);
    for (int i = 0; i < places.size(); i++)
      if (places.get(i).id.equals(selectedId)) drawMarker(canvas, places.get(i), i);
    drawLabels(canvas);
    canvas.restore();
    drawChrome(canvas);
  }

  private void drawTiles(Canvas canvas) {
    int z = tileZoom(), count = 1 << z;
    double scale = scale(),
        cx = MapProjection.worldX(centerLon, z),
        cy = MapProjection.worldY(centerLat, z);
    double left = cx - getWidth() / 2.0 / scale, top = cy + (contentTop() - mapCenterY()) / scale;
    double right = cx + getWidth() / 2.0 / scale,
        bottom = cy + (contentBottom() - mapCenterY()) / scale;
    int x0 = Math.max(0, (int) Math.floor(left / 256)),
        x1 = Math.min(count - 1, (int) Math.floor(right / 256));
    int y0 = Math.max(0, (int) Math.floor(top / 256)),
        y1 = Math.min(count - 1, (int) Math.floor(bottom / 256));
    missingTiles = false;
    for (int y = y0; y <= y1; y++)
      for (int x = x0; x <= x1; x++) {
        Bitmap tile = tiles.get(z, x, y);
        float px = (float) (getWidth() / 2.0 + (x * 256 - cx) * scale);
        float py = (float) (mapCenterY() + (y * 256 - cy) * scale);
        float size = (float) (256 * scale);
        rect.set(px, py, px + size + .5f, py + size + .5f);
        if (tile != null) {
          fill(Color.WHITE);
          paint.setFilterBitmap(true);
          canvas.drawBitmap(tile, null, rect, paint);
        } else {
          missingTiles = true;
          fill(0xffedf2f6);
          canvas.drawRect(rect, paint);
          line(0xffdde5eb, dp(1));
          canvas.drawRect(rect, paint);
        }
      }
  }

  private void roadPath(RoadOverlayData.Road road) {
    path.reset();
    for (int i = 0; i < road.points.length; i++) {
      float x = screenX(road.points[i][0]), y = screenY(road.points[i][1]);
      if (i == 0) path.moveTo(x, y);
      else path.lineTo(x, y);
    }
  }

  private void drawRoads(Canvas canvas) {
    // Draw unassessed roads first, then the example evaluations with a white casing.
    for (int pass = 0; pass < 2; pass++)
      for (RoadOverlayData.Road road : roads) {
        boolean unknown = road.assessment == RoadOverlayData.Assessment.UNKNOWN;
        if ((pass == 0) != unknown) continue;
        roadPath(road);
        if (unknown) {
          line(0x708d9baa, dp(3));
          paint.setPathEffect(new DashPathEffect(new float[] {dp(5), dp(6)}, 0));
          canvas.drawPath(path, paint);
          paint.setPathEffect(null);
        } else {
          boolean selected = road.placeId != null && road.placeId.equals(selectedId);
          line(0xeaffffff, dp(selected ? 14 : 11));
          canvas.drawPath(path, paint);
          line(road.assessment.color, dp(selected ? 9 : 6));
          canvas.drawPath(path, paint);
        }
      }
  }

  private boolean markerVisible(Place place) {
    float x = screenX(place.longitude), y = screenY(place.latitude);
    return !obscured
        && x >= dp(26)
        && x <= getWidth() - dp(26)
        && y >= contentTop() + dp(100)
        && y <= contentBottom() - dp(40);
  }

  private void drawMarker(Canvas canvas, Place place, int index) {
    if (!markerVisible(place)) return;
    float x = screenX(place.longitude), y = screenY(place.latitude);
    boolean active = place.id.equals(selectedId);
    float radius = dp(active ? 28 : 24);
    boolean recommended = place.id.equals(recommendedId);
    int accent = recommended || place.parking ? BLUE : GREEN;
    if (active || recommended) {
      fill(0x402563eb);
      canvas.drawCircle(x, y, radius + dp(7), paint);
    }
    fill(Color.WHITE);
    canvas.drawCircle(x, y, radius + dp(3), paint);
    fill(active || recommended ? accent : Color.WHITE);
    canvas.drawCircle(x, y, radius, paint);
    if (!active && !recommended) {
      line(accent, dp(2));
      canvas.drawCircle(x, y, radius - dp(2), paint);
    }
    if (recommended || place.parking) {
      text(
          canvas,
          recommended ? "★" : "P",
          x,
          y,
          24,
          active || recommended ? Color.WHITE : INK,
          true,
          Paint.Align.CENTER);
    } else {
      UiIconDrawable icon = active ? selectedPickupIcon : pickupIcon;
      float half = dp(14);
      icon.setBounds(
          Math.round(x - half), Math.round(y - half), Math.round(x + half), Math.round(y + half));
      icon.draw(canvas);
    }
    if (accessibilityFocusId == index + 1) {
      line(INK, dp(3));
      canvas.drawCircle(x, y, radius + dp(6), paint);
    }
  }

  private String shortPlaceName(Place place) {
    return place.name.replace(" 예시 승하차 지점", "").replace(" 예시 주차 공간", "");
  }

  private String placeDistance(Place place) {
    Destination target =
        new Destination("map", destinationName, "", destinationLat, destinationLon, "");
    int meters = DestinationRepository.distanceMeters(target, place);
    return meters == Integer.MAX_VALUE ? "거리 확인 필요" : "직선 " + meters + "m";
  }

  private void layoutLabels() {
    labelBounds.clear();
    if (obscured) return;
    float viewportTop = contentTop() + dp(100);
    float viewportBottom =
        contentBottom() - dp(missingTiles || geometryFailed || routeActive ? 44 : 8);
    if (getWidth() <= dp(40) || viewportBottom <= viewportTop) return;
    List<MapLabelLayout.Anchor> anchors = new ArrayList<>();
    List<MapLabelLayout.Bounds> obstacles = new ArrayList<>();
    float labelWidth = Math.min(dp(300), getWidth() - dp(40));
    for (Place place : places) {
      if (!markerVisible(place)) continue;
      float x = screenX(place.longitude), y = screenY(place.latitude);
      anchors.add(
          new MapLabelLayout.Anchor(
              place.id,
              x,
              y,
              labelWidth,
              dp(place.id.equals(recommendedId) ? 112 : 76),
              place.id.equals(recommendedId) || place.id.equals(selectedId)));
      obstacles.add(new MapLabelLayout.Bounds(x - dp(34), y - dp(34), x + dp(34), y + dp(34)));
    }
    float destX = screenX(destinationLon), destY = screenY(destinationLat);
    float destinationWidth =
        Math.min(getWidth() - dp(80), textWidth(destinationName, 18, true)) + dp(20);
    float destLabelX =
        Math.max(
            destinationWidth / 2 + dp(12),
            Math.min(getWidth() - destinationWidth / 2 - dp(12), destX));
    if (destinationVisible())
      obstacles.add(
          new MapLabelLayout.Bounds(
              destLabelX - destinationWidth / 2 - dp(4),
              destY - dp(66),
              destLabelX + destinationWidth / 2 + dp(4),
              destY + dp(24)));
    // The map zoom controls are real Android Views above this canvas.
    obstacles.add(
        new MapLabelLayout.Bounds(
            getWidth() - dp(92), contentTop() + dp(100), getWidth(), contentTop() + dp(368)));
    MapLabelLayout.Bounds viewport =
        new MapLabelLayout.Bounds(dp(20), viewportTop, getWidth() - dp(20), viewportBottom);
    for (MapLabelLayout.Label label : MapLabelLayout.layout(anchors, viewport, obstacles, dp(8)))
      labelBounds.put(label.id, label.bounds);
  }

  private void drawLabelLeaders(Canvas canvas) {
    for (Place place : places) {
      MapLabelLayout.Bounds bounds = labelBounds.get(place.id);
      if (bounds == null) continue;
      float x = screenX(place.longitude), y = screenY(place.latitude);
      float endX = Math.max(bounds.left, Math.min(bounds.right, x));
      float endY = Math.max(bounds.top, Math.min(bounds.bottom, y));
      line(0xfaffffff, dp(6));
      canvas.drawLine(x, y, endX, endY, paint);
      line(place.id.equals(recommendedId) ? BLUE : 0xff7d91a5, dp(2));
      canvas.drawLine(x, y, endX, endY, paint);
    }
  }

  private void drawLabels(Canvas canvas) {
    for (int i = 0; i < places.size(); i++) {
      Place place = places.get(i);
      MapLabelLayout.Bounds bounds = labelBounds.get(place.id);
      if (bounds == null) continue;
      boolean recommended = place.id.equals(recommendedId);
      boolean active = place.id.equals(selectedId);
      rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
      fill(recommended ? 0xfff4f8ff : Color.WHITE);
      paint.setShadowLayer(dp(5), 0, dp(2), 0x30405a75);
      canvas.drawRoundRect(rect, dp(18), dp(18), paint);
      paint.clearShadowLayer();
      line(recommended || active ? BLUE : 0xffd3deea, dp(recommended || active ? 2 : 1));
      canvas.drawRoundRect(rect, dp(18), dp(18), paint);
      float titleY = bounds.top + dp(recommended ? 57 : 26);
      if (recommended)
        text(
            canvas,
            "★ 가까운 추천",
            bounds.left + dp(16),
            bounds.top + dp(23),
            19,
            BLUE,
            true,
            Paint.Align.LEFT);
      text(
          canvas,
          fitText(shortPlaceName(place), bounds.width() - dp(32), 23, true),
          bounds.left + dp(16),
          titleY,
          23,
          INK,
          true,
          Paint.Align.LEFT);
      text(
          canvas,
          fitText(
              placeDistance(place) + (place.parking ? " · 주차 예시" : " · 승하차 예시"),
              bounds.width() - dp(32),
              20,
              false),
          bounds.left + dp(16),
          titleY + dp(30),
          20,
          0xff45566c,
          false,
          Paint.Align.LEFT);
      if (accessibilityFocusId == i + 1) {
        line(INK, dp(3));
        canvas.drawRoundRect(rect, dp(18), dp(18), paint);
      }
    }
  }

  private boolean destinationVisible() {
    float x = screenX(destinationLon), y = screenY(destinationLat);
    return x >= dp(20)
        && x <= getWidth() - dp(20)
        && y >= contentTop() + dp(116)
        && y <= contentBottom() - dp(36);
  }

  private void drawDestination(Canvas canvas) {
    if (!destinationVisible()) return;
    float x = screenX(destinationLon), y = screenY(destinationLat);
    fill(Color.WHITE);
    canvas.drawCircle(x, y, dp(20), paint);
    fill(BLUE);
    canvas.drawCircle(x, y, dp(16), paint);
    fill(Color.WHITE);
    canvas.drawCircle(x, y, dp(6), paint);
    String label = fitText(destinationName, getWidth() - dp(80), 18, true);
    float width = textWidth(label, 18, true) + dp(20);
    float labelX = Math.max(width / 2 + dp(12), Math.min(getWidth() - width / 2 - dp(12), x));
    rect.set(labelX - width / 2, y - dp(62), labelX + width / 2, y - dp(28));
    fill(0xfaffffff);
    canvas.drawRoundRect(rect, dp(10), dp(10), paint);
    text(canvas, label, labelX, y - dp(45), 18, INK, true, Paint.Align.CENTER);
  }

  private void drawChrome(Canvas canvas) {
    float top = contentTop() + dp(8), left = dp(20);
    float width = Math.min(getWidth() - dp(40), dp(610));
    rect.set(left, top, left + width, top + dp(80));
    fill(0xf7ffffff);
    canvas.drawRoundRect(rect, dp(16), dp(16), paint);
    text(
        canvas,
        roadColors ? "도로별 승하차 표시 · 예시" : "실제 도로 지도",
        left + dp(16),
        top + dp(22),
        20,
        INK,
        true,
        Paint.Align.LEFT);
    if (roadColors) {
      String[] labels = {"추천", "주의", "제외", "미평가"};
      int[] colors = {GREEN, 0xffd98a11, 0xffe45862, 0xff8d9baa};
      float gap = (width - dp(32)) / 4;
      for (int i = 0; i < 4; i++) {
        float x = left + dp(20) + i * gap;
        line(colors[i], dp(5));
        canvas.drawLine(x, top + dp(57), x + dp(16), top + dp(57), paint);
        text(canvas, labels[i], x + dp(24), top + dp(57), 18, INK, false, Paint.Align.LEFT);
      }
    } else
      text(
          canvas,
          "도로 색상 버튼으로 예시 평가를 켤 수 있어요",
          left + dp(16),
          top + dp(57),
          18,
          INK,
          false,
          Paint.Align.LEFT);
    if (missingTiles || geometryFailed || routeActive) {
      String status =
          geometryFailed
              ? "도로 표시를 불러오지 못했어요"
              : missingTiles
                  ? tiles.hasFailure() ? "지도 연결 확인 · 여기를 눌러 재시도" : "실제 지도를 불러오는 중…"
                  : "안내 미리보기 · 실제 경로는 순정 내비 확인";
      float w = Math.min(getWidth() - dp(40), textWidth(status, 18, false) + dp(24));
      rect.set(dp(20), contentBottom() - dp(38), dp(20) + w, contentBottom() - dp(4));
      fill(0xf7ffffff);
      canvas.drawRoundRect(rect, dp(10), dp(10), paint);
      text(
          canvas,
          fitText(status, w - dp(24), 18, false),
          dp(32),
          contentBottom() - dp(21),
          18,
          INK,
          false,
          Paint.Align.LEFT);
    }
  }

  private Place hit(float x, float y) {
    for (Place place : places) {
      MapLabelLayout.Bounds bounds = labelBounds.get(place.id);
      if (markerVisible(place) && bounds != null && bounds.contains(x, y)) return place;
    }
    Place best = null;
    float nearest = dp(35) * dp(35);
    for (Place place : places) {
      if (!markerVisible(place)) continue;
      float dx = screenX(place.longitude) - x, dy = screenY(place.latitude) - y;
      float d = dx * dx + dy * dy;
      if (d < nearest) {
        nearest = d;
        best = place;
      }
    }
    return best;
  }

  private void selectRoad(float x, float y) {
    if (!roadColors || y < contentTop() + dp(90) || y > contentBottom() - dp(40)) return;
    RoadOverlayData.Road best = null;
    double nearest = dp(16) * dp(16);
    for (RoadOverlayData.Road road : roads)
      for (int i = 1; i < road.points.length; i++) {
        double ax = screenX(road.points[i - 1][0]), ay = screenY(road.points[i - 1][1]);
        double bx = screenX(road.points[i][0]), by = screenY(road.points[i][1]);
        double dx = bx - ax,
            dy = by - ay,
            t =
                Math.max(
                    0,
                    Math.min(1, ((x - ax) * dx + (y - ay) * dy) / Math.max(1, dx * dx + dy * dy)));
        double d = Math.pow(x - ax - t * dx, 2) + Math.pow(y - ay - t * dy, 2);
        if (d < nearest) {
          nearest = d;
          best = road;
        }
      }
    if (best == null) return;
    String description = best.displayName() + " · " + best.assessment.label;
    Toast.makeText(getContext(), description, Toast.LENGTH_SHORT).show();
    if (best.placeId != null)
      for (Place place : places)
        if (place.id.equals(best.placeId)) {
          pendingClickId = place.id;
          performClick();
          pendingClickId = null;
          break;
        }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled() || obscured) return false;
    scaleDetector.onTouchEvent(event);
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = lastX = event.getX();
        downY = lastY = event.getY();
        moved = false;
        Place place = hit(downX, downY);
        pressedId = place == null ? null : place.id;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      case MotionEvent.ACTION_POINTER_DOWN:
        moved = true;
        pressedId = null;
        return true;
      case MotionEvent.ACTION_POINTER_UP:
        // Continue panning with the remaining finger, not the departing pointer's old position.
        int remaining = event.getActionIndex() == 0 ? 1 : 0;
        lastX = downX = event.getX(remaining);
        lastY = downY = event.getY(remaining);
        moved = true;
        pressedId = null;
        return true;
      case MotionEvent.ACTION_MOVE:
        float x = event.getX(), y = event.getY();
        if (Math.hypot(x - downX, y - downY) > dp(8)) moved = true;
        if (moved && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
          int z = tileZoom();
          centerLon =
              MapProjection.longitude(
                  MapProjection.worldX(centerLon, z) - (x - lastX) / scale(), z);
          centerLat =
              MapProjection.latitude(MapProjection.worldY(centerLat, z) - (y - lastY) / scale(), z);
          centerLon = Math.max(destinationLon - .02, Math.min(destinationLon + .02, centerLon));
          centerLat = Math.max(destinationLat - .015, Math.min(destinationLat + .015, centerLat));
          invalidate();
        }
        lastX = x;
        lastY = y;
        return true;
      case MotionEvent.ACTION_UP:
        if (!moved) {
          Place tapped = hit(event.getX(), event.getY());
          if (tapped != null && tapped.id.equals(pressedId)) {
            pendingClickId = tapped.id;
            performClick();
            pendingClickId = null;
          } else if (missingTiles && event.getY() >= contentBottom() - dp(42)) retryTiles();
          else selectRoad(event.getX(), event.getY());
        }
        pressedId = null;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        return true;
      case MotionEvent.ACTION_CANCEL:
        pressedId = null;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        return true;
      default:
        return true;
    }
  }

  @Override
  public boolean performClick() {
    if (!isEnabled()) return false;
    super.performClick();
    String id = pendingClickId != null ? pendingClickId : selectedId;
    if (id == null && !places.isEmpty()) id = places.get(0).id;
    if (id != null) {
      selectedId = id;
      invalidate();
      if (listener != null) listener.onSelect(id);
    }
    return true;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (!isEnabled()) return super.onKeyDown(keyCode, event);
    if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        && !places.isEmpty()) {
      int index = -1;
      for (int i = 0; i < places.size(); i++) if (places.get(i).id.equals(selectedId)) index = i;
      int direction = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
      index =
          index < 0
              ? (direction > 0 ? 0 : places.size() - 1)
              : (index + direction + places.size()) % places.size();
      pendingClickId = places.get(index).id;
      performClick();
      pendingClickId = null;
      announceForAccessibility(places.get(index).name);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public AccessibilityNodeProvider getAccessibilityNodeProvider() {
    return nodes;
  }

  @Override
  public boolean dispatchHoverEvent(MotionEvent event) {
    AccessibilityManager manager =
        (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled()) {
      int next = -1;
      if (event.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT) {
        Place place = hit(event.getX(), event.getY());
        if (place != null) next = places.indexOf(place) + 1;
      }
      if (next != hoveredVirtualId) {
        if (hoveredVirtualId != -1)
          virtualEvent(hoveredVirtualId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        hoveredVirtualId = next;
        if (next != -1) virtualEvent(next, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
      }
      if (next != -1 || event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) return true;
    }
    return super.dispatchHoverEvent(event);
  }

  private void virtualEvent(int id, int type) {
    if (getParent() == null) return;
    AccessibilityEvent event = AccessibilityEvent.obtain(type);
    event.setPackageName(getContext().getPackageName());
    event.setClassName("android.widget.Button");
    event.setSource(this, id);
    if (id > 0 && id <= places.size()) event.setContentDescription(places.get(id - 1).name);
    getParent().requestSendAccessibilityEvent(this, event);
  }

  private final class MapAccessibilityProvider extends AccessibilityNodeProvider {
    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
      if (id == View.NO_ID) {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(PickupMapView.this);
        onInitializeAccessibilityNodeInfo(info);
        for (int i = 0; i < places.size(); i++)
          if (markerVisible(places.get(i))) info.addChild(PickupMapView.this, i + 1);
        return info;
      }
      if (id < 1 || id > places.size()) return null;
      Place place = places.get(id - 1);
      AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
      info.setSource(PickupMapView.this, id);
      info.setParent(PickupMapView.this);
      info.setPackageName(getContext().getPackageName());
      info.setClassName("android.widget.Button");
      info.setContentDescription(
          place.name
              + ", 목적지까지 "
              + placeDistance(place)
              + (place.id.equals(recommendedId) ? ", 현재 조건에서 목적지에 가장 가까운 추천" : "")
              + (place.parking ? ", 주차장" : ", 승하차 지점")
              + (place.id.equals(selectedId) ? ", 선택됨" : "")
              + ", 데모 지점");
      info.setEnabled(isEnabled());
      info.setFocusable(true);
      info.setClickable(isEnabled());
      info.setSelected(place.id.equals(selectedId));
      float x = screenX(place.longitude), y = screenY(place.latitude), radius = dp(30);
      Rect bounds =
          new Rect((int) (x - radius), (int) (y - radius), (int) (x + radius), (int) (y + radius));
      MapLabelLayout.Bounds label = labelBounds.get(place.id);
      if (label != null)
        bounds.union(
            new Rect((int) label.left, (int) label.top, (int) label.right, (int) label.bottom));
      info.setBoundsInParent(bounds);
      int[] location = new int[2];
      getLocationOnScreen(location);
      bounds.offset(location[0], location[1]);
      info.setBoundsInScreen(bounds);
      info.setVisibleToUser(isShown() && markerVisible(place));
      if (isEnabled()) info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
      info.setAccessibilityFocused(accessibilityFocusId == id);
      info.addAction(
          accessibilityFocusId == id
              ? AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
              : AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
      return info;
    }

    @Override
    public boolean performAction(int id, int action, Bundle arguments) {
      if (id == View.NO_ID) return PickupMapView.this.performAccessibilityAction(action, arguments);
      if (id < 1 || id > places.size() || !isEnabled() || !markerVisible(places.get(id - 1)))
        return false;
      if (action == AccessibilityNodeInfo.ACTION_CLICK) {
        pendingClickId = places.get(id - 1).id;
        performClick();
        pendingClickId = null;
        virtualEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
      }
      if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
        if (accessibilityFocusId == id) return false;
        if (accessibilityFocusId != -1)
          virtualEvent(
              accessibilityFocusId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        accessibilityFocusId = id;
        invalidate();
        virtualEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        return true;
      }
      if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
          && accessibilityFocusId == id) {
        accessibilityFocusId = -1;
        invalidate();
        virtualEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        return true;
      }
      return false;
    }
  }
}
