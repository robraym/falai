package br.com.falai;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.concurrent.TimeUnit;

/** Retries token registration without depending on an Activity staying open. */
public final class DeviceRegistrationWorker extends Worker {
    public DeviceRegistrationWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    static void schedule() {
        if (!Backend.ready() || Backend.uid().isEmpty()) return;
        String owner = Backend.uid();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DeviceRegistrationWorker.class)
                .setInputData(new Data.Builder().putString("owner", owner).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(FalaiApplication.instance).enqueueUniqueWork("device-" + owner, ExistingWorkPolicy.KEEP, request);
    }
    static void cancel(String owner) { WorkManager.getInstance(FalaiApplication.instance).cancelUniqueWork("device-" + owner); }
    @NonNull @Override public Result doWork() {
        if (!Backend.ready() || Backend.uid().isEmpty() || !Backend.uid().equals(getInputData().getString("owner"))) return Result.success();
        try {
            String token = Tasks.await(FirebaseMessaging.getInstance().getToken(), 30, TimeUnit.SECONDS);
            Tasks.await(Backend.call("registerDevice", Backend.deviceData(token)), 60, TimeUnit.SECONDS);
            return Result.success();
        } catch (Exception error) { return Result.retry(); }
    }
}
