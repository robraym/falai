package br.com.falai;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

abstract class FalaiActivity extends AppCompatActivity {
    private int color, size;
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
    }
}
