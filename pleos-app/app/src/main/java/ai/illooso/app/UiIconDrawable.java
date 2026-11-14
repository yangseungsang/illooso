package ai.illooso.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Small, consistent stroke icons. Geometry uses a 24-unit grid, independent of font glyphs. */
public final class UiIconDrawable extends Drawable {
  private final String name;
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();

  public UiIconDrawable(String name, int color) {
    this.name = name;
    p.setColor(color);
    p.setStrokeWidth(1.8f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
  }

  @Override
  public void draw(Canvas c) {
    c.save();
    c.translate(getBounds().left, getBounds().top);
    c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
    path.reset();
    switch (name) {
      case "close":
        c.drawLine(6, 6, 18, 18, p);
        c.drawLine(18, 6, 6, 18, p);
        break;
      case "menu":
        c.drawLine(4, 6, 20, 6, p);
        c.drawLine(4, 12, 20, 12, p);
        c.drawLine(4, 18, 20, 18, p);
        break;
      case "more":
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(5, 12, 1.6f, p);
        c.drawCircle(12, 12, 1.6f, p);
        c.drawCircle(19, 12, 1.6f, p);
        p.setStyle(Paint.Style.STROKE);
        break;
      case "down":
        path.moveTo(5, 9);
        path.lineTo(12, 16);
        path.lineTo(19, 9);
        c.drawPath(path, p);
        break;
      case "pin":
        path.moveTo(12, 22);
        path.cubicTo(9, 18, 4, 13, 4, 9);
        path.cubicTo(4, -1, 20, -1, 20, 9);
        path.cubicTo(20, 13, 15, 18, 12, 22);
        c.drawPath(path, p);
        c.drawCircle(12, 9, 2.6f, p);
        break;
      case "map":
        path.moveTo(2.5f, 5);
        path.lineTo(8.5f, 2.5f);
        path.lineTo(15.5f, 5);
        path.lineTo(21.5f, 2.5f);
        path.lineTo(21.5f, 19);
        path.lineTo(15.5f, 21.5f);
        path.lineTo(8.5f, 19);
        path.lineTo(2.5f, 21.5f);
        path.close();
        c.drawPath(path, p);
        c.drawLine(8.5f, 2.5f, 8.5f, 19, p);
        c.drawLine(15.5f, 5, 15.5f, 21.5f, p);
        break;
      case "car":
        path.moveTo(4, 10);
        path.lineTo(6, 4.8f);
        path.quadTo(6.4f, 3.5f, 7.8f, 3.5f);
        path.lineTo(16.2f, 3.5f);
        path.quadTo(17.6f, 3.5f, 18, 4.8f);
        path.lineTo(20, 10);
        c.drawPath(path, p);
        c.drawRoundRect(3, 10, 21, 18.5f, 2.5f, 2.5f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(7, 14, 1.3f, p);
        c.drawCircle(17, 14, 1.3f, p);
        c.drawRoundRect(4, 17.5f, 7, 21, 1, 1, p);
        c.drawRoundRect(17, 17.5f, 20, 21, 1, 1, p);
        p.setStyle(Paint.Style.STROKE);
        break;
      case "walk":
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(14, 3.5f, 2, p);
        p.setStyle(Paint.Style.STROKE);
        path.moveTo(5, 11);
        path.lineTo(8, 8);
        path.lineTo(12.5f, 7);
        path.lineTo(15.5f, 11);
        path.lineTo(20, 12.5f);
        path.moveTo(12.5f, 7);
        path.lineTo(10.5f, 14);
        path.lineTo(7.5f, 21);
        path.moveTo(10.5f, 14);
        path.lineTo(15, 16);
        path.lineTo(17, 21);
        c.drawPath(path, p);
        break;
      case "person":
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(12, 7, 4, p);
        path.moveTo(3.5f, 21);
        path.cubicTo(3.5f, 10.5f, 20.5f, 10.5f, 20.5f, 21);
        path.close();
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        break;
      case "trees":
        path.moveTo(7.5f, 2.5f);
        path.lineTo(2.5f, 12.5f);
        path.lineTo(5, 12.5f);
        path.lineTo(2, 18);
        path.lineTo(13, 18);
        path.lineTo(10, 12.5f);
        path.lineTo(12.5f, 12.5f);
        path.close();
        path.moveTo(17.5f, 6);
        path.lineTo(14, 13);
        path.lineTo(15.5f, 13);
        path.lineTo(12.5f, 19);
        path.lineTo(22.5f, 19);
        path.lineTo(19.5f, 13);
        path.lineTo(21, 13);
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(7.5f, 18, 7.5f, 22, p);
        c.drawLine(17.5f, 19, 17.5f, 22, p);
        break;
      case "thumbs-up":
        c.drawRoundRect(2.5f, 10.5f, 6.5f, 21, 1, 1, p);
        path.moveTo(6.5f, 11.5f);
        path.lineTo(11, 6.5f);
        path.lineTo(11.5f, 2.7f);
        path.cubicTo(13.2f, 1.4f, 15.5f, 3.3f, 15, 5.5f);
        path.lineTo(14, 9);
        path.lineTo(19, 9);
        path.quadTo(22, 9, 21.4f, 12);
        path.lineTo(19.8f, 19);
        path.quadTo(19.4f, 21, 17.2f, 21);
        path.lineTo(10, 21);
        path.lineTo(6.5f, 19.5f);
        c.drawPath(path, p);
        break;
      case "alert":
        c.drawCircle(12, 12, 9, p);
        c.drawLine(12, 6.8f, 12, 12.5f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(12, 16.5f, 1.1f, p);
        p.setStyle(Paint.Style.STROKE);
        break;
      case "parking":
        c.drawCircle(12, 12, 9.5f, p);
        path.moveTo(9, 18);
        path.lineTo(9, 6);
        path.lineTo(13, 6);
        path.cubicTo(18.5f, 6, 18.5f, 12.5f, 13, 12.5f);
        path.lineTo(9, 12.5f);
        c.drawPath(path, p);
        break;
      case "distance":
        c.drawLine(3, 12, 21, 12, p);
        path.moveTo(6.5f, 8.5f);
        path.lineTo(3, 12);
        path.lineTo(6.5f, 15.5f);
        path.moveTo(17.5f, 8.5f);
        path.lineTo(21, 12);
        path.lineTo(17.5f, 15.5f);
        c.drawPath(path, p);
        c.drawLine(3, 5, 3, 7, p);
        c.drawLine(12, 5, 12, 7, p);
        c.drawLine(21, 5, 21, 7, p);
        break;
      case "bookmark":
      case "saved":
        path.moveTo(5, 21);
        path.lineTo(5, 4);
        path.quadTo(5, 2, 7, 2);
        path.lineTo(17, 2);
        path.quadTo(19, 2, 19, 4);
        path.lineTo(19, 21);
        path.lineTo(12, 16);
        path.close();
        if (name.equals("saved")) p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        break;
      case "message":
        c.drawRoundRect(3, 3, 21, 18, 4, 4, p);
        path.moveTo(6, 18);
        path.lineTo(6, 22);
        path.lineTo(11, 18);
        c.drawPath(path, p);
        c.drawLine(8, 8, 16, 8, p);
        c.drawLine(8, 12, 13, 12, p);
        break;
      case "arrow":
        path.moveTo(5, 12);
        path.lineTo(19, 12);
        path.moveTo(13, 6);
        path.lineTo(19, 12);
        path.lineTo(13, 18);
        c.drawPath(path, p);
        break;
      case "back":
        path.moveTo(15, 5);
        path.lineTo(8, 12);
        path.lineTo(15, 19);
        c.drawPath(path, p);
        break;
      case "chevron":
        path.moveTo(9, 5);
        path.lineTo(16, 12);
        path.lineTo(9, 19);
        c.drawPath(path, p);
        break;
      case "expand":
        path.moveTo(5, 15);
        path.lineTo(12, 8);
        path.lineTo(19, 15);
        c.drawPath(path, p);
        break;
      case "navigation":
        path.moveTo(21, 3);
        path.lineTo(14, 21);
        path.lineTo(10, 14);
        path.lineTo(3, 10);
        path.close();
        c.drawPath(path, p);
        break;
      case "search":
        c.drawCircle(10, 10, 6, p);
        c.drawLine(15, 15, 21, 21, p);
        break;
      case "clock":
        c.drawCircle(12, 12, 9, p);
        c.drawLine(12, 6, 12, 12, p);
        c.drawLine(12, 12, 16, 14, p);
        break;
      case "filter":
        c.drawLine(4, 6, 20, 6, p);
        c.drawLine(4, 12, 20, 12, p);
        c.drawLine(4, 18, 20, 18, p);
        c.drawCircle(8, 6, 2, p);
        c.drawCircle(16, 12, 2, p);
        c.drawCircle(10, 18, 2, p);
        break;
      case "settings":
        c.drawCircle(12, 12, 3.2f, p);
        c.drawCircle(12, 12, 7, p);
        for (int i = 0; i < 8; i++) {
          c.save();
          c.rotate(i * 45, 12, 12);
          c.drawLine(12, 2, 12, 5, p);
          c.restore();
        }
        break;
      case "share":
        c.drawCircle(18, 4, 2.8f, p);
        c.drawCircle(5, 12, 2.8f, p);
        c.drawCircle(18, 20, 2.8f, p);
        c.drawLine(7.5f, 10.5f, 15.5f, 5.5f, p);
        c.drawLine(7.5f, 13.5f, 15.5f, 18.5f, p);
        break;
      case "check":
        path.moveTo(5, 12);
        path.lineTo(10, 17);
        path.lineTo(20, 6);
        c.drawPath(path, p);
        break;
      case "plus":
        c.drawLine(12, 5, 12, 19, p);
        c.drawLine(5, 12, 19, 12, p);
        break;
      case "minus":
        c.drawLine(5, 12, 19, 12, p);
        break;
      case "locate":
        c.drawCircle(12, 12, 6, p);
        c.drawCircle(12, 12, 2, p);
        c.drawLine(12, 2, 12, 6, p);
        c.drawLine(12, 18, 12, 22, p);
        c.drawLine(2, 12, 6, 12, p);
        c.drawLine(18, 12, 22, 12, p);
        break;
    }
    c.restore();
  }

  @Override
  public void setAlpha(int alpha) {
    p.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    p.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
