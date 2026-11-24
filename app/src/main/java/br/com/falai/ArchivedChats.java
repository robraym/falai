package br.com.falai;

import android.content.Context;

/** Account-scoped local mirror prevents late pushes from alerting after archiving. */
final class ArchivedChats {
    static boolean contains(Context context, String owner, String chatId) {
        return context.getSharedPreferences("archived_chats", Context.MODE_PRIVATE).getBoolean(owner + ":" + chatId, false);
    }
    static void save(Context context, String owner, String chatId, boolean archived) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("archived_chats", Context.MODE_PRIVATE);
        if (prefs.getBoolean(owner + ":" + chatId, false) != archived) prefs.edit().putBoolean(owner + ":" + chatId, archived).apply();
        if (archived) {
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            if (manager != null) for (android.service.notification.StatusBarNotification notice : manager.getActiveNotifications()) {
                if (notice.getId() == 0 && chatId.equals(notice.getNotification().extras.getString("falai_chatId"))) manager.cancel(notice.getTag(), notice.getId());
            }
        }
    }
    private ArchivedChats() { }
}
