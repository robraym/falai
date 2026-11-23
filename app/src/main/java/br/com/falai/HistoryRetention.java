package br.com.falai;

import android.content.Context;
import android.content.SharedPreferences;

/** Local account-scoped history visibility, without changing the other participant's history. */
final class HistoryRetention {
    static final int[] HOURS = {0, 24, 168, 2160};
    static final int[] LABELS = {R.string.retention_off, R.string.retention_day, R.string.retention_week, R.string.retention_quarter};
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("history_retention", Context.MODE_PRIVATE); }
    private static String key(String field) { return Backend.uid() + ":" + field; }
    static int selected(Context context) {
        int hours = prefs(context).getInt(key("hours"), 0);
        for (int i = 0; i < HOURS.length; i++) if (HOURS[i] == hours) return i;
        return 0;
    }
    static long cutoff(Context context) {
        if (!Backend.ready() || Backend.uid().isEmpty()) return 0;
        SharedPreferences prefs = prefs(context);
        long previous = prefs.getLong(key("cutoff"), 0);
        int hours = HOURS[selected(context)];
        long next = hours == 0 ? previous : Math.max(previous, System.currentTimeMillis() - hours * 3600000L);
        if (next > previous) prefs.edit().putLong(key("cutoff"), next).apply();
        return next;
    }
    static void select(Context context, int index) {
        // Extending/disabling the duration must not restore history already cleared.
        cutoff(context);
        long revision = Math.max(System.currentTimeMillis(), revision(context) + 1);
        prefs(context).edit().putInt(key("hours"), HOURS[index]).putLong(key("revision"), revision).apply(); removeExpiredNotifications(context);
    }
    static long revision(Context context) { return prefs(context).getLong(key("revision"), 0); }
    static void removeExpiredNotifications(Context context) {
        long cutoff = cutoff(context); if (cutoff == 0) return;
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        if (manager == null) return;
        for (android.service.notification.StatusBarNotification notice : manager.getActiveNotifications()) {
            android.app.Notification notification = notice.getNotification();
            long created = notification.extras.getLong("falai_createdAt", notification.when);
            if (notice.getId() == 0 && created > 0 && created <= cutoff) manager.cancel(notice.getTag(), notice.getId());
        }
    }
    private HistoryRetention() { }
}
