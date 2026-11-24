package br.com.falai;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unicode is the wire identifier: older clients still display the original emoji. */
final class AnimatedEmoji {
    static final List<String> VALUES = Collections.unmodifiableList(Arrays.asList(
            "😀", "😂", "😍", "😘", "😎", "😭", "😡", "😴", "👍", "👏", "❤️", "🎉"));
    static int index(String text) {
        if (text == null) return -1;
        String value = text.trim().replace("\uFE0F", "");
        for (int i = 0; i < VALUES.size(); i++) if (VALUES.get(i).replace("\uFE0F", "").equals(value)) return i;
        return -1;
    }
    private AnimatedEmoji() { }
}
