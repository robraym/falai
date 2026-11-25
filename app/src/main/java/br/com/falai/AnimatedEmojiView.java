package br.com.falai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Local glyph animation; no downloaded media, bitmaps, or background playback. */
final class AnimatedEmojiView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    private final String glyph;
    private ValueAnimator animator;
    private float phase;
    private long lastFrame;
    private boolean visible;
    private boolean playbackAllowed = true;
    AnimatedEmojiView(Context context, String emoji) {
        super(context);
        kind = AnimatedEmoji.index(emoji);
        glyph = kind < 0 ? emoji : AnimatedEmoji.VALUES.get(kind);
        setContentDescription(glyph);
        paint.setTextAlign(Paint.Align.CENTER);
    }
    private void updatePlayback() {
        boolean enabled = android.os.Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled()
                : android.provider.Settings.Global.getFloat(getContext().getContentResolver(),
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
        boolean play = playbackAllowed && kind >= 0 && isAttachedToWindow() && visible && getWindowVisibility() == VISIBLE && enabled;
        if (!play) {
            if (animator != null) { animator.cancel(); animator = null; }
            phase = 0; invalidate(); return;
        }
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(kind == 7 ? 2600 : 1800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            long now = SystemClock.uptimeMillis();
            if (now - lastFrame >= 33) { lastFrame = now; invalidate(); }
        });
        animator.start();
    }
    void setPlaybackAllowed(boolean allowed) { playbackAllowed = allowed; updatePlayback(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updatePlayback(); }
    @Override protected void onDetachedFromWindow() {
        visible = false; updatePlayback(); super.onDetachedFromWindow();
    }
    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible); visible = isVisible; updatePlayback();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); updatePlayback();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        float wave = (float) Math.sin(phase * Math.PI * 2), fast = (float) Math.sin(phase * Math.PI * 4);
        float scale = 1, rotation = 0, lift = 0;
        switch (kind) {
            case 1: rotation = wave * 12; lift = -Math.abs(fast) * side * .035f; break;
            case 2: case 3: scale = 1 + wave * .06f; rotation = wave * 5; break;
            case 4: rotation = wave * 7; break;
            case 5: lift = wave * side * .035f; rotation = fast * 4; break;
            case 6: rotation = fast * 9; break;
            case 7: scale = 1 + wave * .035f; rotation = wave * 3; break;
            case 8: lift = -Math.abs(wave) * side * .08f; rotation = wave * 8; break;
            case 9: scale = 1 + fast * .08f; rotation = fast * 8; break;
            case 10: scale = 1 + Math.max(0, fast) * .14f; break;
            case 11: rotation = wave * 12; scale = 1 + wave * .05f; break;
            default:
                // Reuse light local movements across the full Unicode catalogue.
                switch (Math.max(0, kind) % 6) {
                    case 1: rotation = wave * 9; break;
                    case 2: scale = 1 + wave * .07f; break;
                    case 3: rotation = wave * 6; lift = -Math.abs(wave) * side * .04f; break;
                    case 4: rotation = fast * 5; break;
                    case 5: scale = 1 + Math.max(0, fast) * .08f; break;
                    default: lift = -Math.abs(wave) * side * .06f;
                }
        }
        paint.setTextSize(side * .62f); paint.setAlpha(255);
        canvas.save(); canvas.translate(getWidth() / 2f, getHeight() / 2f + lift);
        canvas.rotate(rotation); canvas.scale(scale, scale);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(glyph, 0, -(metrics.ascent + metrics.descent) / 2, paint); canvas.restore();
        if ((kind == 2 || kind == 3 || kind == 11) && animator != null) {
            paint.setTextSize(side * .15f);
            for (int i = 0; i < 3; i++) {
                float travel = (phase + i / 3f) % 1f;
                paint.setAlpha(Math.round((1 - travel) * 220));
                canvas.drawText(kind == 11 ? "✨" : "❤️", getWidth() * (.2f + i * .3f),
                        getHeight() * (.7f - travel * .55f), paint);
            }
            paint.setAlpha(255);
        }
    }
}
