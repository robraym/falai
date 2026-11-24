package br.com.falai;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

public final class FalaiApplication extends Application {
    static FalaiApplication instance;
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (FirebaseApp.initializeApp(this) != null) {
            AppCheckInstaller.install();
            FirebaseFirestore.getInstance().setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                            .setSizeBytes(100L * 1024 * 1024).build()).build());
            PresenceTracker.install(this);
        }
        FalaiMessagingService.createChannel(this);
    }
}
