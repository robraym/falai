package br.com.falai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;

/** Typing is ephemeral, never part of message history or notification payloads. */
final class ChatPresence {
    private final Context context;
    private final String chatId;
    private final TextView label;
    private final boolean showTyping;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListenerRegistration typingListener, presenceListener;
    private String otherUid = "";
    private boolean resumed, typing;
    private long lastSent;
    private DocumentSnapshot remoteTyping;
    private List<DocumentSnapshot> sessions = new ArrayList<>();
    private final Runnable stop = this::stopTyping;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); if (resumed) handler.postDelayed(this, 1000); }
    };
    ChatPresence(Context context, String chatId, TextView label) { this(context, chatId, label, true); }
    ChatPresence(Context context, String chatId, TextView label, boolean showTyping) {
        this.context = context; this.chatId = chatId; this.label = label; this.showTyping = showTyping;
    }
    void listen(String uid) {
        if (uid.equals(otherUid) && presenceListener != null) return;
        clearListeners(); otherUid = uid;
        if (!resumed || uid.isEmpty()) return;
        if (showTyping) typingListener = Backend.db().collection("chats").document(chatId).collection("typing").document(uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (doc, error) -> {
            remoteTyping = error == null ? doc : null; render();
        });
        presenceListener = Backend.db().collection("presence").document(uid).collection("sessions").orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(20).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (docs, error) -> {
            sessions = error == null && docs != null ? docs.getDocuments() : new ArrayList<>(); render();
        });
    }
    void resume() { resumed = true; listen(otherUid); handler.removeCallbacks(refresh); refresh.run(); }
    void pause() { resumed = false; stopTyping(); clearListeners(); handler.removeCallbacks(refresh); }
    private void clearListeners() {
        if (typingListener != null) typingListener.remove(); if (presenceListener != null) presenceListener.remove();
        typingListener = null; presenceListener = null;
    }
    void changed(boolean hasText) {
        handler.removeCallbacks(stop);
        if (!resumed || !hasText) { stopTyping(); return; }
        long now = SystemClock.elapsedRealtime();
        if (!typing || now - lastSent >= 4000) {
            typing = true; lastSent = now; writeTyping(true);
        }
        handler.postDelayed(stop, 2500);
    }
    void stopTyping() { handler.removeCallbacks(stop); if (typing) { typing = false; writeTyping(false); } }
    private void writeTyping(boolean active) {
        if (Backend.uid().isEmpty()) return;
        Backend.db().collection("chats").document(chatId).collection("typing").document(Backend.uid())
            .set(Backend.data("active", active, "updatedAt", FieldValue.serverTimestamp()));
    }
    private void render() {
        long now = System.currentTimeMillis(), last = 0;
        boolean online = false;
        for (DocumentSnapshot session : sessions) {
            com.google.firebase.Timestamp timestamp = session.getTimestamp("updatedAt"); if (timestamp == null) continue;
            long time = timestamp.toDate().getTime(); last = Math.max(last, time);
            if (!session.getMetadata().isFromCache() && Boolean.TRUE.equals(session.getBoolean("active")) && now - time < 70000 && time <= now + 5000) online = true;
        }
        if (remoteTyping != null && !remoteTyping.getMetadata().isFromCache() && Boolean.TRUE.equals(remoteTyping.getBoolean("active"))) {
            com.google.firebase.Timestamp time = remoteTyping.getTimestamp("updatedAt");
            if (time != null && now - time.toDate().getTime() < 8000 && time.toDate().getTime() <= now + 5000) { label.setText(R.string.typing); return; }
        }
        if (!showTyping) {
            label.setTextColor(online ? Appearance.primary(context) : context.getColor(R.color.text_secondary));
            if (label instanceof PresenceStatusView) ((PresenceStatusView) label).refreshBadge();
            if (online) { label.setText(R.string.online); return; }
            if (last == 0) { label.setText(R.string.presence_unknown); return; }
            long minutes = Math.max(0, (now - last) / 60000);
            if (minutes == 0) label.setText(R.string.offline_now);
            else if (minutes < 60) label.setText(context.getString(R.string.offline_minutes, minutes));
            else if (minutes < 1440) label.setText(context.getString(R.string.offline_hours, minutes / 60));
            else label.setText(context.getString(R.string.offline_days, minutes / 1440));
            return;
        }
        if (online) { label.setText(R.string.online); return; }
        if (last == 0) { label.setText(""); return; }
        long minutes = Math.max(0, (now - last) / 60000);
        if (minutes == 0) label.setText(R.string.last_seen_now);
        else if (minutes < 60) label.setText(context.getString(R.string.last_seen_minutes, minutes));
        else if (minutes < 1440) label.setText(context.getString(R.string.last_seen_hours, minutes / 60));
        else label.setText(context.getString(R.string.last_seen_date, android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", last)));
    }
}
