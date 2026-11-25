package br.com.falai;

import org.junit.Test;
import static org.junit.Assert.*;

public class GifCatalogTest {
    @Test public void everyBundledGifHasAUniqueStableMessageAndResource() {
        assertEquals(72, GifCatalog.IDS.length);
        assertEquals(GifCatalog.IDS.length, GifCatalog.FILES.length);
        assertEquals(GifCatalog.IDS.length, GifCatalog.LABELS.length);
        assertEquals(GifCatalog.IDS.length, GifCatalog.GROUPS.length);
        java.util.Set<String> messages = new java.util.HashSet<>();
        for (int i = 0; i < GifCatalog.IDS.length; i++) {
            assertTrue(messages.add(GifCatalog.message(i))); assertEquals(i, GifCatalog.index(GifCatalog.message(i)));
            assertEquals(i, GifCatalog.index("  " + GifCatalog.message(i) + "\n"));
        }
    }
    @Test public void allCategoriesHaveContentAndOriginalMessagesKeepTheirIds() {
        boolean[] categories = new boolean[7];
        for (int group : GifCatalog.GROUPS) { assertTrue(group >= 0 && group < categories.length); categories[group] = true; }
        for (boolean populated : categories) assertTrue(populated);
        String[] original = {"heart", "laugh", "party", "sleep", "coffee", "applause"};
        for (int i = 0; i < original.length; i++) assertEquals("[GIF:" + original[i] + "]", GifCatalog.message(i));
    }
    @Test public void unknownMixedOrRemoteGifDataRemainNormalText() {
        for (String value : new String[]{"", "[GIF:unknown]", "[GIF:../heart]", "[GIF:heart] hello", "https://example.com/a.gif", "😀"})
            assertEquals(-1, GifCatalog.index(value));
        assertEquals(-1, GifCatalog.index(null));
    }
}
