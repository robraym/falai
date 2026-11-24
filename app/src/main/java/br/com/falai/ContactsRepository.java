package br.com.falai;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

final class ContactsRepository {
    static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static String cacheOwner = "";
    private static Result cached;
    private static long cachedAt;
    private static Task<Result> inFlight;
    private static android.content.SharedPreferences preferences(Context context, String owner) {
        return context.getSharedPreferences("discovered_contacts_" + owner, Context.MODE_PRIVATE);
    }
    static synchronized void invalidate() { clear(); }
    static synchronized void clear() {
        if (Backend.ready() && !Backend.uid().isEmpty()) preferences(FalaiApplication.instance, Backend.uid()).edit().clear().apply();
        cacheOwner = ""; cached = null; cachedAt = 0; inFlight = null;
    }
    static final class Contact {
        final String uid, name, email;
        Contact(String uid, String name, String email) { this.uid = uid; this.name = name; this.email = email; }
    }
    static final class Result {
        final Map<String, String> names = new LinkedHashMap<>();
        final List<Contact> contacts = new ArrayList<>();
        boolean truncated;
        private boolean restored;
        long savedAt;
    }
    static String normalize(String value) {
        if (value == null) return null;
        String email = value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= 254 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ? email : null;
    }
    static Task<Result> local(Context context) {
        String owner = Backend.uid();
        Context app = context.getApplicationContext();
        return Tasks.call(IO, () -> {
            if (owner.isEmpty() || !owner.equals(Backend.uid()) || androidx.core.content.ContextCompat.checkSelfPermission(app,
                    android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
            return restore(app, owner, false);
        });
    }
    static synchronized Task<Result> load(Context context, String ownEmail) {
        String owner = Backend.uid();
        if (owner.isEmpty() || androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return Tasks.forException(new SecurityException("contacts-permission"));
        }
        if (owner.equals(cacheOwner)) {
            long age = System.currentTimeMillis() - cachedAt;
            if (cached != null && age >= 0 && age < 600000) return Tasks.forResult(cached);
            if (inFlight != null) return inFlight;
        }
        if (!owner.equals(cacheOwner)) { cached = null; cachedAt = 0; inFlight = null; }
        cacheOwner = owner;
        Context app = context.getApplicationContext();
        Task<Result> task = Tasks.call(IO, () -> {
            Result saved = restore(app, owner, true);
            if (saved != null) return saved;
            Result result = new Result();
            try (Cursor cursor = app.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS}, null, null,
                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY + " ASC")) {
                if (cursor != null) while (cursor.moveToNext()) {
                    String email = normalize(cursor.getString(1));
                    if (email == null || email.equals(ownEmail)) continue;
                    if (result.names.size() >= 3000 && !result.names.containsKey(email)) { result.truncated = true; continue; }
                    String name = cursor.getString(0);
                    result.names.putIfAbsent(email, name == null || name.trim().isEmpty() ? email : name);
                }
            }
            return result;
        }).onSuccessTask(result -> {
            // Restored results already include the discovered users.
            if (result.restored) return Tasks.forResult(result);
            return discover(result, new ArrayList<>(result.names.keySet()), 0, owner).onSuccessTask(found -> {
                found.savedAt = System.currentTimeMillis(); return Tasks.forResult(found);
            });
        });
        inFlight = task;
        task.addOnCompleteListener(IO, completed -> {
            synchronized (ContactsRepository.class) {
                if (!owner.equals(cacheOwner) || !owner.equals(Backend.uid()) || inFlight != task) return;
                inFlight = null;
                if (completed.isSuccessful()) {
                    cached = completed.getResult(); cachedAt = cached.savedAt;
                    if (!cached.restored) persist(app, owner, cached, cachedAt);
                }
            }
        });
        return task;
    }
    private static Result restore(Context context, String owner, boolean requireFresh) {
        android.content.SharedPreferences prefs = preferences(context, owner);
        try {
            long savedAt = prefs.getLong("savedAt", 0), age = System.currentTimeMillis() - savedAt;
            if (savedAt == 0 || (requireFresh && (age < 0 || age >= 600000))) return null;
            JSONObject json = new JSONObject(prefs.getString("result", ""));
            Result result = new Result();
            JSONObject names = json.getJSONObject("names");
            java.util.Iterator<String> keys = names.keys();
            while (keys.hasNext()) { String key = keys.next(); result.names.put(key, names.getString(key)); }
            JSONArray contacts = json.getJSONArray("contacts");
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject contact = contacts.getJSONObject(i);
                result.contacts.add(new Contact(contact.getString("uid"), contact.getString("name"), contact.getString("email")));
            }
            result.truncated = json.optBoolean("truncated"); result.restored = true; result.savedAt = savedAt;
            return result;
        } catch (org.json.JSONException | ClassCastException error) { return null; }
    }
    private static void persist(Context context, String owner, Result result, long savedAt) {
        try {
            JSONArray contacts = new JSONArray();
            for (Contact contact : result.contacts) contacts.put(new JSONObject().put("uid", contact.uid).put("name", contact.name).put("email", contact.email));
            JSONObject json = new JSONObject().put("names", new JSONObject(result.names)).put("contacts", contacts).put("truncated", result.truncated);
            preferences(context, owner).edit().putString("result", json.toString()).putLong("savedAt", savedAt).apply();
        } catch (org.json.JSONException ignored) { }
    }
    @SuppressWarnings("unchecked")
    private static Task<Result> discover(Result result, List<String> emails, int offset, String owner) {
        if (!owner.equals(Backend.uid())) return Tasks.forException(new IllegalStateException("account-changed"));
        if (offset >= emails.size()) {
            result.contacts.sort(Comparator.comparing(c -> c.name.toLowerCase(Locale.ROOT)));
            return Tasks.forResult(result);
        }
        return Backend.call("discoverContacts", Backend.data("emails", emails.subList(offset, Math.min(offset + 100, emails.size()))))
                .onSuccessTask(response -> {
                    List<Map<String, Object>> found = (List<Map<String, Object>>) response.get("contacts");
                    for (Map<String, Object> contact : found) {
                        String email = (String) contact.get("email");
                        String profileName = (String) contact.get("name");
                        result.contacts.add(new Contact((String) contact.get("uid"), profileName == null || profileName.trim().isEmpty() ? result.names.get(email) : profileName, email));
                    }
                    return discover(result, emails, offset + 100, owner);
                });
    }
}
