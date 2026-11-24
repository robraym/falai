package br.com.falai;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.gms.tasks.Tasks;

/** Activity-bound UI over a durable platform download; never launches an installer in the background. */
final class AppUpdateController implements DefaultLifecycleObserver {
    private final MainActivity activity;
    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> installPermission;
    private AppUpdateDialog dialog;
    private TextView status, detail;
    private AppUpdate update;
    private ApkDownload.State last;
    private boolean foreground, destroyed, busy, querying, verifying, installRequested;
    private boolean restoreOnStart;
    private long verifiedId = -1;
    private int failure;
    private final Runnable poll = this::refresh;
    AppUpdateController(MainActivity activity) {
        this.activity = activity; app = activity.getApplicationContext();
        installPermission = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (destroyed || !ApkDownload.pending(app)) return;
            openExisting();
            if (canInstall()) { installRequested = true; refresh(); }
            else {
                installRequested = false;
                if (status != null) status.setText(R.string.apk_install_permission_needed);
            }
        });
        activity.getLifecycle().addObserver(this);
        Tasks.call(ContactsRepository.IO, () -> { ApkDownload.clearInstalled(app); return null; });
    }
    boolean openExisting() {
        if (!ApkDownload.pending(app)) return false;
        update = ApkDownload.saved(app); installRequested = foreground; showDialog(); refresh(); return true;
    }
    boolean isOpen() { return showing(); }
    void restore() { restoreOnStart = true; }
    void download(AppUpdate update) {
        if (openExisting()) return;
        this.update = update; showDialog(); begin();
    }
    private boolean showing() { return !destroyed && dialog != null && dialog.isShowing(); }
    private void showDialog() {
        if (showing()) return;
        dialog = new AppUpdateDialog(activity);
        status = dialog.message; detail = dialog.detail;
        dialog.setInstalledDetails(activity.installedApkDetails());
        status.setText(R.string.apk_preparing_download);
        detail.setVisibility(View.GONE);
        dialog.setProgressState(true, 0, "");
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (failure != 0 || last == null || last.status == DownloadManager.STATUS_FAILED) begin(); else attemptInstall();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> cancel());
            buttons(false, true);
        });
        dialog.setOnDismissListener(ignored -> { installRequested = false; handler.removeCallbacks(poll); });
        dialog.setCanceledOnTouchOutside(false); dialog.show();
    }
    private void buttons(boolean ready, boolean active) {
        if (!showing()) return;
        android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.setVisibility(failure != 0 ? View.VISIBLE : View.GONE);
        positive.setText(R.string.retry);
        positive.setEnabled(!busy && !verifying);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(active ? View.VISIBLE : View.GONE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(R.string.cancel_apk_download);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);

    }
    private void begin() {
        if (busy || update == null) return;
        busy = true; failure = 0; verifiedId = -1; installRequested = foreground;
        dialog.setProgressState(true, 0, ""); status.setText(R.string.apk_preparing_download); buttons(false, true);
        AppUpdate offered = update;
        Tasks.call(ContactsRepository.IO, () -> { ApkDownload.start(app, offered); return null; }).addOnCompleteListener(result -> {
            busy = false; if (!showing()) return;
            if (!result.isSuccessful()) showFailure(R.string.apk_download_failed); else refresh();
        });
    }
    private void cancel() {
        if (busy) return;
        busy = true; installRequested = false; status.setText(R.string.apk_canceling_download); buttons(false, true);
        Tasks.call(ContactsRepository.IO, () -> { ApkDownload.cancel(app); return null; }).addOnCompleteListener(result -> {
            busy = false; if (!showing()) return;
            if (result.isSuccessful()) dialog.dismiss(); else showFailure(R.string.apk_download_cancel_failed);
        });
    }
    private void refresh() {
        handler.removeCallbacks(poll);
        if (!showing() || !foreground || busy || querying || verifying) return;
        querying = true;
        Tasks.call(ContactsRepository.IO, () -> ApkDownload.inspect(app)).addOnCompleteListener(result -> {
            querying = false; if (!showing() || !foreground || busy) return;
            if (!result.isSuccessful()) { showFailure(R.string.apk_download_failed); return; }
            ApkDownload.State state = result.getResult();
            if (state.id != ApkDownload.id(app)) { handler.post(poll); return; }
            last = state; if (state.update != null) update = state.update;
            if (failure != 0) { showFailure(failure); return; }
            if (state.status == DownloadManager.STATUS_SUCCESSFUL) {
                if (verifiedId != state.id || !ApkDownload.installerFile(app, state.id).isFile()) verify(state);
                else {
                    dialog.hideProgress(); status.setText(R.string.apk_ready_to_install);
                    detail.setVisibility(View.GONE); buttons(true, false);
                    if (installRequested) attemptInstall();
                }
                return;
            }
            if (state.status == DownloadManager.STATUS_FAILED) { showFailure(state.reason == DownloadManager.ERROR_INSUFFICIENT_SPACE ? R.string.apk_download_no_space : R.string.apk_download_failed); return; }
            long total = state.total > 0 ? state.total : update.size;
            int percent = total <= 0 ? 0 : (int) Math.min(100, Math.max(0, state.downloaded) * 100 / total);
            dialog.setProgressState(total <= 0, percent, activity.getString(R.string.apk_update_transfer_size,
                android.text.format.Formatter.formatShortFileSize(app, Math.max(0, state.downloaded)), android.text.format.Formatter.formatShortFileSize(app, Math.max(0, total))));
            status.setText(state.status == DownloadManager.STATUS_PAUSED ? R.string.apk_download_waiting : R.string.apk_update_downloading);
            detail.setVisibility(View.GONE); buttons(false, true); handler.postDelayed(poll, 700);
        });
    }
    private void verify(ApkDownload.State state) {
        if (verifying || state.update == null) return;
        verifying = true; dialog.setProgressState(true, 0, ""); status.setText(R.string.apk_verifying_download); buttons(false, false);
        Tasks.call(ContactsRepository.IO, () -> { ApkDownload.verify(app, state); return null; }).addOnCompleteListener(result -> {
            verifying = false; if (destroyed || state.id != ApkDownload.id(app)) return;
            if (result.isSuccessful()) verifiedId = state.id;
            else {
                Throwable error = result.getException();
                failure = R.string.apk_update_invalid_file;
                if (error instanceof ApkUpdatePolicy.Failure) {
                    ApkUpdatePolicy.Reason reason = ((ApkUpdatePolicy.Failure) error).reason;
                    if (reason == ApkUpdatePolicy.Reason.SIGNATURE) failure = R.string.apk_update_signature_mismatch;
                    else if (reason == ApkUpdatePolicy.Reason.SPACE) failure = R.string.apk_download_no_space;
                }
            }
            refresh();
        });
    }
    private void showFailure(int text) {
        failure = text; installRequested = false; dialog.hideProgress(); status.setText(text);
        detail.setText(R.string.apk_download_retry_hint); detail.setVisibility(View.VISIBLE); buttons(false, false);
    }
    private boolean canInstall() { return Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls(); }
    private void attemptInstall() {
        if (!showing() || !foreground || busy || last == null) return;
        if (verifiedId != last.id) { installRequested = true; refresh(); return; }
        installRequested = false;
        try {
            if (!canInstall()) {
                status.setText(R.string.apk_install_permission_needed);
                detail.setText(R.string.apk_install_permission_explanation);
                detail.setVisibility(View.VISIBLE);
                installPermission.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".updates", ApkDownload.installerFile(app, verifiedId));
            Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, ApkDownload.MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.setClipData(android.content.ClipData.newRawUri("Falai", uri));
            activity.startActivity(install);
            dialog.dismiss();
        } catch (RuntimeException error) { status.setText(R.string.apk_install_unavailable); }
    }
    @Override public void onStart(LifecycleOwner owner) {
        foreground = true;
        if (restoreOnStart) { restoreOnStart = false; openExisting(); }
        refresh();
    }
    @Override public void onStop(LifecycleOwner owner) {
        foreground = false; installRequested = false; handler.removeCallbacks(poll);
    }
    @Override public void onDestroy(LifecycleOwner owner) {
        destroyed = true; handler.removeCallbacks(poll); if (dialog != null) dialog.dismiss(); activity.getLifecycle().removeObserver(this);
    }
}
