package br.com.falai;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class FalaiMessagingService extends FirebaseMessagingService {
    static final String CHANNEL_ID = "messages_alerts_v2";
    private static final String LEGACY_CHANNEL_ID = "messages_icq_v1";
    private static final long[] VIBRATION = {0, 180, 100, 180};
    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            NotificationChannel previous = manager.getNotificationChannel(LEGACY_CHANNEL_ID);
            int importance = previous == null ? NotificationManager.IMPORTANCE_HIGH : previous.getImportance();
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.message_channel), importance);
            // Keep custom audio separate, but publish an actual haptic alert for wearable bridging.
            // A new channel is necessary: Android does not update an existing channel's behavior.
            channel.setSound(null, null);
            channel.enableVibration(true); channel.setVibrationPattern(VIBRATION);
            manager.createNotificationChannel(channel);
        }
    }
    @Override public void onNewToken(@NonNull String token) {
        DeviceRegistrationWorker.schedule();
    }
    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        if (!Backend.ready() || Backend.uid().isEmpty()) return;
        String chatId = message.getData().get("chatId"), recipient = message.getData().get("recipientId");
        String messageId = message.getData().get("messageId");
        if (chatId == null || !chatId.matches("[a-f0-9]{64}") || !Backend.uid().equals(recipient)) return;
        if ("deleted".equals(message.getData().get("event"))) {
            if (messageId != null && messageId.matches("[a-f0-9-]{36}")) {
                android.content.SharedPreferences deleted = getSharedPreferences("deleted_notices", MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = deleted.edit(); if (deleted.getAll().size() >= 200) editor.clear();
                editor.putBoolean(messageId, true).apply(); NotificationManagerCompat.from(this).cancel(messageId, 0);
            }
            return;
        }
        if (messageId != null && getSharedPreferences("deleted_notices", MODE_PRIVATE).getBoolean(messageId, false)) return;
        if (messageId != null && messageId.matches("[a-f0-9-]{36}"))
            ReceiptWorker.schedule(chatId, java.util.Collections.singletonList(messageId), false);
        if (chatId.equals(ChatActivity.visibleChat)) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createChannel(this);
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 26 && getSystemService(NotificationManager.class)
                .getNotificationChannel(CHANNEL_ID).getImportance() == NotificationManager.IMPORTANCE_NONE) return;
        Intent intent = new Intent(this, MainActivity.class).setAction("br.com.falai.OPEN." + chatId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("chatId", chatId).putExtra("recipientId", recipient);
        PendingIntent pending = PendingIntent.getActivity(this, chatId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String sender = message.getData().get("senderName"), text = message.getData().get("text");
        if (sender == null || sender.trim().isEmpty()) sender = getString(R.string.app_name);
        if (text == null || text.isEmpty()) text = getString(R.string.new_message);
        String me = Backend.auth().getCurrentUser().getDisplayName();
        if (me == null || me.trim().isEmpty()) me = getString(R.string.app_name);
        Person recipientPerson = new Person.Builder().setName(me).setKey(recipient).build();
        Person senderPerson = new Person.Builder().setName(sender).setKey(chatId).build();
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(recipientPerson)
                .setGroupConversation(false).addMessage(text, message.getSentTime() > 0 ? message.getSentTime() : System.currentTimeMillis(), senderPerson);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_chat).setContentTitle(sender).setContentText(text)
                .setStyle(style).setCategory(NotificationCompat.CATEGORY_MESSAGE).addPerson(senderPerson)
                .setVibrate(VIBRATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        String tag = messageId != null && messageId.matches("[a-f0-9-]{36}") ? messageId : chatId;
        NotificationManagerCompat.from(this).notify(tag, 0, notification.build());
        MessageSound.play(this, recipient + ":" + tag);
    }
}
