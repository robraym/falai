package br.com.falai;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateMetadataTest {
    private Map<String, Object> offer() {
        Map<String, Object> data = new HashMap<>(); data.put("versionName", "1.5"); data.put("versionCode", "220000001");
        data.put("tag", "v1.5-apk.220000001"); data.put("size", "100000"); data.put("asset", "falai.apk");
        data.put("digest", "sha256:" + "a".repeat(64)); return data;
    }
    @Test public void acceptsPushStringsAndServerNumbers() {
        assertNotNull(UpdateMetadata.parse(offer())); Map<String, Object> data = offer();
        data.put("versionCode", 220000001L); data.put("size", 100000L); assertNotNull(UpdateMetadata.parse(data));
    }
    @Test public void rejectsMismatchedVersionsAndUnsafePaths() {
        Map<String, Object> data = offer(); data.put("versionCode", "220000002"); assertNull(UpdateMetadata.parse(data));
        data = offer(); data.put("versionName", "9.9"); assertNull(UpdateMetadata.parse(data));
        data = offer(); data.put("asset", "../other.apk"); assertNull(UpdateMetadata.parse(data));
        data = offer(); data.put("tag", "../../other"); assertNull(UpdateMetadata.parse(data));
    }
    @Test public void rejectsIncompleteInvalidAndOversizedOffers() {
        for (String key : offer().keySet()) { Map<String, Object> data = offer(); data.remove(key); assertNull(key, UpdateMetadata.parse(data)); }
        Map<String, Object> data = offer(); data.put("size", "209715201"); assertNull(UpdateMetadata.parse(data));
        data = offer(); data.put("digest", "sha256:bad"); assertNull(UpdateMetadata.parse(data));
        assertNull(UpdateMetadata.parse(null));
    }
}
