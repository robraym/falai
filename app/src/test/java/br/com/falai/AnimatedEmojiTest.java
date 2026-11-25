package br.com.falai;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnimatedEmojiTest {
    @Test public void catalogueUsesNormalUnicodeForCompatibility() {
        assertTrue(AnimatedEmoji.VALUES.size() > 3500);
        String[] original = {"😀", "😂", "😍", "😘", "😎", "😭", "😡", "😴", "👍", "👏", "❤️", "🎉"};
        for (int i = 0; i < original.length; i++) assertEquals(i, AnimatedEmoji.index(original[i]));
        for (String emoji : AnimatedEmoji.VALUES) {
            assertTrue(AnimatedEmoji.index(emoji) >= 0);
            assertFalse(emoji.contains(":"));
        }
    }
    @Test public void heartSupportsKeyboardPresentationVariantsAndWhitespace() {
        assertEquals(AnimatedEmoji.index("❤️"), AnimatedEmoji.index("❤"));
        assertEquals(AnimatedEmoji.index("😂"), AnimatedEmoji.index("  😂\n"));
    }
    @Test public void mixedTextMultipleEmojiAndUnknownEmojiRemainText() {
        for (String text : new String[]{"Oi 😀", "😀😀", "😀 ❤️", "", "  ", "[emoji:heart]"}) {
            assertEquals(text, -1, AnimatedEmoji.index(text));
        }
        assertEquals(-1, AnimatedEmoji.index(null));
    }
    @Test public void catalogueCoversSkinTonesFlagsAndJoinedFamilies() {
        for (String emoji : new String[]{"👍🏽", "🦋", "🇧🇷", "👨‍👩‍👧‍👦", "1️⃣"}) assertTrue(emoji, AnimatedEmoji.index(emoji) >= 0);
        assertEquals(9, EmojiCatalog.GROUPS.size());
        for (java.util.List<String> group : EmojiCatalog.GROUPS) for (String emoji : group) assertTrue(AnimatedEmoji.index(emoji) >= 0);
    }
}
