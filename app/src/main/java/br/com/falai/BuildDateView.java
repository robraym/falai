package br.com.falai;

import android.content.Context;
import androidx.appcompat.widget.AppCompatTextView;
import com.google.firebase.firestore.ListenerRegistration;

/** Public build date of the last installation to register, without exposing device tokens. */
final class BuildDateView extends AppCompatTextView {
    private final String uid;
    private ListenerRegistration listener;
    BuildDateView(Context context, String uid) {
        super(context); this.uid = uid; setTextSize(11); setTextColor(context.getColor(R.color.text_secondary));
        setText(R.string.apk_build_unknown);
    }
    private void listen() {
        if (listener != null || uid.isEmpty() || !Backend.ready() || Backend.uid().isEmpty()) return;
        listener = Backend.db().collection("profileBuild").document(uid).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null) return;
            com.google.firebase.Timestamp build = doc.getTimestamp("buildTime");
            setText(build == null ? getContext().getString(R.string.apk_build_unknown)
                : getContext().getString(R.string.apk_build_date, android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", build.toDate())));
        });
    }
    private void stop() { if (listener != null) { listener.remove(); listener = null; } }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); listen(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE && isAttachedToWindow()) listen(); else stop();
    }
}
