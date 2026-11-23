package br.com.falai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.widget.AppCompatTextView;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

/** Listens only while this conversation row is visible; restores the preview on expiry. */
final class TypingPreviewView extends AppCompatTextView {
    private final String chatId, otherUid, preview;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListenerRegistration listener;
    private View checks;
    private boolean typing;
    private final Runnable expire = () -> showTyping(false);
    TypingPreviewView(Context context, String chatId, String otherUid, String preview) {
        super(context); this.chatId = chatId; this.otherUid = otherUid; this.preview = preview;
        setTextSize(13); showTyping(false);
    }
    void setChecks(View view) { checks = view; checks.setVisibility(typing ? GONE : VISIBLE); }
    private void showTyping(boolean active) {
        typing = active;
        setText(active ? getContext().getString(R.string.typing) : preview);
        setTextColor(active ? Appearance.primary(getContext()) : getContext().getColor(R.color.text_secondary));
        if (checks != null) checks.setVisibility(active ? GONE : VISIBLE);
    }
    private void listen() {
        if (listener != null || !Backend.ready() || Backend.uid().isEmpty()) return;
        listener = Backend.db().collection("chats").document(chatId).collection("typing").document(otherUid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (doc, error) -> {
                    handler.removeCallbacks(expire);
                    com.google.firebase.Timestamp time = error == null && doc != null ? doc.getTimestamp("updatedAt") : null;
                    long age = time == null ? Long.MAX_VALUE : System.currentTimeMillis() - time.toDate().getTime();
                    boolean active = error == null && doc != null && !doc.getMetadata().isFromCache()
                            && Boolean.TRUE.equals(doc.getBoolean("active")) && age >= -5000 && age < 8000;
                    showTyping(active);
                    if (active) handler.postDelayed(expire, Math.max(1, 8000 - age));
                });
    }
    private void stop() {
        if (listener != null) { listener.remove(); listener = null; }
        handler.removeCallbacks(expire); showTyping(false);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (getWindowVisibility() == VISIBLE) listen(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) listen(); else if (handler != null) stop();
    }
}
