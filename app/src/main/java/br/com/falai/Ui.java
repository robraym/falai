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
import android.widget.ImageView;
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
        GradientDrawable d = new GradientDrawable(); d.setColor(color(c, color)); d.setCornerRadius(dp(c, radius)); return d;
    }
    static int color(Context c, int resource) {
        if (resource == R.color.action_green) return Appearance.primary(c);
        if (resource == R.color.action_green_soft) return Appearance.soft(c);
        return c.getColor(resource);
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static LinearLayout root(ComponentActivity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        LinearLayout root = column(activity); root.setBackground(new ChatWallpaper(activity));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            applySystemInsets(view, insets); return insets;
        });
        // Replacing setContentView during navigation needs a new inset dispatch.
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { ViewCompat.requestApplyInsets(view); }
            @Override public void onViewDetachedFromWindow(View view) { }
        });
        WindowInsetsCompat existing = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
        if (existing != null) applySystemInsets(root, existing);
        activity.setContentView(root);
        ViewCompat.requestApplyInsets(root);
        // Newer Android versions require the window decor to exist first.
        androidx.core.view.WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), root);
        boolean light = activity.getResources().getBoolean(R.bool.light_system_bars);
        controller.setAppearanceLightNavigationBars(light); controller.setAppearanceLightStatusBars(light);
        return root;
    }
    private static void applySystemInsets(View view, WindowInsetsCompat insets) {
        int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
        androidx.core.graphics.Insets bars = insets.getInsetsIgnoringVisibility(types);
        WindowInsetsCompat window = ViewCompat.getRootWindowInsets(view);
        if (window != null) bars = androidx.core.graphics.Insets.max(bars, window.getInsetsIgnoringVisibility(types));
        int bottom = Math.max(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
        int side = view.getResources().getDimensionPixelSize(R.dimen.navigation_side_padding);
        int left = side + bars.left, right = side + bars.right;
        if (view.getPaddingLeft() != left || view.getPaddingTop() != bars.top || view.getPaddingRight() != right || view.getPaddingBottom() != bottom)
            view.setPadding(left, bars.top, right, bottom);
    }
    static TextView text(Context c, CharSequence value, int size, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size);
        t.setTextColor(c.getColor(bold ? R.color.text_primary : R.color.text_secondary));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setLineSpacing(dp(c, 2), 1); return t;
    }
    static LinearLayout heading(Context c, CharSequence title) {
        LinearLayout header = (LinearLayout) android.view.LayoutInflater.from(c).inflate(R.layout.view_page_header, new LinearLayout(c), false);
        TextView label = header.findViewById(R.id.page_title); label.setText(title);
        View box = header.findViewById(R.id.page_title_box); ViewGroup.LayoutParams size = box.getLayoutParams();
        android.graphics.Paint.FontMetricsInt metrics = label.getPaint().getFontMetricsInt();
        size.height = Math.max(dp(c, 40), metrics.bottom - metrics.top + dp(c, 4)); box.setLayoutParams(size);
        return header;
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
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Appearance.ripple(c)), shape(c, R.color.action_green_soft, 14), null));
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 12); b.setLayoutParams(lp); b.setOnClickListener(listener); return b;
    }
    static ImageButton icon(Context c, int drawable, int description, View.OnClickListener click) {
        ImageButton b = new ImageButton(c); b.setImageResource(drawable); b.setImageTintList(ColorStateList.valueOf(Appearance.primary(c)));
        b.setContentDescription(c.getString(description)); b.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Appearance.ripple(c)), shape(c, R.color.action_green_soft, 24), null));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48))); b.setOnClickListener(click); return b;
    }
    static EditText input(Context c, int hint) {
        EditText t = new EditText(c); t.setHint(hint); t.setTextSize(16); t.setSingleLine(true); t.setTextColor(c.getColor(R.color.text_primary)); t.setHintTextColor(c.getColor(R.color.text_secondary));
        t.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12)); t.setBackground(shape(c, R.color.input_background, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 12); t.setLayoutParams(lp); return t;
    }
    static LinearLayout contactRow(Context c, String name, String detail) {
        return contactRow(c, name, detail, "");
    }
    static LinearLayout contactRow(Context c, String name, String detail, String dateTime) {
        return contactRow(c, name, detail, dateTime, "");
    }
    static LinearLayout contactRow(Context c, String name, String detail, String dateTime, String uid) {
        return contactRow(c, name, detail, dateTime, uid, 0);
    }
    static LinearLayout contactRow(Context c, String name, String detail, String dateTime, String uid, int status) {
        return contactRow(c, name, detail, dateTime, uid, status, false);
    }
    static LinearLayout contactRow(Context c, String name, String detail, String dateTime, String uid, int status, boolean showAbout) {
        return contactRow(c, name, detail, dateTime, uid, status, showAbout, "");
    }
    static LinearLayout contactRow(Context c, String name, String detail, String dateTime, String uid, int status, boolean showAbout, String chatId) {
        return contactRow(c, name, detail, dateTime, uid, status, showAbout, chatId, 0);
    }
    static LinearLayout contactRow(Context c, String name, String detail, String dateTime, String uid, int status, boolean showAbout, String chatId, int unread) {
        LinearLayout row = row(c); row.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10)); row.setMinimumHeight(dp(c, 72));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(Appearance.ripple(c)), new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT), new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        AvatarView circle = new AvatarView(c); circle.load(uid, name);
        if (showAbout) {
            circle.setContentDescription(c.getString(R.string.profile) + ": " + name);
            circle.setFocusable(true);
            circle.setOnClickListener(v -> ContactProfileDialog.show(c, uid, name));
        }
        row.addView(circle, new LinearLayout.LayoutParams(dp(c, 46), dp(c, 46)));
        LinearLayout labels = column(c); labels.setPadding(dp(c, 12), 0, dp(c, 2), 0);
        LinearLayout headline = row(c);
        TextView title = text(c, name, 16, true); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        boolean hasPresence = showAbout || !chatId.isEmpty();
        headline.addView(title, new LinearLayout.LayoutParams(hasPresence ? -2 : 0, -2, 1));
        if (hasPresence) {
            PresenceStatusView availability = new PresenceStatusView(c, uid);
            availability.setMaxWidth(Math.min(dp(c, 140), Math.round(c.getResources().getDisplayMetrics().widthPixels * .4f)));
            LinearLayout.LayoutParams availabilityParams = new LinearLayout.LayoutParams(-2, -2);
            availabilityParams.leftMargin = dp(c, 8);
            headline.addView(availability, availabilityParams);
        }
        labels.addView(headline, new LinearLayout.LayoutParams(hasPresence ? -2 : -1, -2));
        TextView sub;
        if (showAbout) {
            AboutTextView phrase = new AboutTextView(c); phrase.setTextSize(13); phrase.setTextColor(c.getColor(R.color.text_secondary)); phrase.setLineSpacing(dp(c, 2), 1); phrase.bind(uid, detail); sub = phrase;
        } else if (!chatId.isEmpty()) sub = new TypingPreviewView(c, chatId, uid, detail);
        else sub = text(c, detail, 13, false);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (unread != 0) sub.setTypeface(null, android.graphics.Typeface.BOLD);
        if (!dateTime.isEmpty()) {
            LinearLayout preview = row(c); preview.setBaselineAligned(true);
            if (status > 0) {
                MessageChecks checks = new MessageChecks(c, status >= 2, status == 3);
                LinearLayout.LayoutParams checksParams = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 18)); checksParams.rightMargin = dp(c, 3); preview.addView(checks, checksParams);
                if (sub instanceof TypingPreviewView) ((TypingPreviewView) sub).setChecks(checks);
            }
            sub.setSingleLine(true); preview.addView(sub, new LinearLayout.LayoutParams(0, -2, 1));
            TextView when = text(c, dateTime, 11, false); when.setSingleLine(true); when.setGravity(Gravity.END);
            when.setPadding(dp(c, 8), 0, 0, 0); preview.addView(when, new LinearLayout.LayoutParams(-2, -2));
            if (unread != 0) when.setTextColor(Appearance.primary(c));
            labels.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        } else { sub.setMaxLines(2); labels.addView(sub); }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (unread != 0) {
            TextView badge = text(c, unread < 0 ? "•" : unread > 99 ? "99+" : Integer.toString(unread), 11, true);
            badge.setGravity(Gravity.CENTER); badge.setIncludeFontPadding(false);
            badge.setMinWidth(dp(c, 22)); badge.setMinHeight(dp(c, 22));
            badge.setPadding(dp(c, 5), dp(c, 2), dp(c, 5), dp(c, 2));
            badge.setTextColor(c.getColor(R.color.background)); badge.setBackground(shape(c, R.color.action_green, 11));
            badge.setContentDescription(unread < 0 ? c.getString(R.string.unread_messages) : c.getResources().getQuantityString(R.plurals.unread_message_count, unread, unread));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2); badgeParams.leftMargin = dp(c, 6);
            row.addView(badge, badgeParams);
        }
        return row;
    }
    static LinearLayout optionRow(Context c, int icon, String title, String summary, boolean section, View.OnClickListener action) {
        LinearLayout row = row(c); row.setBaselineAligned(false); row.setMinimumHeight(dp(c, 72)); row.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10));
        ImageView image = new ImageView(c); image.setImageResource(icon); image.setImageTintList(ColorStateList.valueOf(Appearance.primary(c)));
        image.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8)); image.setBackground(shape(c, R.color.action_green_soft, 24));
        row.addView(image, new LinearLayout.LayoutParams(dp(c, 46), dp(c, 46)));
        LinearLayout labels = column(c); labels.setPadding(dp(c, 12), 0, dp(c, 4), 0);
        TextView heading = text(c, title, 16, false); heading.setMaxLines(2); labels.addView(heading);
        TextView detail = text(c, summary, 13, false); detail.setMaxLines(2); detail.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            ImageView arrow = new ImageView(c); arrow.setImageResource(R.drawable.ic_chevron_right); arrow.setImageTintList(ColorStateList.valueOf(c.getColor(R.color.text_secondary)));
            arrow.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8)); row.addView(arrow, new LinearLayout.LayoutParams(dp(c, 34), dp(c, 34)));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(Appearance.ripple(c)), new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT), new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
            row.setClickable(true); row.setFocusable(true); row.setOnClickListener(action);
        }
        return row;
    }
    static void enable(View v, boolean enabled) {
        v.setEnabled(enabled); if (v instanceof ViewGroup) { ViewGroup group = (ViewGroup) v; for (int i = 0; i < group.getChildCount(); i++) enable(group.getChildAt(i), enabled); }
    }
}
