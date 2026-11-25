package br.com.falai;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class Backend {
    static final class ApiException extends Exception {
        final String code;
        ApiException(String code) { super(code); this.code = code; }
    }
    private Backend() { }
    static boolean ready() { return !FirebaseApp.getApps(FalaiApplication.instance).isEmpty(); }
    static FirebaseAuth auth() { return FirebaseAuth.getInstance(); }
    static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }
    static String uid() { return auth().getCurrentUser() == null ? "" : auth().getCurrentUser().getUid(); }
    static Map<String, Object> data(Object... pairs) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    static Task<Map<String, Object>> call(String name, Map<String, Object> data) {
        if (!name.matches("[A-Za-z]+") || auth().getCurrentUser() == null) return Tasks.forException(new ApiException("unauthenticated"));
        String owner = uid();
        return auth().getCurrentUser().getIdToken(false).onSuccessTask(token -> Tasks.call(ContactsRepository.IO, () -> {
            if (!owner.equals(uid())) throw new ApiException("unauthenticated");
            HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.WORKER_URL + "/v1/" + name).openConnection();
            try {
                connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Authorization", "Bearer " + token.getToken());
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.setDoOutput(true);
                byte[] bytes = new JSONObject(data).toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                int status = connection.getResponseCode();
                JSONObject json;
                try (InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream()) {
                    if (input == null) throw new ApiException("unavailable");
                    ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) { if (output.size() + count > 262144) throw new ApiException("unavailable"); output.write(buffer, 0, count); }
                    json = new JSONObject(output.toString("UTF-8"));
                }
                if (status < 200 || status >= 300) throw new ApiException(json.optString("error", "unavailable"));
                return objectMap(json);
            } finally { connection.disconnect(); }
        }));
    }
    private static Map<String, Object> objectMap(JSONObject object) throws org.json.JSONException {
        Map<String, Object> result = new HashMap<>(); Iterator<String> keys = object.keys();
        while (keys.hasNext()) { String key = keys.next(); result.put(key, value(object.get(key))); } return result;
    }
    private static Object value(Object item) throws org.json.JSONException {
        if (item instanceof JSONObject) return objectMap((JSONObject) item);
        if (item instanceof JSONArray) {
            ArrayList<Object> result = new ArrayList<>(); JSONArray array = (JSONArray) item;
            for (int i = 0; i < array.length(); i++) result.add(value(array.get(i))); return result;
        }
        return item == JSONObject.NULL ? null : item;
    }
    static synchronized String deviceId() {
        android.content.SharedPreferences preferences = FalaiApplication.instance.getSharedPreferences("installation", Context.MODE_PRIVATE);
        String id = preferences.getString("deviceId", null);
        if (id == null) { id = UUID.randomUUID().toString(); preferences.edit().putString("deviceId", id).apply(); } return id;
    }
    static Task<Void> registerDevice() {
        if (!ready() || uid().isEmpty()) return Tasks.forResult(null);
        return FirebaseMessaging.getInstance().getToken().onSuccessTask(Backend::registerDevice);
    }
    static Task<Void> registerDevice(String token) {
        Task<Void> task = call("registerDevice", deviceData(token)).onSuccessTask(ignored -> UpdateNotice.syncTopic(FalaiApplication.instance));
        task.addOnFailureListener(error -> DeviceRegistrationWorker.schedule()); return task;
    }
    static Map<String, Object> deviceData(String token) {
        return data("deviceId", deviceId(), "token", token, "versionName", BuildConfig.VERSION_NAME, "versionCode", BuildConfig.VERSION_CODE);
    }
    static Task<Void> signOut() {
        DeviceRegistrationWorker.cancel(uid());
        return call("unregisterDevice", data("deviceId", deviceId())).continueWithTask(ignored -> {
            // A missing network connection must not keep the local account signed in.
            auth().signOut();
            return FirebaseMessaging.getInstance().deleteToken().continueWith(task -> null);
        });
    }
    static String error(Context context, Exception error) {
        if (error instanceof ApiException) {
            switch (((ApiException) error).code) {
                case "resource-exhausted": return context.getString(R.string.error_limit);
                case "unauthenticated":
                case "permission-denied": return context.getString(R.string.error_permission);
                case "not-found": return context.getString(R.string.error_not_found);
                case "invalid-argument": return context.getString(R.string.invalid_email);
                case "server-not-configured":
                case "server-credentials-invalid": return context.getString(R.string.server_unavailable);
                default: break;
            }
        }
        return context.getString(R.string.error_connection);
    }
}
