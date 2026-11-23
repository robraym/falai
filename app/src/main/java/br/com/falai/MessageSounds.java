package br.com.falai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Device-local sounds. Imported audio is copied to private storage, never uploaded. */
final class MessageSounds {
    static final String DEFAULT = "icq";
    static final int ICQ_COUNT = 8;
    static final String[] KEYS = {DEFAULT, "icq_soft", "icq_high", "icq_low", "icq_fast", "icq_calm", "icq_bright", "icq_full", "pop", "double", "drop", "crystal", "bell", "click", "bubble", "digital", "rise", "fall"};
    private static final int[] LABELS = {R.string.sound_icq, R.string.sound_icq_soft, R.string.sound_icq_high, R.string.sound_icq_low, R.string.sound_icq_fast, R.string.sound_icq_calm, R.string.sound_icq_bright, R.string.sound_icq_full, R.string.sound_pop, R.string.sound_double, R.string.sound_drop, R.string.sound_crystal, R.string.sound_bell, R.string.sound_click, R.string.sound_bubble, R.string.sound_digital, R.string.sound_rise, R.string.sound_fall};
    private static final int[] RAW = {R.raw.icq_uh_oh, R.raw.icq_uh_oh, R.raw.icq_uh_oh, R.raw.icq_uh_oh, R.raw.icq_uh_oh, R.raw.icq_uh_oh, R.raw.icq_uh_oh, R.raw.icq_uh_oh, R.raw.message_pop, R.raw.message_double, R.raw.message_drop, R.raw.message_crystal, R.raw.message_bell, R.raw.message_click, R.raw.message_bubble, R.raw.message_digital, R.raw.message_rise, R.raw.message_fall};
    static final class Custom {
        final String key, name;
        Custom(String key, String name) { this.key = key; this.name = name; }
    }
    private static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("message_sound", Context.MODE_PRIVATE); }
    private static int index(String key) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i; return -1; }
    private static File file(Context c, String key) {
        if (key == null || !key.matches("custom:[a-f0-9-]{36}")) return null;
        return new File(new File(c.getFilesDir(), "message_sounds"), key.substring(7));
    }
    static synchronized List<Custom> custom(Context c) {
        List<Custom> result = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(prefs(c).getString("custom", "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i); String key = value.getString("key"); File audio = file(c, key);
                if (audio != null && audio.isFile()) result.add(new Custom(key, value.getString("name")));
            }
        } catch (org.json.JSONException ignored) { }
        return result;
    }
    static synchronized String selected(Context c) {
        String key = prefs(c).getString("selected", DEFAULT);
        if (index(key) >= 0) return key;
        for (Custom sound : custom(c)) if (sound.key.equals(key)) return key;
        return DEFAULT;
    }
    static String label(Context c, String key) {
        int i = index(key); if (i >= 0) return c.getString(LABELS[i]);
        for (Custom sound : custom(c)) if (sound.key.equals(key)) return sound.name;
        return c.getString(R.string.sound_icq);
    }
    static synchronized void select(Context c, String key) {
        if (index(key) < 0 && custom(c).stream().noneMatch(sound -> sound.key.equals(key))) return;
        prefs(c).edit().putString("selected", key).apply();
    }
    private static void saveList(Context c, List<Custom> values) throws org.json.JSONException {
        JSONArray array = new JSONArray();
        for (Custom sound : values) array.put(new JSONObject().put("key", sound.key).put("name", sound.name));
        prefs(c).edit().putString("custom", array.toString()).apply();
    }
    static String importAudio(Context c, Uri uri) throws Exception {
        String key = "custom:" + UUID.randomUUID(), name = c.getString(R.string.sound_custom);
        try (android.database.Cursor cursor = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
        }
        name = name.replaceAll("[\\p{Cntrl}]", " ").trim(); if (name.length() > 100) name = name.substring(0, 100);
        if (name.isEmpty()) name = c.getString(R.string.sound_custom);
        File target = file(c, key), directory = target.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("directory");
        boolean kept = false;
        try {
            try (InputStream input = c.getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IOException("input");
                byte[] buffer = new byte[8192]; int read, total = 0;
                while ((read = input.read(buffer)) != -1) { total += read; if (total > 10 * 1024 * 1024) throw new IOException("size"); output.write(buffer, 0, read); }
                if (total == 0) throw new IOException("empty");
            }
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            try {
                metadata.setDataSource(target.getAbsolutePath());
                String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (duration == null || Long.parseLong(duration) <= 0) throw new IOException("audio");
                if (!"yes".equals(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))) throw new IOException("audio");
            } finally { metadata.release(); }
            synchronized (MessageSounds.class) {
                List<Custom> values = custom(c); values.add(new Custom(key, name)); saveList(c, values); select(c, key);
            }
            kept = true; return key;
        } finally { if (!kept && target.exists()) target.delete(); }
    }
    static synchronized void remove(Context c, String key) throws Exception {
        File audio = file(c, key); if (audio == null) return;
        if (audio.exists() && !audio.delete()) throw new IOException("delete");
        List<Custom> values = custom(c); values.removeIf(sound -> sound.key.equals(key)); saveList(c, values);
        if (key.equals(prefs(c).getString("selected", DEFAULT))) prefs(c).edit().putString("selected", DEFAULT).apply();
    }
    static MediaPlayer create(Context c, String key, boolean preview) throws IOException {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(preview ? AudioAttributes.USAGE_MEDIA : AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            int i = index(key);
            if (i >= 0) {
                try (android.content.res.AssetFileDescriptor asset = c.getResources().openRawResourceFd(RAW[i])) {
                    player.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
                }
            } else {
                File audio = file(c, key); if (audio == null || !audio.isFile()) throw new IOException("missing");
                player.setDataSource(audio.getAbsolutePath());
            }
            player.prepare(); return player;
        } catch (IOException | RuntimeException error) { player.release(); throw error; }
    }
    static void start(MediaPlayer player, String key, float volume) {
        float level = volume * ("icq_soft".equals(key) ? .55f : 1f); player.setVolume(level, level);
        float speed = 1f, pitch = 1f;
        switch (key) {
            case "icq_high": pitch = 1.18f; break;
            case "icq_low": pitch = .84f; break;
            case "icq_fast": speed = 1.3f; break;
            case "icq_calm": speed = .82f; break;
            case "icq_bright": speed = 1.12f; pitch = 1.38f; break;
            case "icq_full": speed = .94f; pitch = .7f; break;
        }
        if (speed != 1f || pitch != 1f) player.setPlaybackParams(new android.media.PlaybackParams().setSpeed(speed).setPitch(pitch));
        player.start();
    }
    private MessageSounds() { }
}
