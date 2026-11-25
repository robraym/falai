package br.com.falai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

/** Decodes bundled animations away from the UI thread and stops playback outside visible holders. */
final class LocalGifView extends View {
    private static final java.util.concurrent.ThreadPoolExecutor DECODER =
            (java.util.concurrent.ThreadPoolExecutor) java.util.concurrent.Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final int resource;
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG
            | android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.DITHER_FLAG);
    private final Runnable frame = this::invalidate;
    private Drawable drawable;
    private Movie movie;
    private java.util.concurrent.Future<?> loading;
    private volatile int generation;
    private boolean allowed = true, visible, failed;
    private long started;

    LocalGifView(Context context, int index) {
        super(context); resource = GifCatalog.FILES[index];
        setContentDescription("GIF · " + context.getString(GifCatalog.LABELS[index]));
    }
    private void load() {
        if (loading != null || drawable != null || movie != null || failed || !isAttachedToWindow() || !visible) return;
        int ticket = generation, target = Math.max(1, Math.min(256, Math.min(getWidth(), getHeight())));
        android.content.res.Resources resources = getResources();
        loading = DECODER.submit(() -> {
            if (ticket != generation) return;
            Drawable image = null; Movie legacy = null;
            try {
                if (Build.VERSION.SDK_INT >= 28) image = decodeNative(resources, resource, target);
                else try (java.io.InputStream input = resources.openRawResource(resource)) {
                    legacy = Movie.decodeStream(input);
                    if (legacy != null) {
                        // Prepare the first legacy frame on this worker, before handing the Movie to the UI.
                        android.graphics.Bitmap first = android.graphics.Bitmap.createBitmap(legacy.width(), legacy.height(), android.graphics.Bitmap.Config.ARGB_8888);
                        legacy.setTime(0); legacy.draw(new Canvas(first), 0, 0); first.recycle();
                    }
                }
            } catch (Exception ignored) { }
            Drawable decoded = image; Movie fallback = legacy;
            MAIN.post(() -> {
                if (ticket != generation || !isAttachedToWindow()) return;
                loading = null; drawable = decoded; movie = fallback; failed = drawable == null && movie == null;
                started = SystemClock.uptimeMillis();
                if (drawable != null) drawable.setCallback(this);
                if (movie != null) setLayerType(LAYER_TYPE_SOFTWARE, null);
                updatePlayback(); invalidate();
            });
        });
    }
    @androidx.annotation.RequiresApi(28)
    private static Drawable decodeNative(android.content.res.Resources resources, int resource, int target) throws java.io.IOException {
        return android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(resources, resource),
                (decoder, info, source) -> decoder.setTargetSize(target, target));
    }
    private boolean playing() {
        boolean enabled = Build.VERSION.SDK_INT >= 26 ? android.animation.ValueAnimator.areAnimatorsEnabled()
                : android.provider.Settings.Global.getFloat(getContext().getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1) > 0;
        return allowed && visible && isAttachedToWindow() && getWindowVisibility() == VISIBLE && enabled;
    }
    private void updatePlayback() {
        removeCallbacks(frame);
        if (Build.VERSION.SDK_INT >= 28 && drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
            android.graphics.drawable.AnimatedImageDrawable animation = (android.graphics.drawable.AnimatedImageDrawable) drawable;
            if (playing()) { if (!animation.isRunning()) animation.start(); }
            else animation.stop();
        }
    }
    void setPlaybackAllowed(boolean play) { allowed = play; updatePlayback(); invalidate(); }
    @Override public void onVisibilityAggregated(boolean shown) { super.onVisibilityAggregated(shown); visible = shown; updatePlayback(); invalidate(); }
    @Override protected void onDetachedFromWindow() {
        generation++; if (loading != null) { loading.cancel(true); loading = null; DECODER.purge(); }
        visible = false; updatePlayback();
        if (drawable != null) { unscheduleDrawable(drawable); drawable.setCallback(null); }
        drawable = null; movie = null; failed = false; super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int state) { super.onWindowVisibilityChanged(state); updatePlayback(); invalidate(); }
    @Override protected boolean verifyDrawable(@androidx.annotation.NonNull Drawable candidate) { return candidate == drawable || super.verifyDrawable(candidate); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawable == null && movie == null) { load(); return; }
        if (drawable != null) {
            int side = Math.min(getWidth(), getHeight()), x = (getWidth() - side) / 2, y = (getHeight() - side) / 2;
            drawable.setBounds(x, y, x + side, y + side); drawable.draw(canvas); updatePlayback(); return;
        }
        boolean play = playing();
        movie.setTime(play ? (int) ((SystemClock.uptimeMillis() - started) % Math.max(1, movie.duration())) : 0);
        float scale = Math.min(getWidth() / (float) movie.width(), getHeight() / (float) movie.height());
        canvas.save(); canvas.translate((getWidth() - movie.width() * scale) / 2, (getHeight() - movie.height() * scale) / 2);
        canvas.scale(scale, scale); movie.draw(canvas, 0, 0, paint); canvas.restore();
        removeCallbacks(frame); if (play) postDelayed(frame, 40);
    }
}
