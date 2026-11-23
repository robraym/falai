package br.com.falai;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared message expiration and the account's default for new conversations. */
final class HistoryRetention {
    static final int[] HOURS = {0, 24, 168, 2160};
    static final int[] LABELS = {R.string.retention_off, R.string.retention_day, R.string.retention_week, R.string.retention_quarter};
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("message_defaults", Context.MODE_PRIVATE); }
    private static String key(String field) { return Backend.uid() + ":" + field; }
    static int selected(Context context) {
        int hours = prefs(context).getInt(key("hours"), 0);
        for (int i = 0; i < HOURS.length; i++) if (HOURS[i] == hours) return i;
        return 0;
    }
    static long cutoff(Context context) {
        if (!Backend.ready() || Backend.uid().isEmpty()) return 0;
        // Freeze any previous local cleanup; shared timers must never advance it.
        return context.getSharedPreferences("history_retention", Context.MODE_PRIVATE).getLong(key("cutoff"), 0);
    }
    static void select(Context context, int index) {
        prefs(context).edit().putInt(key("hours"), HOURS[index]).apply();
    }
    static void saveDefault(Context context, long hours) {
        for (int i = 0; i < HOURS.length; i++) if (HOURS[i] == hours) { select(context, i); return; }
    }
    static boolean expired(com.google.firebase.firestore.DocumentSnapshot doc) {
        com.google.firebase.Timestamp expiry = doc.getTimestamp("expiresAt");
        return expiry != null && expiry.toDate().getTime() <= System.currentTimeMillis();
    }
    static long unread(com.google.firebase.firestore.DocumentSnapshot chat, String uid) {
        Number count = (Number) chat.get("unreadCounts." + uid); if (count == null) return -1;
        long pending = count.longValue(); Object raw = chat.get("unreadExpiry." + uid);
        if (raw instanceof java.util.Map) for (Object entry : ((java.util.Map<?, ?>) raw).entrySet()) {
            java.util.Map.Entry<?, ?> bucket = (java.util.Map.Entry<?, ?>) entry;
            try { if (Long.parseLong(bucket.getKey().toString()) <= System.currentTimeMillis() && bucket.getValue() instanceof Number) pending -= ((Number) bucket.getValue()).longValue(); }
            catch (NumberFormatException ignored) { }
        }
        return Math.max(0, pending);
    }
    static void removeExpiredNotifications(Context context) {
        long cutoff = cutoff(context);
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        if (manager == null) return;
        for (android.service.notification.StatusBarNotification notice : manager.getActiveNotifications()) {
            android.app.Notification notification = notice.getNotification();
            long created = notification.extras.getLong("falai_createdAt", notification.when);
            long expires = notification.extras.getLong("falai_expiresAt", 0);
            if (notice.getId() == 0 && ((created > 0 && created <= cutoff) || (expires > 0 && expires <= System.currentTimeMillis()))) manager.cancel(notice.getTag(), notice.getId());
        }
    }
    private HistoryRetention() { }
}
