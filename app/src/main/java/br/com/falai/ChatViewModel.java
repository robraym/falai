package br.com.falai;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.UUID;

/** Keeps the draft and idempotency identifier across Activity recreation. */
public final class ChatViewModel extends AndroidViewModel {
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    String chatId, draft = "", pendingId, pendingText, pendingKind;
    android.os.Bundle reply;
    boolean busy, failed;
    boolean nudgeBusy;
    String nudgeId;
    int nudgeNotice, sendError;
    private String owner;
    public ChatViewModel(@NonNull Application application) { super(application); }
    void init(String chatId) { if (this.chatId == null) { this.chatId = chatId; owner = Backend.uid(); } }
    void changed() { changes.setValue(changes.getValue() + 1); }
    void sendText() {
        if (busy || pendingId != null) return;
        pendingId = UUID.randomUUID().toString(); pendingText = draft.trim(); sendError = 0; send();
    }
    void retry() { if (!busy && pendingId != null) send(); }
    void sendGif(String value) {
        if (busy || pendingId != null || GifCatalog.index(value) < 0) return;
        pendingId = UUID.randomUUID().toString(); pendingText = value; sendError = 0; send();
    }
    void sendPhoto(String messageId) {
        if (busy || pendingId != null || messageId == null || !messageId.matches("[a-f0-9-]{36}")) return;
        pendingId = messageId; pendingKind = "image"; pendingText = "📷 Foto"; sendError = 0; send();
    }
    void preparePhoto(android.net.Uri uri) {
        if (busy || pendingId != null || uri == null) return;
        pendingId = UUID.randomUUID().toString(); pendingKind = "image"; pendingText = "📷 Foto";
        String id = pendingId; busy = true; failed = false; sendError = 0; changed();
        com.google.android.gms.tasks.Tasks.call(ContactsRepository.IO, () -> ChatMedia.prepare(getApplication(), uri, id))
                .addOnCompleteListener(task -> {
                    busy = false;
                    if (task.isSuccessful()) send(); else {
                        pendingId = null; pendingKind = null; pendingText = null; failed = false;
                        nudgeNotice = R.string.photo_prepare_error; changed();
                    }
                });
    }
    private void send() {
        if (!owner.equals(Backend.uid())) { failed = true; changed(); return; }
        busy = true; failed = false; changed();
        final String messageId = pendingId, text = pendingText, kind = pendingKind;
        final android.os.Bundle replyTo = reply == null ? null : new android.os.Bundle(reply);
        java.util.Map<String, Object> data = Backend.data("chatId", chatId, "messageId", messageId);
        if ("image".equals(kind)) { data.put("kind", "image"); data.put("mediaId", messageId); }
        else data.put("text", text);
        if (replyTo != null) data.put("replyToId", replyTo.getString("id"));
        com.google.android.gms.tasks.Task<?> sendTask = "image".equals(kind)
                ? com.google.android.gms.tasks.Tasks.call(ContactsRepository.IO, () -> ChatMedia.read(getApplication(), messageId))
                    .onSuccessTask(bytes -> Backend.uploadChatMedia(chatId, messageId, bytes))
                    .onSuccessTask(ignored -> Backend.call("sendMessage", data))
                : Backend.call("sendMessage", data);
        sendTask
                .addOnCompleteListener(task -> {
                    busy = false;
                    if (task.isSuccessful()) { if (!"image".equals(kind) && GifCatalog.index(text) < 0) draft = ""; reply = null; clearPending(false); }
                    else {
                        failed = true; Exception error = task.getException();
                        sendError = "image".equals(kind) && error instanceof Backend.ApiException
                                && "resource-exhausted".equals(((Backend.ApiException) error).code)
                                ? R.string.photo_daily_limit : R.string.error_send;
                        changed();
                    }
                });
    }
    void discard() {
        if (busy) return;
        clearPending(true);
    }
    private void clearPending(boolean deletePhoto) {
        if (deletePhoto && "image".equals(pendingKind)) ChatMedia.delete(getApplication(), pendingId);
        pendingId = null; pendingText = null; pendingKind = null; failed = false; sendError = 0; changed();
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
