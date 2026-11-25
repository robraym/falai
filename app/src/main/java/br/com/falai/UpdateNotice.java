package br.com.falai;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

final class UpdateNotice {
    static final String TOPIC = "falai-updates-v1";
    private static final String CHANNEL = "app_updates_v1";
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("update_notices", Context.MODE_PRIVATE); }
    static boolean enabled(Context context) { return prefs(context).getBoolean("enabled", false); }
    static synchronized void setEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor settings = prefs(context).edit().putBoolean("enabled", enabled);
        if (enabled) settings.putLong("last_refresh", 0);
        settings.apply();
        if (!enabled) NotificationManagerCompat.from(context).cancel("app-update", 0);
        syncTopic(context).addOnFailureListener(error -> DeviceRegistrationWorker.schedule());
        if (enabled && Backend.ready() && !Backend.uid().isEmpty()) refresh(context);
    }
    static com.google.android.gms.tasks.Task<Void> syncTopic(Context context) {
        Context app = context.getApplicationContext(); boolean wanted = enabled(app);
        com.google.firebase.messaging.FirebaseMessaging messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance();
        return (wanted ? messaging.subscribeToTopic(TOPIC) : messaging.unsubscribeFromTopic(TOPIC)).onSuccessTask(ignored ->
                wanted == enabled(app) ? com.google.android.gms.tasks.Tasks.forResult(null) : syncTopic(app));
    }
    static AppUpdate available(Context context) {
        try {
            JSONObject json = new JSONObject(prefs(context).getString("offer", "{}"));
            Map<String, Object> values = new HashMap<>();
            java.util.Iterator<String> keys = json.keys(); while (keys.hasNext()) { String key = keys.next(); values.put(key, json.get(key)); }
            AppUpdate offer = AppUpdate.fromMetadata(UpdateMetadata.parse(values));
            if (offer != null && offer.isNewerThanInstalled()) return offer;
        } catch (org.json.JSONException ignored) { }
        NotificationManagerCompat.from(context).cancel("app-update", 0); return null;
    }
    static synchronized void accept(Context context, Map<String, ?> values) {
        if (!enabled(context)) return;
        UpdateMetadata metadata = UpdateMetadata.parse(values); if (metadata == null) return;
        AppUpdate current = available(context), offer = AppUpdate.fromMetadata(metadata);
        if (!offer.isNewerThanInstalled() || (current != null && current.versionCode > offer.versionCode)) return;
        prefs(context).edit().putString("offer", new JSONObject(values).toString()).apply();
        notifyAvailable(context, offer);
    }
    static void refresh(Context context) {
        if (!enabled(context)) return;
        Context app = context.getApplicationContext();
        AppUpdate cached = available(app); if (cached != null) notifyAvailable(app, cached);
        SharedPreferences settings = prefs(app); long now = System.currentTimeMillis();
        if (now - settings.getLong("last_refresh", 0) < 15 * 60_000) return;
        settings.edit().putLong("last_refresh", now).apply();
        // Recovery for a missed push or a device first installed after publication; no GitHub API request.
        Backend.call("getAppUpdate", Backend.data()).addOnSuccessListener(result -> {
            Object value = result.get("offer");
            if (value instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, ?> offer = (Map<String, ?>) value;
                accept(app, offer);
            }
        });
    }
    private static synchronized void notifyAvailable(Context context, AppUpdate offer) {
        if (!enabled(context)) return;
        if (prefs(context).getLong("notified", 0) >= offer.versionCode) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat notices = NotificationManagerCompat.from(context); if (!notices.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, context.getString(R.string.app_update), NotificationManager.IMPORTANCE_DEFAULT));
            if (manager.getNotificationChannel(CHANNEL).getImportance() == NotificationManager.IMPORTANCE_NONE) return;
        }
        Intent intent = new Intent(context, MainActivity.class).setAction("br.com.falai.APP_UPDATE")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("appUpdate", true);
        PendingIntent open = PendingIntent.getActivity(context, 1701, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notice = new NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_app_update)
                .setContentTitle(context.getString(R.string.apk_update_notification_title))
                .setContentText(context.getString(R.string.apk_update_notification_body, offer.versionName))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_STATUS);
        notices.notify("app-update", 0, notice.build());
        prefs(context).edit().putLong("notified", offer.versionCode).apply();
    }
    private UpdateNotice() { }
}
