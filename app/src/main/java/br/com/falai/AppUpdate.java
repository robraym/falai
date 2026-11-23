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
    private static final String REPOSITORY = "robraym/falei";
    private static final String APK_NAME = "falai.apk";
    private static final Pattern VERSION_TAG = Pattern.compile("v([0-9]+(?:\\.[0-9]+){1,3})-apk\\.([1-9][0-9]{0,9})");

    final String versionName;
    final long versionCode;
    final Uri download;

    private AppUpdate(String versionName, long versionCode, Uri download) {
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.download = download;
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

    static Task<AppUpdate> check() {
        return Tasks.call(ContactsRepository.IO, () -> {
            URL endpoint = new URL("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
            try {
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Falai/" + BuildConfig.VERSION_NAME);
                int status = connection.getResponseCode();
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
            if (asset == null || !APK_NAME.equals(asset.optString("name"))
                    || !"uploaded".equals(asset.optString("state")) || asset.optLong("size") <= 0) continue;
            Uri download = Uri.parse("https://github.com/" + REPOSITORY + "/releases/download/")
                    .buildUpon().appendPath(tag).appendPath(APK_NAME).build();
            return new AppUpdate(version.group(1), code, download);
        }
        return null;
    }
}
