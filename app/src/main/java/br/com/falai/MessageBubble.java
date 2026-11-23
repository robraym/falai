package br.com.falai;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

final class MessageBubble extends Drawable {
    private final boolean mine;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    MessageBubble(Context context, boolean mine) {
        this.mine = mine; density = context.getResources().getDisplayMetrics().density;
        paint.setColor(mine ? (Appearance.selected(context) == 0 ? context.getColor(R.color.bubble_sent) : Appearance.soft(context)) : context.getColor(R.color.bubble_received));
    }
    @Override protected void onBoundsChange(Rect bounds) {
        float tail = Math.min(6 * density, bounds.width() / 4f);
        float radius = Math.min(10 * density, Math.min((bounds.width() - tail) / 2f, bounds.height() / 2f));
        float left = bounds.left, right = bounds.right - tail, top = bounds.top, bottom = bounds.bottom;
        float tip = Math.min(density, tail / 3f);
        // A single outline joins the curved tail to the body. Overlapping subpaths
        // can leave a visible seam where a separate triangle meets a rounded corner.
        shape.reset(); shape.moveTo(left + radius, top);
        shape.lineTo(bounds.right - tip, top);
        shape.quadTo(bounds.right, top, bounds.right - tip, top + 2 * tip);
        shape.cubicTo(bounds.right - 3 * tip, top + radius * .5f, right, top + radius * .7f, right, top + radius * 1.2f);
        shape.lineTo(right, bottom - radius);
        shape.quadTo(right, bottom, right - radius, bottom);
        shape.lineTo(left + radius, bottom); shape.quadTo(left, bottom, left, bottom - radius);
        shape.lineTo(left, top + radius); shape.quadTo(left, top, left + radius, top);
        shape.close();
        if (!mine) {
            Matrix mirror = new Matrix(); mirror.setScale(-1, 1, bounds.exactCenterX(), bounds.exactCenterY());
            shape.transform(mirror);
        }
    }
    @Override public void draw(Canvas canvas) { canvas.drawPath(shape, paint); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
