package br.com.falai;

import android.os.Bundle;
import android.widget.LinearLayout;

/** Profile editing opened from Settings. */
public final class ProfileActivity extends FalaiActivity {
    private final ProfileController profile = new ProfileController(this);
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.settings_enter, R.anim.settings_hold);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.settings_hold, R.anim.settings_exit);
        } else overridePendingTransition(R.anim.settings_enter, R.anim.settings_hold);
        if (!Backend.ready() || Backend.uid().isEmpty()) { finish(); return; }
        LinearLayout root = Ui.root(this);
        NavigationSurface.attachCard(root);
        root.setBackground(new SoftGlowBackground(this));
        root.addView(Ui.backHeading(this, getString(R.string.profile), view -> finish()));
        LinearLayout body = Ui.column(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        profile.show(body);
    }
    @Override public void finish() {
        super.finish();
        if (android.os.Build.VERSION.SDK_INT < 34) overridePendingTransition(R.anim.settings_hold, R.anim.settings_exit);
    }
}
