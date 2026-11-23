package br.com.falai;

import android.content.Context;
import android.util.LruCache;
import androidx.appcompat.widget.AppCompatTextView;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.function.Consumer;

/** Public name only; never reads another person's private user document. */
final class ProfileNameView extends AppCompatTextView {
    private static final LruCache<String, String> cache = new LruCache<>(200);
    private String uid = "", fallback = "";
    private ListenerRegistration listener;
    private Consumer<String> changed;
    ProfileNameView(Context context) { super(context); }
    static String current(String uid, String fallback) {
        String name = uid == null || uid.isEmpty() ? null : cache.get(uid);
        return name == null ? fallback : name;
    }
    void bind(String uid, String fallback, Consumer<String> changed) {
        String owner = uid == null ? "" : uid;
        if (!this.uid.equals(owner)) stop();
        this.uid = owner; this.fallback = fallback == null ? "" : fallback; this.changed = changed;
        display(current(owner, this.fallback));
        if (isAttachedToWindow() && getWindowVisibility() == VISIBLE) listen();
    }
    private void display(String name) { setText(name); if (changed != null) changed.accept(name); }
    private void listen() {
        if (listener != null || uid.isEmpty() || !Backend.ready() || Backend.uid().isEmpty()) return;
        String requested = uid;
        listener = Backend.db().collection("profileNames").document(uid).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null || !requested.equals(uid)) return;
            if (!doc.exists() && doc.getMetadata().isFromCache()) return;
            String name = doc.getString("name");
            if (name == null || name.trim().isEmpty()) { cache.remove(requested); display(fallback); }
            else { cache.put(requested, name); display(name); }
        });
    }
    private void stop() { if (listener != null) { listener.remove(); listener = null; } }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (getWindowVisibility() == VISIBLE) listen(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) listen(); else stop();
    }
}
