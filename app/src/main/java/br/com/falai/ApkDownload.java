package br.com.falai;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** The platform owns the transfer; only this app's update directory is shared with the installer. */
final class ApkDownload {
    static final String MIME = "application/vnd.android.package-archive";
    static final class State {
        final long id, downloaded, total; final int status, reason; final AppUpdate update;
        State(long id, long downloaded, long total, int status, int reason, AppUpdate update) {
            this.id = id; this.downloaded = downloaded; this.total = total; this.status = status; this.reason = reason; this.update = update;
        }
    }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("apk_download", Context.MODE_PRIVATE); }
    static long id(Context context) { return prefs(context).getLong("id", -1); }
    static AppUpdate saved(Context context) {
        SharedPreferences p = prefs(context); long code = p.getLong("version", 0), size = p.getLong("size", 0);
        Uri uri = Uri.parse(p.getString("url", "")); String digest = p.getString("sha256", "");
        if (code <= 0 || code > Integer.MAX_VALUE || size <= 0 || size > ApkUpdatePolicy.MAX_BYTES
            || !"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getPort() != -1
            || uri.getUserInfo() != null || !trustedReleaseUrl(uri)
            || uri.getQuery() != null || uri.getFragment() != null || (!digest.isEmpty() && !digest.matches("[a-f0-9]{64}"))) return null;
        return new AppUpdate(p.getString("name", ""), code, uri, size, digest);
    }
    private static boolean trustedReleaseUrl(Uri uri) {
        String url = uri.toString();
        // Keep downloads already started before the repository rename resumable.
        return url.startsWith("https://github.com/robraym/falai/releases/download/")
            || url.startsWith("https://github.com/robraym/falei/releases/download/");
    }
    static boolean pending(Context context) {
        AppUpdate update = saved(context); return id(context) > 0 && update != null && update.isNewerThanInstalled();
    }
    static File file(Context context, AppUpdate update) throws IOException {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) throw new IOException("Update storage unavailable");
        return new File(base, "updates/falai-" + update.versionCode + ".apk");
    }
    static File installerFile(Context context, long id) { return new File(context.getFilesDir(), "updates/verified-" + id + ".apk"); }
    static synchronized void start(Context context, AppUpdate update) throws IOException {
        if (!update.isNewerThanInstalled()) throw new IOException("Update is not newer");
        cancel(context);
        File destination = file(context, update);
        if (!destination.getParentFile().isDirectory() && !destination.getParentFile().mkdirs()) throw new IOException("Update storage unavailable");
        if (destination.exists() && !destination.delete()) throw new IOException("Old update cannot be removed");
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        if (manager == null) throw new IOException("Download service unavailable");
        DownloadManager.Request request = new DownloadManager.Request(update.download)
            .setTitle(context.getString(R.string.app_update)).setDescription(context.getString(R.string.apk_downloading_version, update.versionName))
            .setMimeType(MIME).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/" + destination.getName());
        long id = manager.enqueue(request);
        boolean stored = prefs(context).edit().putLong("id", id).putLong("version", update.versionCode).putString("name", update.versionName)
            .putLong("size", update.size).putString("sha256", update.sha256).putString("url", update.download.toString()).commit();
        if (!stored) { manager.remove(id); throw new IOException("Download state cannot be saved"); }
    }
    static State inspect(Context context) {
        long id = id(context); AppUpdate update = saved(context);
        if (id > 0 && update != null) {
            DownloadManager manager = context.getSystemService(DownloadManager.class);
            if (manager != null) try (Cursor row = manager.query(new DownloadManager.Query().setFilterById(id))) {
                if (row != null && row.moveToFirst()) return new State(id,
                    row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)), update);
            }
        }
        return new State(id, 0, update == null ? 0 : update.size, DownloadManager.STATUS_FAILED, 0, update);
    }
    static synchronized void cancel(Context context) throws IOException {
        long id = id(context); AppUpdate previous = saved(context);
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        if (id > 0 && manager != null) manager.remove(id);
        if (!prefs(context).edit().clear().commit()) throw new IOException("Download state cannot be cleared");
        File verified = installerFile(context, id), staged = new File(verified.getParentFile(), "verified-" + id + ".part.apk");
        if (verified.exists() && !verified.delete()) throw new IOException("Update file cannot be removed");
        if (staged.exists() && !staged.delete()) throw new IOException("Partial update cannot be removed");
        if (previous != null) {
            File existing = file(context, previous);
            if (existing.exists() && !existing.delete()) throw new IOException("Update file cannot be removed");
        }
    }
    static synchronized void clearInstalled(Context context) throws IOException {
        AppUpdate update = saved(context);
        if (update != null && !update.isNewerThanInstalled()) cancel(context);
    }
    @SuppressWarnings("deprecation")
    static synchronized void verify(Context context, State state) throws IOException, PackageManager.NameNotFoundException {
        AppUpdate update = state.update;
        if (id(context) != state.id) throw new IOException("Download was replaced");
        File destination = installerFile(context, state.id);
        if (!destination.getParentFile().isDirectory() && !destination.getParentFile().mkdirs()) throw new IOException("Update storage unavailable");
        if (destination.isFile()) { verifyArchive(context, update, destination); return; }
        File file = new File(destination.getParentFile(), "verified-" + state.id + ".part.apk");
        try {
            if (file.exists() && !file.delete()) throw new IOException("Partial update cannot be removed");
            if (destination.getParentFile().getUsableSpace() < update.size) throw new ApkUpdatePolicy.Failure(ApkUpdatePolicy.Reason.SPACE);
            try (FileInputStream input = new FileInputStream(file(context, update)); FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[65536]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count; if (total > update.size || total > ApkUpdatePolicy.MAX_BYTES) throw new ApkUpdatePolicy.Failure(ApkUpdatePolicy.Reason.FILE);
                    output.write(buffer, 0, count);
                }
            }
            verifyArchive(context, update, file);
            if (destination.exists() && !destination.delete()) throw new IOException("Previous verified file cannot be removed");
            if (!file.renameTo(destination)) throw new IOException("Verified file cannot be saved");
        } finally { if (file.exists()) file.delete(); }
    }
    @SuppressWarnings("deprecation")
    private static void verifyArchive(Context context, AppUpdate update, File file) throws IOException, PackageManager.NameNotFoundException {
        ApkUpdatePolicy.verifyFile(file, update.size, update.sha256);
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
        if (candidate == null) throw new ApkUpdatePolicy.Failure(ApkUpdatePolicy.Reason.FILE);
        if (!update.versionName.equals(candidate.versionName)) throw new ApkUpdatePolicy.Failure(ApkUpdatePolicy.Reason.VERSION);
        Set<String> current = signers(installed, false), next = signers(candidate, false), history = signers(candidate, true);
        ApkUpdatePolicy.verifyIdentity(installed.packageName, version(installed), candidate.packageName, version(candidate), update.versionCode, current, next, history);
    }
    @SuppressWarnings("deprecation")
    private static long version(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo info, boolean history) {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return new HashSet<>();
            if (info.signingInfo.hasMultipleSigners()) signatures = info.signingInfo.getApkContentsSigners();
            else {
                Signature[] lineage = info.signingInfo.getSigningCertificateHistory();
                signatures = history || lineage == null || lineage.length == 0 ? lineage : new Signature[]{lineage[lineage.length - 1]};
            }
        } else signatures = info.signatures;
        Set<String> result = new HashSet<>();
        if (signatures != null) for (Signature signature : signatures) result.add(ApkUpdatePolicy.certificate(signature.toByteArray()));
        return result;
    }
    private ApkDownload() { }
}
