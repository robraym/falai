package br.com.falai;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;

/** Foreground app presence, with independent sessions for multiple installations. */
final class PresenceTracker implements Application.ActivityLifecycleCallbacks {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int resumed;
    private DocumentReference session;
    private boolean published;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() { publish(); if (resumed > 0) handler.postDelayed(this, 30000); }
    };
    static void install(Application app) {
        PresenceTracker tracker = new PresenceTracker(); app.registerActivityLifecycleCallbacks(tracker);
        Backend.auth().addAuthStateListener(auth -> {
            tracker.stop();
            if (auth.getCurrentUser() != null && tracker.resumed > 0) tracker.start();
        });
    }
    private void start() {
        handler.removeCallbacks(heartbeat);
        if (!Backend.uid().isEmpty()) {
            session = Backend.db().collection("presence").document(Backend.uid()).collection("sessions").document(Backend.deviceId());
            heartbeat.run();
        }
    }
    private void publish() {
        Application app = FalaiApplication.instance;
        PowerManager power = (PowerManager) app.getSystemService(Application.POWER_SERVICE);
        KeyguardManager lock = (KeyguardManager) app.getSystemService(Application.KEYGUARD_SERVICE);
        boolean active = resumed > 0 && power.isInteractive() && !lock.isKeyguardLocked();
        if (session == null) return;
        if (active || published) session.set(Backend.data("active", active, "updatedAt", FieldValue.serverTimestamp()));
        published = active;
    }
    private void stop() {
        handler.removeCallbacks(heartbeat);
        if (session != null && published) session.set(Backend.data("active", false, "updatedAt", FieldValue.serverTimestamp()));
        session = null; published = false;
    }
    private final Runnable background = () -> { if (resumed == 0) stop(); };
    @Override public void onActivityResumed(Activity activity) { resumed++; handler.removeCallbacks(background); start(); }
    @Override public void onActivityPaused(Activity activity) { resumed = Math.max(0, resumed - 1); handler.postDelayed(background, 500); }
    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
