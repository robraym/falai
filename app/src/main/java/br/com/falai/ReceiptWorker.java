package br.com.falai;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.android.gms.tasks.Tasks;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Durable acknowledgements, including when a push arrives with the UI closed. */
public final class ReceiptWorker extends Worker {
    public ReceiptWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    static void schedule(String chat, List<String> ids, boolean read) {
        if (ids.isEmpty() || Backend.uid().isEmpty()) return;
        for (int offset = 0; offset < ids.size(); offset += 50) {
            String[] batch = ids.subList(offset, Math.min(offset + 50, ids.size())).toArray(new String[0]);
            String owner = Backend.uid(), state = read ? "read" : "delivered";
            String key = UUID.nameUUIDFromBytes((owner + chat + state + Arrays.toString(batch)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReceiptWorker.class)
                    .setInputData(new Data.Builder().putString("owner", owner).putString("chat", chat)
                            .putString("state", state).putStringArray("ids", batch).build())
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
            WorkManager.getInstance(FalaiApplication.instance).enqueueUniqueWork("receipt-" + key, ExistingWorkPolicy.KEEP, request);
        }
    }
    @NonNull @Override public Result doWork() {
        if (!Backend.ready() || Backend.uid().isEmpty() || !Backend.uid().equals(getInputData().getString("owner"))) return Result.success();
        String[] ids = getInputData().getStringArray("ids");
        if (ids == null) return Result.failure();
        try {
            Tasks.await(Backend.call("acknowledgeMessages", Backend.data("chatId", getInputData().getString("chat"),
                    "state", getInputData().getString("state"), "messageIds", Arrays.asList(ids))), 60, TimeUnit.SECONDS);
            return Result.success();
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof Backend.ApiException) {
                String code = ((Backend.ApiException) cause).code;
                if ("permission-denied".equals(code) || "invalid-argument".equals(code)) return Result.failure();
            }
            return Result.retry();
        }
    }
}
