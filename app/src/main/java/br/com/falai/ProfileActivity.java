package br.com.falai;

import android.os.Bundle;
import android.widget.LinearLayout;

/** Profile editing opened from Settings. */
public final class ProfileActivity extends FalaiActivity {
    private final ProfileController profile = new ProfileController(this);
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Backend.ready() || Backend.uid().isEmpty()) { finish(); return; }
        LinearLayout root = Ui.root(this);
        NavigationSurface.attachCard(root);
        root.setBackground(new SoftGlowBackground(this));
        LinearLayout header = Ui.row(this);
        header.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, view -> finish()));
        header.addView(Ui.heading(this, getString(R.string.profile)), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        LinearLayout body = Ui.column(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        profile.show(body);
    }
}
