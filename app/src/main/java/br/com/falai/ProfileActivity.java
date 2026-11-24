package br.com.falai;

import android.os.Bundle;
import android.widget.LinearLayout;

/** Compatibility entry point; main navigation hosts the same profile content. */
public final class ProfileActivity extends FalaiActivity {
    private final ProfileController profile = new ProfileController(this);
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Backend.ready() || Backend.uid().isEmpty()) { finish(); return; }
        LinearLayout root = Ui.root(this);
        root.addView(Ui.heading(this, getString(R.string.profile)));
        LinearLayout body = Ui.column(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        profile.show(body);
    }
}
