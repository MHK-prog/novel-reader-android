package com.novelreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small code-drawn icons keep the Android app offline and dependency-free. */
final class NativeIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String name;
    private int tint;
    private boolean filled;

    NativeIconView(Context context, String name, int tint, boolean filled) {
        super(context);
        this.name = name;
        this.tint = tint;
        this.filled = filled;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setStyle(int color, boolean isFilled) { tint = color; filled = isFilled; invalidate(); }
    void setName(String value) { name = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(tint);
        paint.setStrokeWidth(1.9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        switch (name) {
            case "menu": line(canvas, 4, 6, 20, 6); line(canvas, 4, 12, 20, 12); line(canvas, 4, 18, 20, 18); break;
            case "add": line(canvas, 12, 5, 12, 19); line(canvas, 5, 12, 19, 12); break;
            case "search":
                canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); line(canvas, 15.5f, 15.5f, 21, 21); break;
            case "filter":
                path(canvas, "M3 5h18l-7 8v5l-4 2v-7z"); break;
            case "back": line(canvas, 19, 12, 5, 12); line(canvas, 5, 12, 11, 6); line(canvas, 5, 12, 11, 18); break;
            case "next": line(canvas, 5, 12, 19, 12); line(canvas, 19, 12, 13, 6); line(canvas, 19, 12, 13, 18); break;
            case "edit": path(canvas, "M4 16.5 15.8 4.7a2.2 2.2 0 0 1 3.1 0l.4.4a2.2 2.2 0 0 1 0 3.1L7.5 20H4z"); line(canvas, 13.5f, 7, 17, 10.5f); break;
            case "book":
                path(canvas, "M12 7v14m0-14C9.5 4.8 6.8 4 3 4v14c3.8 0 6.5.8 9 3m0-14c2.5-2.2 5.2-3 9-3v14c-3.8 0-6.5.8-9 3"); break;
            case "tag":
                path(canvas, "M20.5 13.2 13.2 20.5a1.7 1.7 0 0 1-2.4 0l-7.3-7.3V4h9.2l7.8 7.8a1 1 0 0 1 0 1.4Z");
                canvas.drawCircle(7.3f, 7.3f, 1.2f, paint); break;
            case "like": case "dislike": drawThumb(canvas, "dislike".equals(name)); break;
            case "check": path(canvas, "m5 12 4 4L19 6"); break;
            case "close": line(canvas, 6, 6, 18, 18); line(canvas, 18, 6, 6, 18); break;
            case "scrolltop": line(canvas, 12, 19, 12, 5); line(canvas, 6, 11, 12, 5); line(canvas, 18, 11, 12, 5); break;
            case "scrollbottom": line(canvas, 12, 5, 12, 19); line(canvas, 6, 13, 12, 19); line(canvas, 18, 13, 12, 19); break;
            case "folder": path(canvas, "M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"); break;
            default: canvas.drawCircle(12, 12, 7, paint);
        }
        canvas.restore();
    }

    private void drawThumb(Canvas canvas, boolean pointingDown) {
        if (pointingDown) { canvas.save(); canvas.rotate(180, 12, 12); }
        Path p = new Path();
        p.moveTo(7.2f, 10.4f); p.lineTo(10.7f, 4.1f); p.quadTo(12, 2, 13.3f, 3.1f);
        p.quadTo(14.2f, 3.9f, 13.7f, 5.4f); p.lineTo(12.7f, 8.2f); p.lineTo(18.2f, 8.2f);
        p.quadTo(20.2f, 8.2f, 19.7f, 10.4f); p.lineTo(18.3f, 18.2f);
        p.quadTo(18, 20, 16.2f, 20); p.lineTo(7.2f, 20); p.close();
        if (filled) { paint.setStyle(Paint.Style.FILL); canvas.drawPath(p, paint); }
        else canvas.drawPath(p, paint);
        RectF cuff = new RectF(3.8f, 10.2f, 7.2f, 20.2f);
        if (filled) canvas.drawRoundRect(cuff, 1.1f, 1.1f, paint); else canvas.drawRoundRect(cuff, 1.1f, 1.1f, paint);
        if (pointingDown) canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, paint); }
    private void path(Canvas c, String data) {
        // These icons use a compact, fixed set of line/curve commands drawn directly below.
        Path p = new Path();
        if (data.startsWith("M4 16.5")) {
            p.moveTo(4,16.5f); p.lineTo(15.8f,4.7f); p.quadTo(17.35f,3.15f,18.9f,4.7f);
            p.lineTo(19.3f,5.1f); p.quadTo(20.85f,6.65f,19.3f,8.2f); p.lineTo(7.5f,20); p.lineTo(4,20); p.close();
        } else if (data.startsWith("M12 7")) {
            p.moveTo(12,7); p.cubicTo(9.5f,4.8f,6.8f,4,3,4); p.lineTo(3,18); p.cubicTo(6.8f,18,9.5f,18.8f,12,21);
            p.moveTo(12,7); p.cubicTo(14.5f,4.8f,17.2f,4,21,4); p.lineTo(21,18); p.cubicTo(17.2f,18,14.5f,18.8f,12,21);
            p.moveTo(12,7); p.lineTo(12,21);
        } else if (data.startsWith("M20.5")) {
            p.moveTo(20.5f,13.2f); p.lineTo(13.2f,20.5f); p.quadTo(12,21.7f,10.8f,20.5f); p.lineTo(3.5f,13.2f);
            p.lineTo(3.5f,4); p.lineTo(12.7f,4); p.lineTo(20.5f,11.8f); p.quadTo(21.2f,12.5f,20.5f,13.2f);
        } else if (data.startsWith("M3 5")) {
            p.moveTo(3,5); p.lineTo(21,5); p.lineTo(14,13); p.lineTo(14,18); p.lineTo(10,20); p.lineTo(10,13); p.close();
        } else if (data.startsWith("m5 12")) {
            p.moveTo(5,12); p.lineTo(9,16); p.lineTo(19,6);
        }
        c.drawPath(p, paint);
    }
}
