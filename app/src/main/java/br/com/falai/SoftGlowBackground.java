package br.com.falai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Quiet background with the same accent glow used by the update screen. */
final class SoftGlowBackground extends Drawable {
    private final Paint base = new Paint(), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, radiusLimit;

    SoftGlowBackground(Context context) {
        base.setColor(context.getColor(R.color.background));
        accent = androidx.core.graphics.ColorUtils.setAlphaComponent(Appearance.primary(context), Appearance.dark(context) ? 35 : 24);
        radiusLimit = Ui.dp(context, 260);
    }
    @Override protected void onBoundsChange(Rect bounds) {
        float radius = Math.min(bounds.width() * .65f, radiusLimit);
        glow.setShader(radius > 0 ? new RadialGradient(bounds.exactCenterX(), bounds.top + bounds.height() * .48f,
                radius, new int[]{accent, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP) : null);
    }
    @Override public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), base);
        if (glow.getShader() != null) canvas.drawRect(getBounds(), glow);
    }
    @Override public void setAlpha(int alpha) { base.setAlpha(alpha); glow.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { base.setColorFilter(filter); glow.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
