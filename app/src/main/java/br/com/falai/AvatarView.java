package br.com.falai;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;

final class AvatarView extends View {
    private static final class Entry {
        final Bitmap bitmap; final long time = SystemClock.elapsedRealtime();
        Entry(Bitmap bitmap) { this.bitmap = bitmap; }
    }
    private static final LruCache<String, Entry> cache = new LruCache<String, Entry>(4096) {
        @Override protected int sizeOf(String key, Entry value) { return value.bitmap == null ? 1 : Math.max(1, value.bitmap.getAllocationByteCount() / 1024); }
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private Bitmap bitmap;
    private BitmapShader shader;
    private com.google.firebase.firestore.ListenerRegistration listener;
    private String owner = "", initial = "?";
    public AvatarView(Context context) { super(context); }
    static void invalidatePhoto(String uid) { cache.remove(uid); }
    static Bitmap cachedPhoto(String uid) { Entry entry = cache.get(uid); return entry == null ? null : entry.bitmap; }
    static boolean photoFresh(String uid) { Entry entry = cache.get(uid); return entry != null && SystemClock.elapsedRealtime() - entry.time < 600000; }
    static void cachePhoto(String uid, Bitmap photo) { cache.put(uid, new Entry(photo)); }
    void load(String uid, String name) {
        initial = name == null || name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
        if (uid != null && uid.equals(owner) && listener != null) { invalidate(); return; }
        owner = uid == null ? "" : uid;
        if (listener != null) { listener.remove(); listener = null; }
        setPhoto(null);
        if (owner.isEmpty() || !Backend.ready() || Backend.uid().isEmpty()) return;
        Entry entry = cache.get(owner);
        if (entry != null) setPhoto(entry.bitmap);
        if (isAttachedToWindow()) listen();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); listen(); }
    @Override protected void onDetachedFromWindow() { if (listener != null) { listener.remove(); listener = null; } super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) listen();
        else if (listener != null) { listener.remove(); listener = null; }
    }
    private void listen() {
        if (listener != null || owner.isEmpty() || !Backend.ready() || Backend.uid().isEmpty()) return;
        String requested = owner;
        listener = Backend.db().collection("profilePhotos").document(requested).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null) return;
            Bitmap photo = ProfilePhotos.decode(doc.getString("photo")); cachePhoto(requested, photo);
            if (requested.equals(owner)) setPhoto(photo);
        });
    }
    private void setPhoto(Bitmap photo) { bitmap = photo; shader = photo == null ? null : new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); float radius = Math.min(getWidth(), getHeight()) / 2f, x = getWidth() / 2f, y = getHeight() / 2f;
        paint.setShader(null); paint.setColor(Appearance.soft(getContext())); canvas.drawCircle(x, y, radius, paint);
        if (bitmap != null) {
            matrix.setScale(radius * 2 / bitmap.getWidth(), radius * 2 / bitmap.getHeight()); matrix.postTranslate(x - radius, y - radius); shader.setLocalMatrix(matrix);
            paint.setShader(shader); canvas.drawCircle(x, y, radius, paint); paint.setShader(null);
        } else {
            paint.setColor(Appearance.primary(getContext())); paint.setTextSize(radius * .8f); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(initial, x, y - (paint.ascent() + paint.descent()) / 2, paint);
        }
    }
}
