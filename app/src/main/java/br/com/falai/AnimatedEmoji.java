package br.com.falai;

import java.util.List;

/** Unicode is the wire identifier: older clients still display the original emoji. */
final class AnimatedEmoji {
    static final List<String> VALUES = EmojiCatalog.VALUES;
    static int index(String text) {
        return EmojiCatalog.index(text);
    }
    private AnimatedEmoji() { }
}
