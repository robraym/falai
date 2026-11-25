package br.com.falai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;
import android.widget.TextView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

/** Typing is ephemeral, never part of message history or notification payloads. */
final class ChatPresence {
    private static final LruCache<String, PresenceSnapshot> CACHE = new LruCache<>(256);
    private static final PresenceSnapshot UNKNOWN = new PresenceSnapshot(0, 0);

    private static final class PresenceSnapshot {
        final long lastSeen, lastActive;
        PresenceSnapshot(long lastSeen, long lastActive) {
            this.lastSeen = lastSeen;
            this.lastActive = lastActive;
        }
    }

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
    private PresenceSnapshot presence = UNKNOWN;
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
        clearListeners(); otherUid = uid; remoteTyping = null;
        String owner = Backend.uid();
        String key = owner + ":" + uid;
        PresenceSnapshot cached = uid.isEmpty() || owner.isEmpty() ? null : CACHE.get(key);
        presence = cached == null ? UNKNOWN : cached;
        render();
        if (!resumed || uid.isEmpty() || owner.isEmpty()) return;
        if (showTyping) typingListener = Backend.db().collection("chats").document(chatId).collection("typing").document(uid).addSnapshotListener(MetadataChanges.INCLUDE, (doc, error) -> {
            remoteTyping = error == null ? doc : null; render();
        });
        presenceListener = Backend.db().collection("presence").document(uid).collection("sessions").orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(20).addSnapshotListener(MetadataChanges.INCLUDE, (docs, error) -> {
            if (error != null || docs == null || !owner.equals(Backend.uid())) return;
            boolean fromCache = docs.getMetadata().isFromCache();
            if (fromCache && presence != UNKNOWN) return;
            long lastSeen = 0, lastActive = 0, now = System.currentTimeMillis();
            for (DocumentSnapshot session : docs) {
                com.google.firebase.Timestamp timestamp = session.getTimestamp("updatedAt");
                if (timestamp == null || session.getMetadata().hasPendingWrites()) continue;
                long time = timestamp.toDate().getTime();
                lastSeen = Math.max(lastSeen, time);
                if (time <= now + 5000 && Boolean.TRUE.equals(session.getBoolean("active"))) {
                    lastActive = Math.max(lastActive, time);
                }
            }
            if (fromCache && lastSeen == 0) return;
            presence = new PresenceSnapshot(lastSeen, lastActive);
            CACHE.put(key, presence);
            render();
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
        long now = System.currentTimeMillis(), last = presence.lastSeen;
        boolean online = presence.lastActive > 0 && now - presence.lastActive < 70000
                && presence.lastActive <= now + 5000;
        if (remoteTyping != null && !remoteTyping.getMetadata().isFromCache() && Boolean.TRUE.equals(remoteTyping.getBoolean("active"))) {
            com.google.firebase.Timestamp time = remoteTyping.getTimestamp("updatedAt");
            if (time != null && now - time.toDate().getTime() < 8000 && time.toDate().getTime() <= now + 5000) { setLabel(R.string.typing); return; }
        }
        if (!showTyping) {
            int color = online ? Appearance.primary(context) : context.getColor(R.color.text_secondary);
            if (label.getCurrentTextColor() != color) {
                label.setTextColor(color);
                if (label instanceof PresenceStatusView) ((PresenceStatusView) label).refreshBadge();
            }
            if (online) { setLabel(R.string.online); return; }
            if (last == 0) { setLabel(""); return; }
            long minutes = Math.max(0, (now - last) / 60000);
            if (minutes == 0) setLabel(R.string.offline_now);
            else if (minutes < 60) setLabel(context.getString(R.string.offline_minutes, minutes));
            else if (minutes < 1440) setLabel(context.getString(R.string.offline_hours, minutes / 60));
            else setLabel(context.getString(R.string.offline_days, minutes / 1440));
            return;
        }
        if (online) { setLabel(R.string.online); return; }
        if (last == 0) { setLabel(""); return; }
        long minutes = Math.max(0, (now - last) / 60000);
        if (minutes == 0) setLabel(R.string.last_seen_now);
        else if (minutes < 60) setLabel(context.getString(R.string.last_seen_minutes, minutes));
        else if (minutes < 1440) setLabel(context.getString(R.string.last_seen_hours, minutes / 60));
        else setLabel(context.getString(R.string.last_seen_date, android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", last)));
    }
    private void setLabel(int resource) { setLabel(context.getText(resource)); }
    private void setLabel(CharSequence text) {
        if (!TextUtils.equals(label.getText(), text)) label.setText(text);
        if (label instanceof PresenceStatusView) {
            int visibility = TextUtils.isEmpty(text) ? TextView.GONE : TextView.VISIBLE;
            if (label.getVisibility() != visibility) label.setVisibility(visibility);
        }
    }
}
