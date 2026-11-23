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

final class ContactsRepository {
    static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static String cacheOwner = "";
    private static Result cached;
    private static long cachedAt;
    private static Task<Result> inFlight;
    static synchronized void invalidate() { cachedAt = 0; }
    static synchronized void clear() { cacheOwner = ""; cached = null; cachedAt = 0; inFlight = null; }
    static final class Contact {
        final String uid, name, email;
        Contact(String uid, String name, String email) { this.uid = uid; this.name = name; this.email = email; }
    }
    static final class Result {
        final Map<String, String> names = new LinkedHashMap<>();
        final List<Contact> contacts = new ArrayList<>();
        boolean truncated;
    }
    static String normalize(String value) {
        if (value == null) return null;
        String email = value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= 254 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ? email : null;
    }
    static synchronized Task<Result> load(Context context, String ownEmail) {
        String owner = Backend.uid();
        if (owner.equals(cacheOwner)) {
            if (cached != null && System.currentTimeMillis() - cachedAt < 600000) return Tasks.forResult(cached);
            if (inFlight != null) return inFlight;
        }
        if (!owner.equals(cacheOwner)) { cached = null; cachedAt = 0; inFlight = null; }
        cacheOwner = owner;
        Context app = context.getApplicationContext();
        Task<Result> task = Tasks.call(IO, () -> {
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
        }).onSuccessTask(result -> discover(result, new ArrayList<>(result.names.keySet()), 0, owner));
        inFlight = task;
        task.addOnCompleteListener(completed -> {
            synchronized (ContactsRepository.class) {
                if (!owner.equals(cacheOwner) || !owner.equals(Backend.uid()) || inFlight != task) return;
                inFlight = null;
                if (completed.isSuccessful()) { cached = completed.getResult(); cachedAt = System.currentTimeMillis(); }
            }
        });
        return task;
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
