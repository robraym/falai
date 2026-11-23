package br.com.falai;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import java.util.HashSet;
import java.util.Set;

final class NudgeFeedback {
    static final long[] VIBRATION = {0, 300, 100, 320, 110, 375};
    private static final int[] AMPLITUDES = {0, 255, 0, 255, 0, 255};

    @androidx.annotation.RequiresApi(26)
    static VibrationEffect vibrationEffect() {
        return VibrationEffect.createWaveform(VIBRATION, AMPLITUDES, -1);
    }

    static void vibrate(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
        if (Build.VERSION.SDK_INT >= 26) {
            VibrationEffect effect = vibrator.hasAmplitudeControl() ? vibrationEffect()
                    : VibrationEffect.createWaveform(VIBRATION, -1);
            vibrator.vibrate(effect, attributes);
        } else {
            vibrator.vibrate(VIBRATION, -1, attributes);
        }
    }

    static synchronized boolean accept(Context context, String id, String expiresAt) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) return false;
        try {
            long expiry = Long.parseLong(expiresAt), now = System.currentTimeMillis();
            if (expiry <= now || expiry > now + 90000) return false;
        } catch (RuntimeException error) { return false; }
        android.content.SharedPreferences prefs = context.getSharedPreferences("nudges", Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(prefs.getStringSet("seen", java.util.Collections.emptySet()));
        if (!seen.add(id)) return false;
        if (seen.size() > 100) { seen.clear(); seen.add(id); }
        prefs.edit().putStringSet("seen", seen).apply(); return true;
    }
    static void shake(String sender) {
        new Handler(Looper.getMainLooper()).post(() -> FalaiActivity.showNudge(sender));
    }
    private NudgeFeedback() { }
}
