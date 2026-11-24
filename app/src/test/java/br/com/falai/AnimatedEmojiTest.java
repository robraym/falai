package br.com.falai;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnimatedEmojiTest {
    @Test public void catalogueUsesNormalUnicodeForCompatibility() {
        assertEquals(12, AnimatedEmoji.VALUES.size());
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
        for (String text : new String[]{"Oi 😀", "😀😀", "😀 ❤️", "🦋", "", "  ", "👍🏽", "[emoji:heart]"}) {
            assertEquals(text, -1, AnimatedEmoji.index(text));
        }
        assertEquals(-1, AnimatedEmoji.index(null));
    }
}
