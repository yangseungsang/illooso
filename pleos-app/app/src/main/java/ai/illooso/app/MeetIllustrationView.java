package ai.illooso.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Decorative miniature streetscape; intentionally contains no map data or interactive controls. */
public final class MeetIllustrationView extends View {
  private static final float SCENE_WIDTH = 1000f, SCENE_HEIGHT = 340f;
  private static final int BLUE = Color.rgb(52, 130, 246);
  private static final int MINT = Color.rgb(34, 195, 171);
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final RectF bounds = new RectF();

  public MeetIllustrationView(Context context) {
    super(context);
    setFocusable(false);
    setClickable(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setContentDescription("목적지 가까운 승하차 지점에서 만나는 거리 일러스트");
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int desiredWidth = Math.round(760f * getResources().getDisplayMetrics().density);
    int width = resolveSize(desiredWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
    int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
    int desiredHeight = Math.round(contentWidth * SCENE_HEIGHT / SCENE_WIDTH);
    setMeasuredDimension(
        width,
        resolveSize(desiredHeight + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float width = getWidth() - getPaddingLeft() - getPaddingRight();
    float height = getHeight() - getPaddingTop() - getPaddingBottom();
    if (width <= 0 || height <= 0) return;
    float scale = Math.min(width / SCENE_WIDTH, height / SCENE_HEIGHT);
    int saved = canvas.save();
    canvas.translate(
        getPaddingLeft() + (width - SCENE_WIDTH * scale) / 2f,
        getPaddingTop() + (height - SCENE_HEIGHT * scale) / 2f);
    canvas.scale(scale, scale);

    // Broad, soft planes keep this recognizably illustrative rather than a navigation map.
    oval(canvas, 83, 71, 938, 320, 0xFFF0F4F9);
    ground(canvas, 0xFFF7F9FC, -238, -146, 242, -146, 242, 153, -238, 153);
    ground(canvas, 0xFFE2EAF3, -250, -35, 255, -35, 255, 35, -250, 35);
    ground(canvas, 0xFFE2EAF3, -35, -181, 35, -181, 35, 199, -35, 199);
    ground(canvas, 0xFFD3DFEC, -250, -27, 255, -27, 255, 27, -250, 27);
    ground(canvas, 0xFFD3DFEC, -27, -181, 27, -181, 27, 199, -27, 199);
    roadMarkings(canvas);
    crosswalk(canvas, false, 83);
    crosswalk(canvas, true, -122);

    // Rear gardens, a small apartment block, and a civic building with a glazed entrance.
    garden(canvas, -212, -127, 147, 82);
    tree(canvas, -214, -69, 20);
    tree(canvas, -196, -43, 17);
    building(canvas, -185, -112, 117, 62, 44, false);
    tree(canvas, -87, -137, 17);
    tree(canvas, -60, -119, 20);

    garden(canvas, 57, -151, 166, 108);
    building(canvas, 76, -139, 130, 77, 62, true);
    tree(canvas, 67, -49, 20);
    tree(canvas, 93, -45, 18);
    tree(canvas, 211, -46, 22);
    tree(canvas, 228, -72, 18);

    // Lower buildings frame a clear central street rather than competing with the pins.
    building(canvas, -209, 62, 108, 80, 57, false);
    tree(canvas, -226, 105, 21);
    tree(canvas, -92, 143, 19);
    garden(canvas, 80, 84, 107, 64);
    building(canvas, 91, 91, 78, 47, 28, false);
    tree(canvas, 186, 105, 20);
    tree(canvas, 183, 144, 18);

    route(canvas);
    car(canvas, -8, 155);
    person(canvas, 99, 43);
    pin(canvas, x(80, 13), y(80, 13, 0), MINT, 0.91f);
    pin(canvas, x(141, -101), y(141, -101, 64), BLUE, 1f);
    canvas.restoreToCount(saved);
  }

  private static float x(float u, float v) {
    return 500f + (u - v) * 1.15f;
  }

  private static float y(float u, float v, float elevation) {
    return 165f + (u + v) * 0.38f - elevation;
  }

  private void fill(int color) {
    paint.setShader(null);
    paint.setPathEffect(null);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(color);
  }

  private void stroke(int color, float width) {
    fill(color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(width);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
  }

  private void oval(Canvas canvas, float left, float top, float right, float bottom, int color) {
    fill(color);
    bounds.set(left, top, right, bottom);
    canvas.drawOval(bounds, paint);
  }

  private void ground(Canvas canvas, int color, float... points) {
    path.reset();
    for (int i = 0; i < points.length; i += 2) {
      float px = x(points[i], points[i + 1]);
      float py = y(points[i], points[i + 1], 0);
      if (i == 0) path.moveTo(px, py);
      else path.lineTo(px, py);
    }
    path.close();
    fill(color);
    canvas.drawPath(path, paint);
  }

  private void face(Canvas canvas, int color, float... points) {
    path.reset();
    for (int i = 0; i < points.length; i += 3) {
      float px = x(points[i], points[i + 1]);
      float py = y(points[i], points[i + 1], points[i + 2]);
      if (i == 0) path.moveTo(px, py);
      else path.lineTo(px, py);
    }
    path.close();
    fill(color);
    canvas.drawPath(path, paint);
  }

  private void garden(Canvas canvas, float u, float v, float width, float depth) {
    ground(canvas, 0xFFE6EEE9, u, v, u + width, v, u + width, v + depth, u, v + depth);
    ground(canvas, 0xFFF0F5F1, u + 3, v + 3, u + width - 3, v + 3,
        u + width - 3, v + depth - 3, u + 3, v + depth - 3);
  }

  private void building(Canvas canvas, float u, float v, float w, float d, float h, boolean glass) {
    ground(canvas, 0xFFDDE6F0, u + 9, v + 8, u + w + 9, v + 8,
        u + w + 9, v + d + 8, u + 9, v + d + 8);
    face(canvas, 0xFFE6EDF5, u + w, v, 0, u + w, v + d, 0,
        u + w, v + d, h, u + w, v, h);
    face(canvas, 0xFFDCE6F0, u, v + d, 0, u + w, v + d, 0,
        u + w, v + d, h, u, v + d, h);
    face(canvas, 0xFFFFFFFF, u, v, h, u + w, v, h,
        u + w, v + d, h, u, v + d, h);
    face(canvas, 0xFFF0F4F9, u + 7, v + 7, h + 0.3f, u + w - 7, v + 7, h + 0.3f,
        u + w - 7, v + d - 7, h + 0.3f, u + 7, v + d - 7, h + 0.3f);

    float bottom = glass ? 5 : h * 0.38f;
    float top = glass ? h - 9 : h * 0.76f;
    int panes = glass ? 6 : 5;
    for (int i = 0; i < panes; i++) {
      float left = u + 8 + (w - 16) * i / panes;
      float right = u + 8 + (w - 16) * (i + 1) / panes - 3;
      face(canvas, glass && i == 3 ? 0xFFE3E9E8 : 0xFFBFD2E3,
          left, v + d + 0.1f, bottom, right, v + d + 0.1f, bottom,
          right, v + d + 0.1f, top, left, v + d + 0.1f, top);
    }
    for (int i = 0; i < 3; i++) {
      float near = v + 9 + (d - 18) * i / 3;
      float far = v + 9 + (d - 18) * (i + 1) / 3 - 3;
      face(canvas, 0xFFD0DDEB, u + w + 0.1f, near, bottom,
          u + w + 0.1f, far, bottom, u + w + 0.1f, far, top,
          u + w + 0.1f, near, top);
    }
    if (glass) {
      face(canvas, 0xFFF9FBFE, u + 39, v + d - 1, 28, u + 91, v + d - 1, 28,
          u + 91, v + d + 15, 28, u + 39, v + d + 15, 28);
      ground(canvas, 0xFFE6EDF5, u + 39, v + d, u + 91, v + d,
          u + 91, v + d + 18, u + 39, v + d + 18);
    }
  }

  private void roadMarkings(Canvas canvas) {
    stroke(0xFFF8FBFF, 2f);
    for (int u = -230; u < 235; u += 48) {
      if (Math.abs(u) < 58) continue;
      canvas.drawLine(x(u, 0), y(u, 0, 0), x(u + 21, 0), y(u + 21, 0, 0), paint);
    }
    for (int v = -163; v < 190; v += 46) {
      if (Math.abs(v) < 60) continue;
      canvas.drawLine(x(0, v), y(0, v, 0), x(0, v + 18), y(0, v + 18, 0), paint);
    }
  }

  private void crosswalk(Canvas canvas, boolean alongU, float start) {
    for (int i = 0; i < 5; i++) {
      float a = start + i * 8;
      if (alongU) ground(canvas, 0xFFF8FBFF, a, -23, a + 4, -23, a + 4, 23, a, 23);
      else ground(canvas, 0xFFF8FBFF, -23, a, 23, a, 23, a + 4, -23, a + 4);
    }
  }

  private void tree(Canvas canvas, float u, float v, float size) {
    float px = x(u, v), py = y(u, v, 0);
    oval(canvas, px - size * 0.8f, py - 3, px + size, py + 8, 0x1E526C65);
    stroke(0xFFA6B4AB, 2.5f);
    canvas.drawLine(px, py, px, py - size * 0.7f, paint);
    fill(0xFFB0C8B5);
    canvas.drawCircle(px, py - size, size * 0.67f, paint);
    fill(0xFFBDD4BE);
    canvas.drawCircle(px - size * 0.28f, py - size * 1.26f, size * 0.58f, paint);
    fill(0xFFCEE0CA);
    canvas.drawCircle(px + size * 0.22f, py - size * 1.36f, size * 0.47f, paint);
  }

  private void route(Canvas canvas) {
    path.reset();
    path.moveTo(x(-8, 176), y(-8, 176, 0));
    path.cubicTo(x(-8, 132), y(-8, 132, 0), x(-8, 84), y(-8, 84, 0),
        x(-8, 55), y(-8, 55, 0));
    path.cubicTo(x(-8, 25), y(-8, 25, 0), x(5, 13), y(5, 13, 0),
        x(34, 13), y(34, 13, 0));
    path.lineTo(x(80, 13), y(80, 13, 0));
    stroke(0x1F3482F6, 21);
    canvas.drawPath(path, paint);
    stroke(0xFFF9FCFF, 12);
    canvas.drawPath(path, paint);
    stroke(BLUE, 7);
    canvas.drawPath(path, paint);
  }

  private void car(Canvas canvas, float u, float v) {
    int saved = canvas.save();
    canvas.translate(x(u, v), y(u, v, 0) - 4);
    canvas.rotate(-18.3f);
    oval(canvas, -28, -10, 27, 18, 0x26425A75);
    fill(0xFF344B65);
    canvas.drawRoundRect(-18, -12, -8, 12, 3, 3, paint);
    canvas.drawRoundRect(10, -12, 20, 12, 3, 3, paint);
    fill(0xFFFFFFFF);
    canvas.drawRoundRect(-25, -11, 25, 10, 8, 8, paint);
    fill(0xFFDDE8F3);
    canvas.drawRoundRect(-18, -9, 15, 7, 6, 6, paint);
    fill(0xFF5B7794);
    canvas.drawRoundRect(-12, -7, 10, 5, 4, 4, paint);
    fill(0xFFEAF1F8);
    canvas.drawRoundRect(-8, -6, 3, 4, 2, 2, paint);
    stroke(0xFFADC9E5, 2);
    canvas.drawLine(21, -5, 21, 3, paint);
    canvas.restoreToCount(saved);
  }

  private void person(Canvas canvas, float u, float v) {
    float px = x(u, v), py = y(u, v, 0);
    oval(canvas, px - 6, py - 2, px + 6, py + 3, 0x1F526C65);
    stroke(0xFF526B73, 2.1f);
    canvas.drawLine(px - 2, py, px - 1, py - 9, paint);
    canvas.drawLine(px + 3, py, px + 1, py - 9, paint);
    stroke(0xFF6C9C98, 5);
    canvas.drawLine(px, py - 8, px, py - 16, paint);
    fill(0xFF78908D);
    canvas.drawCircle(px, py - 21, 3.1f, paint);
  }

  private void pin(Canvas canvas, float px, float py, int color, float size) {
    int saved = canvas.save();
    canvas.translate(px, py);
    canvas.scale(size, size);
    oval(canvas, -30, -9, 30, 12, (color & 0x00FFFFFF) | 0x14000000);
    oval(canvas, -20, -6, 20, 8, (color & 0x00FFFFFF) | 0x27000000);
    stroke(0xFFFFFFFF, 1.6f);
    bounds.set(-20, -6, 20, 8);
    canvas.drawOval(bounds, paint);
    path.reset();
    path.moveTo(0, 1);
    path.cubicTo(-8, -13, -23, -27, -23, -38);
    path.cubicTo(-23, -66, 23, -66, 23, -38);
    path.cubicTo(23, -27, 8, -13, 0, 1);
    path.close();
    stroke(0xFFFFFFFF, 4);
    canvas.drawPath(path, paint);
    fill(color);
    canvas.drawPath(path, paint);
    fill(0xFFFFFFFF);
    canvas.drawCircle(0, -39, 8, paint);
    canvas.restoreToCount(saved);
  }
}
