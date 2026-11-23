package br.com.falai;

import android.content.Context;
import android.util.LruCache;
import androidx.appcompat.widget.AppCompatTextView;
import com.google.firebase.firestore.ListenerRegistration;

/** A public about line, kept separate from private profile/email data. */
final class AboutTextView extends AppCompatTextView {
    private static final LruCache<String, String> cache = new LruCache<>(200);
    private String uid = "", fallback = "";
    private ListenerRegistration listener;
    public AboutTextView(Context context) { super(context); }
    void bind(String owner, String fallbackText) {
        if (listener != null) { listener.remove(); listener = null; }
        uid = owner == null ? "" : owner; fallback = fallbackText;
        String cached = cache.get(uid); setText(cached == null || cached.isEmpty() ? fallback : cached);
        if (isAttachedToWindow()) listen();
    }
    private void listen() {
        if (listener != null || uid.isEmpty() || !Backend.ready() || Backend.uid().isEmpty()) return;
        String requested = uid;
        listener = Backend.db().collection("profileAbout").document(uid).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null || !requested.equals(uid)) return;
            String text = doc.getString("text"); if (text == null) text = "";
            cache.put(requested, text); setText(text.isEmpty() ? fallback : text);
        });
    }
    private void stop() { if (listener != null) { listener.remove(); listener = null; } }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); listen(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE && isAttachedToWindow()) listen(); else stop();
    }
}
