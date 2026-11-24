package br.com.falai;

import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

final class MessageTimerDialog {
    static void show(FalaiActivity activity, String chatId, int hours, Runnable saved) {
        String[] labels = new String[HistoryRetention.LABELS.length]; int current = 0;
        for (int i = 0; i < labels.length; i++) { labels[i] = activity.getString(HistoryRetention.LABELS[i]); if (HistoryRetention.HOURS[i] == hours) current = i; }
        TextView explanation = Ui.text(activity, activity.getString(chatId == null ? R.string.retention_info : R.string.retention_notice_details), 13, false);
        explanation.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 12), Ui.dp(activity, 24), Ui.dp(activity, 12));
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(chatId == null ? R.string.history_duration : R.string.chat_timer_title).setView(explanation)
            .setSingleChoiceItems(labels, current, (dialog, index) -> {
                dialog.dismiss();
                if (HistoryRetention.HOURS[index] == hours) return;
                String owner = Backend.uid();
                com.google.android.gms.tasks.Task<java.util.Map<String, Object>> task = chatId == null
                    ? Backend.call("setDefaultTimer", Backend.data("hours", HistoryRetention.HOURS[index]))
                    : Backend.call("setChatTimer", Backend.data("chatId", chatId, "hours", HistoryRetention.HOURS[index], "requestId", java.util.UUID.randomUUID().toString()));
                task.addOnSuccessListener(result -> { if (chatId == null && owner.equals(Backend.uid())) HistoryRetention.select(activity.getApplicationContext(), index); });
                task.addOnCompleteListener(activity, result -> {
                    if (!owner.equals(Backend.uid())) return;
                    if (result.isSuccessful()) saved.run();
                    else android.widget.Toast.makeText(activity, Backend.error(activity, result.getException()), android.widget.Toast.LENGTH_LONG).show();
                });
            }).setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    private MessageTimerDialog() { }
}
