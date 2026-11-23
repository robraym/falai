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
        float tail = 6 * density, radius = 10 * density;
        float left = bounds.left + (mine ? 0 : tail), right = bounds.right - (mine ? tail : 0);
        shape.reset(); shape.addRoundRect(left, bounds.top, right, bounds.bottom, radius, radius, Path.Direction.CW);
        if (mine) { shape.moveTo(right - radius, bounds.top); shape.lineTo(bounds.right, bounds.top); shape.lineTo(right, bounds.top + radius); }
        else { shape.moveTo(left + radius, bounds.top); shape.lineTo(bounds.left, bounds.top); shape.lineTo(left, bounds.top + radius); }
        shape.close();
    }
    @Override public void draw(Canvas canvas) { canvas.drawPath(shape, paint); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
