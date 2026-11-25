package br.com.falai;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only metadata for the fixed Falai repository is accepted; notices cannot supply URLs. */
final class UpdateMetadata {
    final String versionName, tag, asset, digest;
    final long versionCode, size;
    private UpdateMetadata(String name, long code, long size, String tag, String asset, String digest) {
        this.versionName = name; this.versionCode = code; this.size = size; this.tag = tag; this.asset = asset; this.digest = digest;
    }
    static UpdateMetadata parse(Map<String, ?> data) {
        if (data == null) return null;
        try {
            String name = String.valueOf(data.get("versionName")), tag = String.valueOf(data.get("tag"));
            String asset = String.valueOf(data.get("asset")), digest = String.valueOf(data.get("digest"));
            long code = Long.parseLong(String.valueOf(data.get("versionCode"))), size = Long.parseLong(String.valueOf(data.get("size")));
            Matcher match = Pattern.compile("v([0-9]+(?:\\.[0-9]+){1,3})-apk\\.([1-9][0-9]{0,9})").matcher(tag);
            if (!match.matches() || !name.equals(match.group(1)) || code != Long.parseLong(match.group(2))
                    || code < 1 || code > Integer.MAX_VALUE || size <= 0 || size > ApkUpdatePolicy.MAX_BYTES
                    || !("Falai.apk".equals(asset) || "falai.apk".equals(asset)) || !digest.matches("sha256:[a-f0-9]{64}")) return null;
            return new UpdateMetadata(name, code, size, tag, asset, digest);
        } catch (RuntimeException ignored) { return null; }
    }
}
