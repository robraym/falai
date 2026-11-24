package br.com.falai;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class EmojiPicker {
    static void show(Context context, Consumer<String> selected) {
        show(context, false, selected);
    }
    static void show(Context context, boolean animated, Consumer<String> selected) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("emoji_picker", Context.MODE_PRIVATE);
        String key = "recent_" + Backend.uid();
        List<String> recent = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < Math.min(24, stored.length()); i++) {
                String value = stored.optString(i); if (!value.isEmpty() && !recent.contains(value)) recent.add(value);
            }
        } catch (org.json.JSONException ignored) { }
        LinearLayout container = Ui.column(context), body = Ui.column(context);
        int height = Math.max(96, Math.min(520, Math.round(context.getResources().getConfiguration().screenHeightDp * .65f)));
        container.addView(body, new LinearLayout.LayoutParams(-1, Ui.dp(context, height)));
        body.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.emoji).setView(container).setNegativeButton(R.string.cancel, null).create();
        Consumer<String> pick = value -> {
            recent.remove(value); recent.add(0, value);
            if (recent.size() > 24) recent.subList(24, recent.size()).clear();
            prefs.edit().putString(key, new JSONArray(recent).toString()).apply();
            selected.accept(value); dialog.dismiss();
        };
        body.addView(heading(context, context.getString(R.string.emoji_recent)));
        if (recent.isEmpty()) body.addView(Ui.text(context, context.getString(R.string.emoji_recent_empty), 12, false));
        else {
            HorizontalScrollView strip = new HorizontalScrollView(context); strip.setHorizontalScrollBarEnabled(false);
            LinearLayout line = Ui.row(context);
            for (String value : recent) line.addView(option(context, value, pick), new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 52)));
            strip.addView(line); body.addView(strip, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView scroll = new ScrollView(context); LinearLayout categories = Ui.column(context); scroll.addView(categories);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        int cell = Math.max(48, Math.round(36 * context.getResources().getConfiguration().fontScale));
        int columns = Math.max(3, Math.min(8, (context.getResources().getConfiguration().screenWidthDp - 72) / cell));
        if (animated) {
            categories.addView(heading(context, context.getString(R.string.emoji_animated)));
            categories.addView(Ui.text(context, context.getString(R.string.emoji_animated_hint), 12, false));
            for (int offset = 0; offset < AnimatedEmoji.VALUES.size(); offset += columns) {
                LinearLayout line = Ui.row(context); categories.addView(line);
                for (int col = 0; col < columns; col++) {
                    int index = offset + col;
                    View option = new View(context);
                    if (index < AnimatedEmoji.VALUES.size()) {
                        String emoji = AnimatedEmoji.VALUES.get(index);
                        option = new AnimatedEmojiView(context, emoji); option.setFocusable(true);
                        option.setOnClickListener(view -> pick.accept(emoji));
                    }
                    line.addView(option, new LinearLayout.LayoutParams(0, Ui.dp(context, 60), 1));
                }
            }
        }
        String[] labels = context.getResources().getStringArray(R.array.emoji_categories);
        String[] groups = context.getResources().getStringArray(R.array.emoji_groups);
        for (int category = 0; category < groups.length; category++) {
            categories.addView(heading(context, labels[category])); String[] values = groups[category].split(" ");
            for (int offset = 0; offset < values.length; offset += columns) {
                LinearLayout line = Ui.row(context); categories.addView(line);
                for (int col = 0; col < columns; col++) {
                    int index = offset + col;
                    line.addView(index < values.length ? option(context, values[index], pick) : new View(context), new LinearLayout.LayoutParams(0, Ui.dp(context, 52), 1));
                }
            }
        }
        dialog.show();
    }
    private static TextView heading(Context context, String title) {
        TextView label = Ui.text(context, title, 13, true); label.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 6)); return label;
    }
    private static TextView option(Context context, String value, Consumer<String> pick) {
        TextView option = Ui.text(context, value, 26, false); option.setGravity(Gravity.CENTER); option.setContentDescription(value); option.setFocusable(true);
        option.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(context)), null, new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        option.setOnClickListener(view -> pick.accept(value)); return option;
    }
}
