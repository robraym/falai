package br.com.falai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
import java.util.Set;

final class NudgeFeedback {
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
