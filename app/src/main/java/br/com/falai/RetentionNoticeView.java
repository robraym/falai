package br.com.falai;

import android.content.Context;
import android.view.Gravity;
import androidx.appcompat.widget.AppCompatTextView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;

/** Shared disclosure only: remote settings never change this device's retention. */
final class RetentionNoticeView extends AppCompatTextView {
    private String owner = "", other = "", name = "";
    private DocumentSnapshot mine, theirs;
    private ListenerRegistration mineListener, theirListener;
    RetentionNoticeView(Context context) {
        super(context); setTextSize(12); setGravity(Gravity.CENTER);
        setTextColor(context.getColor(R.color.text_secondary));
        setPadding(Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6));
        setVisibility(GONE);
    }
    void bind(String uid, String name) {
        if (!other.equals(uid) || !owner.equals(Backend.uid())) {
            stop(); owner = Backend.uid(); other = uid; mine = null; theirs = null;
        }
        this.name = name; render(); if (isAttachedToWindow() && getWindowVisibility() == VISIBLE) listen();
    }
    void setContactName(String name) { this.name = name; render(); }
    private String durations(DocumentSnapshot doc) {
        Object raw = doc == null ? null : doc.get("durations");
        if (!(raw instanceof List)) return "";
        List<String> labels = new ArrayList<>();
        for (Object value : (List<?>) raw) if (value instanceof Number) {
            int hours = ((Number) value).intValue();
            for (int i = 1; i < HistoryRetention.HOURS.length; i++) if (hours == HistoryRetention.HOURS[i]) labels.add(getContext().getString(HistoryRetention.LABELS[i]));
        }
        return android.text.TextUtils.join(", ", labels);
    }
    private void render() {
        List<String> lines = new ArrayList<>(); String own = durations(mine), remote = durations(theirs);
        if (!own.isEmpty()) lines.add(getContext().getString(R.string.retention_notice_you, own));
        else if (HistoryRetention.selected(getContext()) != 0) lines.add(getContext().getString(R.string.retention_notice_you,
            getContext().getString(HistoryRetention.LABELS[HistoryRetention.selected(getContext())])));
        else if (mine != null && mine.exists()) lines.add(getContext().getString(R.string.retention_notice_off_you));
        if (!remote.isEmpty()) lines.add(getContext().getString(R.string.retention_notice_other, name, remote));
        else if (theirs != null && theirs.exists()) lines.add(getContext().getString(R.string.retention_notice_off_other, name));
        if (!lines.isEmpty()) lines.add(getContext().getString(R.string.retention_notice_scope));
        setText(android.text.TextUtils.join("\n", lines)); setVisibility(lines.isEmpty() ? GONE : VISIBLE);
    }
    private void listen() {
        if (!Backend.ready() || owner.isEmpty() || other.isEmpty() || !owner.equals(Backend.uid())) return;
        String requestedOwner = owner, requestedOther = other;
        if (mineListener == null) mineListener = Backend.db().collection("profileRetention").document(owner).addSnapshotListener((doc, error) -> {
            if (error == null && doc != null && requestedOwner.equals(owner) && owner.equals(Backend.uid())) { mine = doc; render(); }
        });
        if (theirListener == null) theirListener = Backend.db().collection("profileRetention").document(other).addSnapshotListener((doc, error) -> {
            if (error == null && doc != null && requestedOwner.equals(owner) && requestedOther.equals(other) && owner.equals(Backend.uid())) { theirs = doc; render(); }
        });
    }
    private void stop() {
        if (mineListener != null) { mineListener.remove(); mineListener = null; }
        if (theirListener != null) { theirListener.remove(); theirListener = null; }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (getWindowVisibility() == VISIBLE) listen(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) listen(); else stop();
    }
}
