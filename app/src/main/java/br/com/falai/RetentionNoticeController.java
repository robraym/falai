package br.com.falai;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Participant-only timer changes displayed as system notices in the conversation. */
final class RetentionNoticeController implements DefaultLifecycleObserver {
    private final FalaiActivity activity;
    private final Consumer<List<MessageAdapter.Notice>> changed;
    private final Runnable configure;
    private String owner = "", chatId = "", name = "";
    private final List<DocumentSnapshot> events = new ArrayList<>();
    private DocumentSnapshot current;
    private ListenerRegistration listener;
    RetentionNoticeController(FalaiActivity activity, Consumer<List<MessageAdapter.Notice>> changed, Runnable configure) {
        this.activity = activity; this.changed = changed; this.configure = configure; activity.getLifecycle().addObserver(this);
    }
    void bind(DocumentSnapshot chat, String name) {
        String chatId = chat.getId();
        if (!this.chatId.equals(chatId) || !owner.equals(Backend.uid())) {
            stop(); owner = Backend.uid(); this.chatId = chatId; events.clear();
        }
        current = chat; this.name = name; render(); listen();
    }
    void setContactName(String name) { this.name = name; render(); }
    private void render() {
        List<MessageAdapter.Notice> notices = new ArrayList<>();
        for (DocumentSnapshot event : events) {
            Long hours = event.getLong("hours");
            if (hours == null || event.getTimestamp("createdAt") == null) continue;
            addNotice(notices, "timer:" + event.getId(), hours, event.getString("changedBy"),
                event.getTimestamp("createdAt").toDate().getTime(), "default".equals(event.getId()));
        }
        // The chat is authoritative even when its event is absent, unreadable or not cached yet.
        if (current != null) {
            Long hours = current.getLong("timerHours");
            String by = current.getString("timerChangedBy");
            com.google.firebase.Timestamp at = current.getTimestamp("timerChangedAt");
            if (at == null) at = current.getTimestamp("timerFormatSince");
            List<TimerNoticeState.Change> history = new ArrayList<>();
            for (DocumentSnapshot event : events) {
                Long value = event.getLong("hours");
                com.google.firebase.Timestamp time = event.getTimestamp("createdAt");
                if (value != null && time != null) history.add(new TimerNoticeState.Change(value, event.getString("changedBy"), time.toDate().getTime()));
            }
            long time = at == null ? 0 : at.toDate().getTime();
            if (TimerNoticeState.needsNotice(hours, by, time, history)) {
                addNotice(notices, "timer:current", hours, by, time, false);
            }
        }
        changed.accept(notices);
    }
    private void addNotice(List<MessageAdapter.Notice> notices, String key, long hours, String by, long time, boolean defaultTimer) {
        String duration = "";
        for (int i = 0; i < HistoryRetention.HOURS.length; i++) if (hours == HistoryRetention.HOURS[i]) duration = activity.getString(HistoryRetention.LABELS[i]);
        if (duration.isEmpty()) return;
        boolean own = owner.equals(by);
        String text;
        if (hours == 0) text = own ? activity.getString(R.string.retention_notice_off_you) : activity.getString(R.string.retention_notice_off_other, name);
        else if (defaultTimer) text = own ? activity.getString(R.string.retention_default_notice_you, duration) : activity.getString(R.string.retention_default_notice_other, name, duration);
        else if (by == null || by.isEmpty()) text = activity.getString(R.string.retention_notice_active, duration);
        else text = own ? activity.getString(R.string.retention_notice_you, duration) : activity.getString(R.string.retention_notice_other, name, duration);
        notices.add(new MessageAdapter.Notice(key, text, activity.getString(R.string.retention_notice_details), time, configure));
    }
    private void listen() {
        if (!activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)
            || !Backend.ready() || owner.isEmpty() || chatId.isEmpty() || !owner.equals(Backend.uid()) || listener != null) return;
        String requestedOwner = owner, requestedChat = chatId;
        listener = Backend.db().collection("chats").document(chatId).collection("timerEvents")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).addSnapshotListener((snapshot, error) -> {
                if (error != null && requestedOwner.equals(owner) && requestedChat.equals(chatId)) {
                    android.util.Log.w("TimerNotices", "Timer event history: " + error.getCode());
                    render(); return;
                }
                if (error == null && snapshot != null && requestedOwner.equals(owner) && requestedChat.equals(chatId) && owner.equals(Backend.uid())) {
                    if (snapshot.isEmpty() && snapshot.getMetadata().isFromCache()) return;
                    events.clear(); events.addAll(snapshot.getDocuments()); render();
                }
            });
    }
    private void stop() {
        if (listener != null) { listener.remove(); listener = null; }
    }
    @Override public void onStart(LifecycleOwner owner) { listen(); }
    @Override public void onStop(LifecycleOwner owner) { stop(); }
    @Override public void onDestroy(LifecycleOwner owner) { stop(); activity.getLifecycle().removeObserver(this); }
}
