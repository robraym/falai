package br.com.falai;

import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

final class MessageTimerDialog {
    static void show(FalaiActivity activity, String chatId, int hours, Runnable saved) {
        if (chatId == null) { showSettings(activity, hours, saved); return; }
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
    private static void showSettings(FalaiActivity activity, int hours, Runnable saved) {
        SettingsPage page = new SettingsPage(activity, R.string.history_duration);
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        android.widget.LinearLayout content = Ui.column(activity);
        content.setPadding(0, 0, 0, Ui.dp(activity, 12));
        android.widget.LinearLayout selection = SettingsPage.panel(activity); content.addView(selection);
        selection.addView(Ui.text(activity, activity.getString(R.string.settings_choose_duration), 13, true)); Ui.gap(selection, 8);
        android.widget.RadioGroup options = new android.widget.RadioGroup(activity);
        for (int i = 0; i < HistoryRetention.HOURS.length; i++) {
            final int index = i;
            android.widget.RadioButton option = new android.widget.RadioButton(activity);
            option.setId(android.view.View.generateViewId());
            option.setText(HistoryRetention.LABELS[i]); option.setTextSize(16);
            option.setTextColor(activity.getColor(R.color.text_primary));
            option.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{Appearance.primary(activity), activity.getColor(R.color.text_secondary)}));
            option.setMinHeight(Ui.dp(activity, 56));
            option.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
            option.setBackground(Ui.shape(activity, HistoryRetention.HOURS[i] == hours ? R.color.action_green_soft : R.color.card, 12));
            options.addView(option); option.setChecked(HistoryRetention.HOURS[i] == hours);
            option.setOnClickListener(view -> {
                page.dismiss();
                if (HistoryRetention.HOURS[index] == hours) return;
                String owner = Backend.uid();
                Backend.call("setDefaultTimer", Backend.data("hours", HistoryRetention.HOURS[index]))
                        .addOnCompleteListener(activity, result -> {
                            if (!owner.equals(Backend.uid())) return;
                            if (result.isSuccessful()) { HistoryRetention.select(activity.getApplicationContext(), index); saved.run(); }
                            else android.widget.Toast.makeText(activity, Backend.error(activity, result.getException()), android.widget.Toast.LENGTH_LONG).show();
                        });
            });
        }
        selection.addView(options);
        android.widget.LinearLayout info = SettingsPage.panel(activity); content.addView(info, 0);
        info.addView(Ui.text(activity, activity.getString(R.string.settings_how_it_works), 14, true)); Ui.gap(info, 8);
        info.addView(Ui.text(activity, activity.getString(R.string.retention_info), 13, false));
        scroll.addView(content);
        page.body.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, -1)); page.show();
    }
    private MessageTimerDialog() { }
}
