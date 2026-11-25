package br.com.falai;

import android.net.Uri;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

final class AppUpdate {
    private static Task<AppUpdate> pendingCheck;
    static final class CheckWait extends IOException {
        final long until;
        final boolean serverLimit;
        CheckWait(long until, boolean serverLimit) {
            super("Update check available at " + until); this.until = until; this.serverLimit = serverLimit;
        }
    }
    private static final String REPOSITORY = "robraym/falai";
    private static final String APK_NAME = "Falai.apk";
    private static final Pattern VERSION_TAG = Pattern.compile("v([0-9]+(?:\\.[0-9]+){1,3})-apk\\.([1-9][0-9]{0,9})");

    final String versionName;
    final long versionCode;
    final Uri download;
    final long size;
    final String sha256;

    AppUpdate(String versionName, long versionCode, Uri download, long size, String sha256) {
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.download = download;
        this.size = size; this.sha256 = sha256;
    }

    static long buildTimeMillis(long versionCode) {
        return versionCode >= BuildConfig.APK_FIRST_DATED_CODE
                ? (BuildConfig.APK_BUILD_EPOCH_SECONDS + versionCode) * 1000L : 0;
    }

    boolean isNewerThanInstalled() {
        long installed = buildTimeMillis(BuildConfig.VERSION_CODE);
        long offered = buildTimeMillis(versionCode);
        if (installed == 0) return versionCode > BuildConfig.VERSION_CODE;
        return offered > installed;
    }
    static AppUpdate fromMetadata(UpdateMetadata value) {
        if (value == null) return null;
        Uri url = Uri.parse("https://github.com/" + REPOSITORY + "/releases/download/")
                .buildUpon().appendPath(value.tag).appendPath(value.asset).build();
        return new AppUpdate(value.versionName, value.versionCode, url, value.size, ApkUpdatePolicy.normalizeDigest(value.digest));
    }

    static synchronized Task<AppUpdate> check(android.content.Context context) {
        if (pendingCheck != null && !pendingCheck.isComplete()) return pendingCheck;
        android.content.Context app = context.getApplicationContext();
        pendingCheck = Tasks.call(ContactsRepository.IO, () -> {
            android.content.SharedPreferences gate = app.getSharedPreferences("update_checks", android.content.Context.MODE_PRIVATE);
            long now = System.currentTimeMillis(), next = gate.getLong("next_allowed", 0);
            if (now < next) throw new CheckWait(next, gate.getBoolean("server_limit", false));
            // Reserve the interval before any network IO, including failed requests.
            if (!gate.edit().putLong("next_allowed", now + UpdateCheckPolicy.INTERVAL_MS).putBoolean("server_limit", false).commit())
                throw new IOException("Unable to persist update check interval");
            URL endpoint = new URL("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
            try {
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Falai/" + BuildConfig.VERSION_NAME);
                int status = connection.getResponseCode();
                long received = System.currentTimeMillis();
                String remaining = connection.getHeaderField("x-ratelimit-remaining");
                boolean limited = UpdateCheckPolicy.limited(status, remaining);
                long allowed = UpdateCheckPolicy.nextAllowed(received, status, remaining,
                        connection.getHeaderField("x-ratelimit-reset"), connection.getHeaderField("retry-after"),
                        connection.getHeaderFieldDate("Date", 0));
                if (!gate.edit().putLong("next_allowed", allowed).putBoolean("server_limit", limited).commit())
                    throw new IOException("Unable to persist update check backoff");
                if (status == 403 || status == 429) throw new CheckWait(allowed, true);
                if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("GitHub release status " + status);
                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream body = new ByteArrayOutputStream();
                    byte[] bytes = new byte[4096];
                    for (int count; (count = input.read(bytes)) != -1; ) {
                        if (body.size() + count > 262144) throw new IOException("GitHub release response too large");
                        body.write(bytes, 0, count);
                    }
                    return readRelease(new JSONObject(new String(body.toByteArray(), StandardCharsets.UTF_8)));
                }
            } finally {
                connection.disconnect();
            }
        });
        return pendingCheck;
    }

    private static AppUpdate readRelease(JSONObject release) {
        String tag = release.optString("tag_name");
        Matcher version = VERSION_TAG.matcher(tag);
        JSONArray assets = release.optJSONArray("assets");
        if (!version.matches() || release.optBoolean("draft") || release.optBoolean("prerelease") || assets == null) return null;
        long code = Long.parseLong(version.group(2));
        if (code > Integer.MAX_VALUE) return null;
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset == null || !(APK_NAME.equals(asset.optString("name")) || "falai.apk".equals(asset.optString("name")))
                    || !"uploaded".equals(asset.optString("state")) || asset.optLong("size") <= 0 || asset.optLong("size") > ApkUpdatePolicy.MAX_BYTES) continue;
            Uri download = Uri.parse("https://github.com/" + REPOSITORY + "/releases/download/")
                    .buildUpon().appendPath(tag).appendPath(asset.optString("name")).build();
            String digest = asset.isNull("digest") ? "" : asset.optString("digest", "");
            return new AppUpdate(version.group(1), code, download, asset.optLong("size"), ApkUpdatePolicy.normalizeDigest(digest));
        }
        return null;
    }
}
