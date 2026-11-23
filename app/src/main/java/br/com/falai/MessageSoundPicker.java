package br.com.falai;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
    private float volume = .72f;
    private String previewKey = MessageSounds.DEFAULT;
    private boolean destroyed, importing;
    private Runnable changed = () -> { };
    MessageSoundPicker(MainActivity activity) {
        this.activity = activity;
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
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        Dialog next = new Dialog(activity); dialog = next;
        LinearLayout content = Ui.column(activity); content.setPadding(dp(20), dp(18), dp(20), dp(12)); content.setBackground(Ui.shape(activity, R.color.card, 24));
        LinearLayout header = Ui.row(activity);
        header.addView(Ui.text(activity, activity.getString(R.string.message_sounds), 22, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = action("+", R.string.sound_add); add.setTextSize(30); add.setOnClickListener(view -> {
            if (importing) return;
            stopPreview(); picker.launch(new String[]{"audio/*"});
        }); header.addView(add); content.addView(header);
        ScrollView scroll = new ScrollView(activity); options = Ui.column(activity); options.setPadding(0, 0, 0, dp(10)); scroll.addView(options);
        int height = Math.min(dp(500), Math.round(activity.getResources().getDisplayMetrics().heightPixels * .62f));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, height)); renderOptions();
        LinearLayout bar = Ui.row(activity); bar.setPadding(dp(12), dp(9), dp(12), dp(9)); bar.setBackground(Ui.shape(activity, R.color.presence_badge_surface, 24));
        ImageView speaker = new ImageView(activity); speaker.setImageResource(R.drawable.ic_volume_preview); speaker.setImageTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity)));
        speaker.setPadding(0, 0, dp(10), 0); speaker.setContentDescription(activity.getString(R.string.sound_listen));
        speaker.setOnClickListener(view -> play(MessageSounds.selected(activity))); speaker.setFocusable(true);
        bar.addView(speaker, new LinearLayout.LayoutParams(dp(34), dp(28)));
        SeekBar slider = new SeekBar(activity); slider.setMax(100); slider.setProgress(Math.round(volume * 100)); slider.setContentDescription(activity.getString(R.string.sound_preview_volume));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity))); slider.setThumbTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                volume = progress / 100f;
                if (preview != null) { float level = volume * ("icq_soft".equals(previewKey) ? .55f : 1f); preview.setVolume(level, level); }
            }
            public void onStartTrackingTouch(SeekBar seek) { }
            public void onStopTrackingTouch(SeekBar seek) { }
        }); bar.addView(slider, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, -2); barParams.topMargin = dp(8); barParams.bottomMargin = dp(2); content.addView(bar, barParams);
        LinearLayout actions = Ui.row(activity); actions.setGravity(Gravity.END);
        TextView close = action(activity.getString(R.string.sound_close), R.string.sound_close); close.setOnClickListener(view -> next.dismiss()); actions.addView(close); content.addView(actions);
        next.setContentView(content); next.setOnDismissListener(ignored -> { stopPreview(); if (dialog == next) dialog = null; });
        next.show();
        if (next.getWindow() != null) {
            next.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            next.getWindow().setLayout(activity.getResources().getDisplayMetrics().widthPixels - dp(24), -2);
            next.getWindow().setDimAmount(.65f);
        }
    }
    private void renderOptions() {
        options.removeAllViews(); List<MessageSounds.Custom> customs = MessageSounds.custom(activity);
        if (!customs.isEmpty()) {
            options.addView(section(R.string.sound_custom_section));
            for (MessageSounds.Custom sound : customs) options.addView(option(sound.key, true));
        }
        options.addView(section(R.string.sound_icq_section)); grid(java.util.Arrays.copyOfRange(MessageSounds.KEYS, 0, MessageSounds.ICQ_COUNT));
        options.addView(section(R.string.sound_short_section)); grid(java.util.Arrays.copyOfRange(MessageSounds.KEYS, MessageSounds.ICQ_COUNT, MessageSounds.KEYS.length));
    }
    private void grid(String[] keys) {
        GridLayout grid = new GridLayout(activity); grid.setColumnCount(2); grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        for (int i = 0; i < keys.length; i++) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = -2;
            params.columnSpec = GridLayout.spec(i % 2, 1, 1f); params.rowSpec = GridLayout.spec(i / 2); params.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(option(keys[i], false), params);
        }
        options.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }
    private LinearLayout option(String key, boolean custom) {
        boolean selected = key.equals(MessageSounds.selected(activity));
        LinearLayout row = Ui.row(activity); row.setMinimumHeight(dp(44)); row.setPadding(dp(5), dp(4), dp(7), dp(4));
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(activity)), selected ? Ui.shape(activity, R.color.action_green_soft, 16) : null, Ui.shape(activity, R.color.card, 16)));
        FrameLayout radio = new FrameLayout(activity);
        View ring = new View(activity); GradientDrawable outline = new GradientDrawable(); outline.setShape(GradientDrawable.OVAL); outline.setColor(Color.TRANSPARENT); outline.setStroke(dp(2), selected ? Appearance.primary(activity) : activity.getColor(R.color.text_secondary)); ring.setBackground(outline);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL | Gravity.START); radio.addView(ring, ringParams);
        View dot = new View(activity); GradientDrawable fill = new GradientDrawable(); fill.setShape(GradientDrawable.OVAL); fill.setColor(Appearance.primary(activity)); dot.setBackground(fill); dot.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER_VERTICAL | Gravity.START); dotParams.leftMargin = dp(6); radio.addView(dot, dotParams);
        radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); row.addView(radio, new LinearLayout.LayoutParams(dp(32), dp(36)));
        TextView label = Ui.text(activity, MessageSounds.label(activity, key), 14, false); label.setTextColor(activity.getColor(R.color.text_primary)); label.setTypeface(Typeface.DEFAULT, Typeface.NORMAL); label.setMaxLines(custom ? 1 : 2); label.setEllipsize(android.text.TextUtils.TruncateAt.END); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
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
            previewKey = key; MediaPlayer player = MessageSounds.create(activity, key, true); preview = player;
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
