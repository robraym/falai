package br.com.falai;

import android.content.Context;
import androidx.appcompat.widget.AppCompatTextView;
import com.google.firebase.firestore.ListenerRegistration;

/** Version of the last installation to register, without exposing device tokens. */
final class AppVersionView extends AppCompatTextView {
    private final String uid;
    private ListenerRegistration listener;
    AppVersionView(Context context, String uid) {
        super(context); this.uid = uid; setTextSize(11); setTextColor(context.getColor(R.color.text_secondary));
        setText(R.string.apk_version_unknown);
    }
    private void listen() {
        if (listener != null || uid.isEmpty() || !Backend.ready() || Backend.uid().isEmpty()) return;
        listener = Backend.db().collection("profileBuild").document(uid).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null) return;
            String version = doc.getString("versionName"); Long code = doc.getLong("versionCode");
            setText(version == null || code == null ? getContext().getString(R.string.apk_version_unknown)
                : getContext().getString(R.string.apk_version, version, code));
        });
    }
    private void stop() { if (listener != null) { listener.remove(); listener = null; } }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); listen(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE && isAttachedToWindow()) listen(); else stop();
    }
}
