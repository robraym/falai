package br.com.falai;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

abstract class FalaiActivity extends AppCompatActivity {
    private int color, size;
    private static java.lang.ref.WeakReference<FalaiActivity> foreground = new java.lang.ref.WeakReference<>(null);
    private android.animation.ObjectAnimator nudgeAnimation;
    @Override protected void attachBaseContext(Context base) {
        int percent = Appearance.percent(base);
        if (percent == 100) { super.attachBaseContext(base); return; }
        Configuration config = new Configuration(base.getResources().getConfiguration()); config.fontScale *= percent / 100f;
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    @Override protected void onCreate(Bundle state) {
        color = Appearance.selected(this); size = Appearance.percent(this); setTheme(Appearance.theme(this)); super.onCreate(state);
    }
    @Override protected void onResume() {
        super.onResume(); if (color != Appearance.selected(this) || size != Appearance.percent(this)) recreate();
        foreground = new java.lang.ref.WeakReference<>(this);
    }
    @Override protected void onPause() {
        if (foreground.get() == this) foreground.clear();
        if (nudgeAnimation != null) { nudgeAnimation.cancel(); findViewById(android.R.id.content).setTranslationX(0); nudgeAnimation = null; }
        super.onPause();
    }
    static void showNudge(String sender) {
        FalaiActivity activity = foreground.get();
        if (activity == null || activity.isFinishing() || !activity.hasWindowFocus()) return;
        android.view.View content = activity.findViewById(android.R.id.content);
        if (activity.nudgeAnimation != null) activity.nudgeAnimation.cancel();
        float shift = Ui.dp(activity, 6);
        activity.nudgeAnimation = android.animation.ObjectAnimator.ofFloat(content, "translationX", 0, -shift, shift, -shift * .7f, shift * .7f, -shift * .35f, shift * .35f, 0);
        activity.nudgeAnimation.setDuration(420); activity.nudgeAnimation.start();
        android.widget.Toast.makeText(activity, activity.getString(R.string.nudge_from, sender), android.widget.Toast.LENGTH_SHORT).show();
    }
}
