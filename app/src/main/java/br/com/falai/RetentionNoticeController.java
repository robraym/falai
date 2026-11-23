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
    private ListenerRegistration listener;
    RetentionNoticeController(FalaiActivity activity, Consumer<List<MessageAdapter.Notice>> changed, Runnable configure) {
        this.activity = activity; this.changed = changed; this.configure = configure; activity.getLifecycle().addObserver(this);
    }
    void bind(String chatId, String name) {
        if (!this.chatId.equals(chatId) || !owner.equals(Backend.uid())) {
            stop(); owner = Backend.uid(); this.chatId = chatId; events.clear();
        }
        this.name = name; render(); listen();
    }
    void setContactName(String name) { this.name = name; render(); }
    private void render() {
        List<MessageAdapter.Notice> notices = new ArrayList<>();
        for (DocumentSnapshot event : events) {
            Long hours = event.getLong("hours");
            if (hours == null || event.getTimestamp("createdAt") == null) continue;
            String duration = "";
            for (int i = 0; i < HistoryRetention.HOURS.length; i++) if (hours == HistoryRetention.HOURS[i]) duration = activity.getString(HistoryRetention.LABELS[i]);
            if (duration.isEmpty()) continue;
            boolean own = owner.equals(event.getString("changedBy"));
            String text = hours == 0
                ? own ? activity.getString(R.string.retention_notice_off_you) : activity.getString(R.string.retention_notice_off_other, name)
                : own ? activity.getString(R.string.retention_notice_you, duration) : activity.getString(R.string.retention_notice_other, name, duration);
            notices.add(new MessageAdapter.Notice("timer:" + event.getId(), text, activity.getString(R.string.retention_notice_details),
                event.getTimestamp("createdAt").toDate().getTime(), configure));
        }
        changed.accept(notices);
    }
    private void listen() {
        if (!activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)
            || !Backend.ready() || owner.isEmpty() || chatId.isEmpty() || !owner.equals(Backend.uid()) || listener != null) return;
        String requestedOwner = owner, requestedChat = chatId;
        listener = Backend.db().collection("chats").document(chatId).collection("timerEvents")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).addSnapshotListener((snapshot, error) -> {
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
