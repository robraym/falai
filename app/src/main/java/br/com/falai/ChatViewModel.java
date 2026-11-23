package br.com.falai;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.UUID;

/** Keeps the draft and idempotency identifier across Activity recreation. */
public final class ChatViewModel extends AndroidViewModel {
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    String chatId, draft = "", pendingId, pendingText;
    boolean busy, failed;
    boolean nudgeBusy;
    String nudgeId;
    int nudgeNotice;
    private String owner;
    public ChatViewModel(@NonNull Application application) { super(application); }
    void init(String chatId) { if (this.chatId == null) { this.chatId = chatId; owner = Backend.uid(); } }
    void changed() { changes.setValue(changes.getValue() + 1); }
    void sendText() {
        if (busy || pendingId != null) return;
        pendingId = UUID.randomUUID().toString(); pendingText = draft.trim(); send();
    }
    void retry() { if (!busy && pendingId != null) send(); }
    private void send() {
        if (!owner.equals(Backend.uid())) { failed = true; changed(); return; }
        busy = true; failed = false; changed();
        Backend.call("sendMessage", Backend.data("chatId", chatId, "messageId", pendingId, "text", pendingText))
                .addOnCompleteListener(task -> {
                    busy = false;
                    if (task.isSuccessful()) { draft = ""; discard(); }
                    else { failed = true; changed(); }
                });
    }
    void discard() {
        if (busy) return;
        pendingId = null; pendingText = null; failed = false; changed();
    }
    void sendNudge() {
        if (nudgeBusy || !owner.equals(Backend.uid())) return;
        if (nudgeId == null) nudgeId = UUID.randomUUID().toString();
        nudgeBusy = true; changed();
        Backend.call("sendNudge", Backend.data("chatId", chatId, "messageId", nudgeId)).addOnCompleteListener(task -> {
            nudgeBusy = false;
            if (task.isSuccessful()) { nudgeId = null; nudgeNotice = R.string.nudge_sent; }
            else {
                Exception error = task.getException();
                boolean limited = error instanceof Backend.ApiException && ("nudge-cooldown".equals(((Backend.ApiException) error).code) || "resource-exhausted".equals(((Backend.ApiException) error).code));
                nudgeNotice = limited ? R.string.nudge_wait : R.string.nudge_error;
            }
            changed();
        });
    }
}
