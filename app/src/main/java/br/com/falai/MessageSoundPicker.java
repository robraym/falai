package br.com.falai;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.List;

/** Alertou-style selector: custom files, two-column built-ins, and a preview volume bar. */
final class MessageSoundPicker implements DefaultLifecycleObserver {
    private final MainActivity activity;
    private final ActivityResultLauncher<String[]> picker;
    private Dialog dialog;
    private LinearLayout options;
    private MediaPlayer preview;
    private float volume;
    private String previewKey = MessageSounds.DEFAULT;
    private boolean destroyed, importing;
    private Runnable changed = () -> { };
    MessageSoundPicker(MainActivity activity) {
        this.activity = activity;
        // Constructed before Activity.attach(): read preferences only in show(), after context attachment.
        picker = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || importing) return;
            importing = true;
            Toast.makeText(activity, R.string.sound_importing, Toast.LENGTH_SHORT).show();
            android.content.Context app = activity.getApplicationContext();
            Tasks.call(ContactsRepository.IO, () -> MessageSounds.importAudio(app, uri)).addOnCompleteListener(task -> {
                importing = false;
                if (destroyed || activity.isFinishing()) return;
                if (!task.isSuccessful()) Toast.makeText(activity, R.string.sound_import_error, Toast.LENGTH_LONG).show();
                changed.run();
                if (dialog == null) show(changed); else renderOptions();
                if (task.isSuccessful() && activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) play(task.getResult());
            });
        });
        activity.getLifecycle().addObserver(this);
    }
    void show(Runnable onChanged) {
        changed = onChanged;
        volume = MessageSounds.volume(activity);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        SettingsPage next = new SettingsPage(activity, R.string.message_sounds); dialog = next;
        LinearLayout content = next.body;
        Button add = Ui.button(activity, R.string.sound_add, view -> {
            if (importing) return;
            stopPreview(); picker.launch(new String[]{"audio/*"});
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, -2); addParams.setMargins(dp(8), dp(6), dp(8), dp(6));
        content.addView(add, addParams);
        ScrollView scroll = new ScrollView(activity); options = Ui.column(activity); options.setPadding(0, 0, 0, dp(6)); scroll.addView(options);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); renderOptions();
        LinearLayout volumeCard = Ui.section(activity);
        volumeCard.setPadding(dp(16), dp(10), dp(16), dp(10));
        volumeCard.addView(Ui.text(activity, activity.getString(R.string.sound_preview_volume), 13, true));
        LinearLayout bar = Ui.row(activity);
        ImageView speaker = new ImageView(activity); speaker.setImageResource(R.drawable.ic_volume_preview); speaker.setImageTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity)));
        speaker.setPadding(dp(10), dp(10), dp(10), dp(10)); speaker.setContentDescription(activity.getString(R.string.sound_listen));
        speaker.setOnClickListener(view -> play(MessageSounds.selected(activity))); speaker.setFocusable(true);
        bar.addView(speaker, new LinearLayout.LayoutParams(dp(48), dp(48)));
        SeekBar slider = new SeekBar(activity); slider.setMax(100); slider.setProgress(Math.round(volume * 100)); slider.setContentDescription(activity.getString(R.string.sound_preview_volume));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity))); slider.setThumbTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                volume = progress / 100f;
                if (fromUser) MessageSounds.saveVolume(activity, progress);
                if (fromUser && (preview == null || !previewKey.equals(MessageSounds.selected(activity)))) {
                    play(MessageSounds.selected(activity)); return;
                }
                if (preview != null) { float level = volume * ("icq_soft".equals(previewKey) ? .55f : 1f); preview.setVolume(level, level); }
            }
            public void onStartTrackingTouch(SeekBar seek) { }
            public void onStopTrackingTouch(SeekBar seek) { play(MessageSounds.selected(activity)); }
        }); bar.addView(slider, new LinearLayout.LayoutParams(0, -2, 1));
        volumeCard.addView(bar); content.addView(volumeCard);
        next.setOnDismissListener(ignored -> { stopPreview(); if (dialog == next) dialog = null; });
        next.show();
    }
    private void renderOptions() {
        options.removeAllViews(); List<MessageSounds.Custom> customs = MessageSounds.custom(activity);
        if (!customs.isEmpty()) {
            LinearLayout card = Ui.section(activity); card.addView(section(R.string.sound_custom_section));
            for (int i = 0; i < customs.size(); i++) { if (i > 0) divider(card); card.addView(option(customs.get(i).key, true)); }
            options.addView(card);
        }
        group(R.string.sound_icq_section, java.util.Arrays.copyOfRange(MessageSounds.KEYS, 0, MessageSounds.ICQ_COUNT));
        group(R.string.sound_short_section, java.util.Arrays.copyOfRange(MessageSounds.KEYS, MessageSounds.ICQ_COUNT, MessageSounds.KEYS.length));
    }
    private void group(int title, String[] keys) {
        LinearLayout card = Ui.section(activity); card.addView(section(title));
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        int columns = SettingsPage.optionColumns(activity);
        GridLayout grid = new GridLayout(activity); grid.setColumnCount(columns); grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        for (int i = 0; i < keys.length; i++) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = -2;
            params.columnSpec = GridLayout.spec(i % columns, 1, 1f); params.rowSpec = GridLayout.spec(i / columns, GridLayout.FILL);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(option(keys[i], false), params);
        }
        card.addView(grid);
        options.addView(card);
    }
    private void divider(LinearLayout card) {
        View line = new View(activity);
        line.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(activity.getColor(R.color.text_secondary), 35));
        card.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private LinearLayout option(String key, boolean custom) {
        boolean selected = key.equals(MessageSounds.selected(activity));
        LinearLayout row = Ui.row(activity); row.setMinimumHeight(dp(48)); row.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(activity)), selected ? Ui.shape(activity, R.color.action_green_soft, 16) : null, Ui.shape(activity, R.color.card, 16)));
        FrameLayout radio = new FrameLayout(activity);
        View ring = new View(activity); GradientDrawable outline = new GradientDrawable(); outline.setShape(GradientDrawable.OVAL); outline.setColor(Color.TRANSPARENT); outline.setStroke(dp(2), selected ? Appearance.primary(activity) : activity.getColor(R.color.text_secondary)); ring.setBackground(outline);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL | Gravity.START); radio.addView(ring, ringParams);
        View dot = new View(activity); GradientDrawable fill = new GradientDrawable(); fill.setShape(GradientDrawable.OVAL); fill.setColor(Appearance.primary(activity)); dot.setBackground(fill); dot.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER_VERTICAL | Gravity.START); dotParams.leftMargin = dp(6); radio.addView(dot, dotParams);
        radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); row.addView(radio, new LinearLayout.LayoutParams(dp(28), dp(32)));
        TextView label = Ui.text(activity, MessageSounds.label(activity, key), 14, selected); label.setTextColor(activity.getColor(R.color.text_primary)); label.setMaxLines(2); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.setSelected(selected); row.setFocusable(true); row.setContentDescription(MessageSounds.label(activity, key) + (selected ? activity.getString(R.string.sound_selected) : ""));
        row.setOnClickListener(view -> { MessageSounds.select(activity, key); renderOptions(); changed.run(); play(key); });
        if (custom) {
            TextView remove = action("—", R.string.sound_remove); remove.setTextSize(24); remove.setTextColor(activity.getColor(R.color.sound_remove_color));
            remove.setOnClickListener(view -> new AlertDialog.Builder(activity).setMessage(activity.getString(R.string.sound_remove_question, MessageSounds.label(activity, key)))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.sound_remove, (confirm, which) -> {
                    stopPreview();
                    try { MessageSounds.remove(activity, key); renderOptions(); changed.run(); }
                    catch (Exception error) { Toast.makeText(activity, R.string.sound_remove_error, Toast.LENGTH_LONG).show(); }
                }).show()); row.addView(remove);
        }
        return row;
    }
    private LinearLayout section(int title) {
        LinearLayout row = Ui.row(activity); row.setPadding(0, dp(8), 0, dp(4)); row.addView(Ui.text(activity, activity.getString(title), 12, true));
        return row;
    }
    private TextView action(String text, int description) {
        TextView view = Ui.text(activity, text, 14, true); view.setGravity(Gravity.CENTER); view.setPadding(dp(12), 0, 0, 0); view.setMinWidth(dp(48)); view.setMinHeight(dp(48)); view.setFocusable(true); view.setContentDescription(activity.getString(description)); return view;
    }
    private void play(String key) {
        stopPreview();
        try {
            previewKey = key; MediaPlayer player = MessageSounds.create(activity, key); preview = player;
            player.setOnCompletionListener(done -> { if (preview == done) stopPreview(); });
            player.setOnErrorListener((failed, what, extra) -> { if (preview == failed) stopPreview(); return true; });
            MessageSounds.start(player, key, volume);
        } catch (Exception error) { stopPreview(); Toast.makeText(activity, R.string.sound_preview_error, Toast.LENGTH_SHORT).show(); }
    }
    private void stopPreview() { if (preview != null) { preview.setOnCompletionListener(null); preview.release(); preview = null; } }
    private int dp(int value) { return Ui.dp(activity, value); }
    @Override public void onStop(LifecycleOwner owner) { stopPreview(); }
    @Override public void onDestroy(LifecycleOwner owner) { destroyed = true; stopPreview(); if (dialog != null) dialog.dismiss(); }
}
