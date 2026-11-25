package br.com.falai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Qualified Unicode catalogue, shared by the picker and standalone-message renderer. */
final class EmojiCatalog {
    static final List<List<String>> GROUPS;
    static final List<String> VALUES;
    private static final Map<String, Integer> INDEX;
    static {
        List<List<String>> groups = new ArrayList<>();
        for (int i = 0; i < 9; i++) groups.add(new ArrayList<>());
        // Preserve the motion identifiers of the first twelve animated emojis.
        List<String> values = new ArrayList<>(Arrays.asList("😀", "😂", "😍", "😘", "😎", "😭", "😡", "😴", "👍", "👏", "❤️", "🎉"));
        Map<String, Integer> indices = new LinkedHashMap<>();
        for (int i = 0; i < values.size(); i++) indices.put(normalize(values.get(i)), i);
        for (String chunk : EmojiCatalogData.RECORDS) for (String line : chunk.split("\n")) {
            if (line.isEmpty()) continue;
            int separator = line.indexOf(';'), group = Integer.parseInt(line.substring(0, separator));
            StringBuilder glyph = new StringBuilder();
            for (String hex : line.substring(separator + 1).split(" +")) glyph.appendCodePoint(Integer.parseInt(hex, 16));
            String emoji = glyph.toString(); groups.get(group).add(emoji);
            String key = normalize(emoji);
            if (!indices.containsKey(key)) { indices.put(key, values.size()); values.add(emoji); }
        }
        List<List<String>> frozen = new ArrayList<>();
        for (List<String> group : groups) frozen.add(Collections.unmodifiableList(group));
        GROUPS = Collections.unmodifiableList(frozen); VALUES = Collections.unmodifiableList(values);
        INDEX = Collections.unmodifiableMap(indices);
    }
    private static String normalize(String value) { return value.trim().replace("\uFE0F", ""); }
    static int index(String value) { return value == null ? -1 : INDEX.getOrDefault(normalize(value), -1); }
    private EmojiCatalog() { }
}
