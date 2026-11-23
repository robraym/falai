package br.com.falai;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import java.util.ArrayList;
import java.util.HashSet;

/** Uses the same alarm-volume playback as Alertou, independently of ringer mode. */
final class MessageSound {
    private static MediaPlayer current;

    static synchronized void play(Context context, String messageKey) {
        SharedPreferences prefs = context.getSharedPreferences("message_sound", Context.MODE_PRIVATE);
        ArrayList<String> recent = new ArrayList<>(prefs.getStringSet("recent", new HashSet<>()));
        if (recent.contains(messageKey)) return;
        // Bound duplicate suppression across process restarts; message keys contain no message text.
        if (recent.size() >= 100) recent.clear();
        recent.add(messageKey);
        prefs.edit().putStringSet("recent", new HashSet<>(recent)).apply();
        try {
            if (current != null) { current.release(); current = null; }
            String selected = MessageSounds.selected(context);
            MediaPlayer player;
            try { player = MessageSounds.create(context.getApplicationContext(), selected, false); }
            catch (java.io.IOException | RuntimeException error) { selected = MessageSounds.DEFAULT; player = MessageSounds.create(context.getApplicationContext(), selected, false); }
            current = player;
            player.setOnCompletionListener(MessageSound::release);
            player.setOnErrorListener((failed, what, extra) -> { release(failed); return true; });
            MessageSounds.start(player, selected, 1f);
        } catch (java.io.IOException | RuntimeException ignored) {
            if (current != null) release(current);
        }
    }

    private static synchronized void release(MediaPlayer player) {
        if (current == player) current = null;
        player.release();
    }
    private MessageSound() { }
}
