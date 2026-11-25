package br.com.falai;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import java.util.ArrayList;
import java.util.List;

/** Matches Alertou's swatches, two-column options, live preview and apply-on-close flow. */
final class AppearanceColorPicker {
    static void show(MainActivity activity) {
        int initial = Appearance.selected(activity);
        SettingsPage dialog = new SettingsPage(activity, R.string.app_color);
        LinearLayout content = dialog.body;
        LinearLayout preview = Ui.section(activity); content.addView(preview);
        preview.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
        LinearLayout previewRow = Ui.row(activity); preview.addView(previewRow);
        TextView title = Ui.text(activity, activity.getString(R.string.app_name), 18, true); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        previewRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = Ui.text(activity, activity.getString(R.string.color_preview_action), 13, false); action.setGravity(Gravity.CENTER);
        action.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 6), Ui.dp(activity, 10), Ui.dp(activity, 6));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-2, -2); actionParams.leftMargin = Ui.dp(activity, 8); previewRow.addView(action, actionParams);
        int columns = SettingsPage.optionColumns(activity);
        GridLayout grid = new GridLayout(activity); grid.setColumnCount(columns); grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        LinearLayout palette = Ui.section(activity);
        palette.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 10));
        palette.addView(Ui.text(activity, activity.getString(R.string.settings_choose_color), 13, true)); Ui.gap(palette, 6); palette.addView(grid);
        LinearLayout list = Ui.column(activity); list.addView(palette);
        ScrollView scroll = new ScrollView(activity); scroll.addView(list); scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Ui.dp(activity, 12));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        List<TextView> options = new ArrayList<>();
        Runnable update = () -> {
            int selected = Appearance.selected(activity), primary = Appearance.primary(activity), soft = Appearance.soft(activity);
            title.setTextColor(primary); action.setTextColor(primary);
            action.setBackground(background(soft, Ui.dp(activity, 18)));
            for (int i = 0; i < options.size(); i++) {
                TextView option = options.get(i); boolean active = i == selected;
                option.setSelected(active); option.setTextColor(active ? Appearance.primary(activity, i) : activity.getColor(R.color.text_primary));
                option.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
                option.setContentDescription(activity.getString(Appearance.LABELS[i]) + (active ? activity.getString(R.string.sound_selected) : ""));
                GradientDrawable selectionSurface = background(active ? Appearance.soft(activity, i) : Color.TRANSPARENT, Ui.dp(activity, 12));
                if (active) selectionSurface.setStroke(Ui.dp(activity, 1), Appearance.primary(activity, i));
                option.setBackground(selectionSurface);
            }
        };
        for (int i = 0; i < Appearance.LABELS.length; i++) {
            final int index = i;
            TextView option = Ui.text(activity, activity.getString(Appearance.LABELS[i]), 14, false); option.setGravity(Gravity.CENTER_VERTICAL); option.setMinHeight(Ui.dp(activity, 48)); option.setMaxLines(2);
            option.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 6), Ui.dp(activity, 6), Ui.dp(activity, 6));
            option.setCompoundDrawablePadding(Ui.dp(activity, 6));
            GradientDrawable swatch = new GradientDrawable(); swatch.setShape(GradientDrawable.OVAL); swatch.setColor(Appearance.primary(activity, i)); swatch.setSize(Ui.dp(activity, 18), Ui.dp(activity, 18));
            option.setCompoundDrawablesRelativeWithIntrinsicBounds(swatch, null, null, null);
            option.setFocusable(true); option.setOnClickListener(view -> { Appearance.prefs(activity).edit().putInt("color", index).apply(); update.run(); });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = -2;
            params.columnSpec = GridLayout.spec(i % columns, 1, 1f); params.rowSpec = GridLayout.spec(i / columns, GridLayout.FILL); params.setMargins(Ui.dp(activity, 2), Ui.dp(activity, 2), Ui.dp(activity, 2), Ui.dp(activity, 2));
            grid.addView(option, params); options.add(option);
        }
        DefaultLifecycleObserver observer = new DefaultLifecycleObserver() {
            @Override public void onDestroy(LifecycleOwner owner) { dialog.setOnDismissListener(null); dialog.dismiss(); activity.getLifecycle().removeObserver(this); }
        };
        activity.getLifecycle().addObserver(observer);
        dialog.setOnDismissListener(ignored -> {
            activity.getLifecycle().removeObserver(observer);
            if (initial != Appearance.selected(activity) && !activity.isFinishing() && !activity.isDestroyed()) SettingsPage.recreateAfterExit(activity);
        });
        update.run(); dialog.show();
    }
    private static GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape;
    }
    private AppearanceColorPicker() { }
}
