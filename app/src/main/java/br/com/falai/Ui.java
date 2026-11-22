package br.com.falai;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

final class Ui {
    private Ui() { }
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(c.getColor(color)); d.setCornerRadius(dp(c, radius)); return d;
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static LinearLayout root(ComponentActivity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        LinearLayout root = column(activity); root.setBackgroundColor(activity.getColor(R.color.background));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int bottom = Math.max(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            int side = activity.getResources().getDimensionPixelSize(R.dimen.navigation_side_padding);
            view.setPadding(side + bars.left, bars.top, side + bars.right, bottom); return insets;
        });
        activity.setContentView(root);
        // Newer Android versions require the window decor to exist first.
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightNavigationBars(false);
        return root;
    }
    static TextView text(Context c, CharSequence value, int size, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size);
        t.setTextColor(c.getColor(bold ? R.color.text_primary : R.color.text_secondary));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setLineSpacing(dp(c, 2), 1); return t;
    }
    static TextView heading(Context c, CharSequence title) {
        TextView v = text(c, title, 27, true); v.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 6)); return v;
    }
    static LinearLayout card(Context c) {
        LinearLayout v = column(c); v.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 18)); v.setBackground(shape(c, R.color.card, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        int side = c.getResources().getDimensionPixelSize(R.dimen.screen_card_inset);
        lp.setMargins(side, c.getResources().getDimensionPixelSize(R.dimen.screen_header_content_gap), side, c.getResources().getDimensionPixelSize(R.dimen.screen_card_gap));
        v.setLayoutParams(lp); return v;
    }
    static void gap(LinearLayout parent, int height) { parent.addView(new View(parent.getContext()), new LinearLayout.LayoutParams(1, dp(parent.getContext(), height))); }
    static Button button(Context c, int label, View.OnClickListener listener) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(c.getColor(R.color.text_primary)); b.setMinHeight(dp(c, 48));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3345C98A), shape(c, R.color.action_green_soft, 14), null));
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 12); b.setLayoutParams(lp); b.setOnClickListener(listener); return b;
    }
    static ImageButton icon(Context c, int drawable, int description, View.OnClickListener click) {
        ImageButton b = new ImageButton(c); b.setImageResource(drawable); b.setImageTintList(ColorStateList.valueOf(c.getColor(R.color.action_green)));
        b.setContentDescription(c.getString(description)); b.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3345C98A), shape(c, R.color.action_green_soft, 24), null));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48))); b.setOnClickListener(click); return b;
    }
    static EditText input(Context c, int hint) {
        EditText t = new EditText(c); t.setHint(hint); t.setTextSize(16); t.setSingleLine(true); t.setTextColor(c.getColor(R.color.text_primary)); t.setHintTextColor(c.getColor(R.color.text_secondary));
        t.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12)); t.setBackground(shape(c, R.color.input_background, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 12); t.setLayoutParams(lp); return t;
    }
    static View divider(Context c) { View v = new View(c); v.setBackgroundColor(c.getColor(R.color.divider)); v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, 1))); return v; }
    static LinearLayout contactRow(Context c, String name, String detail) {
        LinearLayout row = row(c); row.setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14)); row.setMinimumHeight(dp(c, 80));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3345C98A), shape(c, R.color.card, 18), null));
        TextView circle = text(c, name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT), 19, true);
        circle.setGravity(Gravity.CENTER); circle.setTextColor(c.getColor(R.color.action_green)); circle.setBackground(shape(c, R.color.action_green_soft, 24));
        row.addView(circle, new LinearLayout.LayoutParams(dp(c, 46), dp(c, 46)));
        LinearLayout labels = column(c); labels.setPadding(dp(c, 14), 0, dp(c, 6), 0);
        TextView title = text(c, name, 16, true); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(title);
        TextView sub = text(c, detail, 13, false); sub.setMaxLines(2); sub.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); return row;
    }
    static void enable(View v, boolean enabled) {
        v.setEnabled(enabled); if (v instanceof ViewGroup) { ViewGroup group = (ViewGroup) v; for (int i = 0; i < group.getChildCount(); i++) enable(group.getChildAt(i), enabled); }
    }
}
