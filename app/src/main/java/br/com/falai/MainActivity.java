package br.com.falai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public final class MainActivity extends AppCompatActivity {
    private LinearLayout root, body;
    private ListenerRegistration chatsListener;
    private String email = "", profileName = "";
    private int tab, generation;
    private boolean home, profileLoaded, contactsBusy, loadingProfile;
    private boolean selectingContact;
    private ContactsRepository.Result contacts;
    private long contactsAt;
    private android.os.CancellationSignal googleCancellation;
    private final com.google.firebase.auth.FirebaseAuth.AuthStateListener authListener = auth -> {
        if (!Backend.uid().isEmpty() && !home && !loadingProfile) loadProfile();
    };
    private final ActivityResultLauncher<String> contactsPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) { contactsAt = 0; showHome(1); } else showContactsPermission(true);
    });
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) Backend.registerDevice();
    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        selectingContact = getIntent().getBooleanExtra("selectContact", false);
        if (state != null) tab = state.getInt("tab");
        if (!Backend.ready()) { page(getString(R.string.app_name)); info(R.string.not_ready_title, R.string.not_ready_body); return; }
        if (Backend.uid().isEmpty()) showLogin(); else loadProfile();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out); out.putInt("tab", tab); }
    @Override protected void onStart() {
        super.onStart(); if (!Backend.ready()) return;
        if (home && profileLoaded) showHome(tab);
        Backend.auth().addAuthStateListener(authListener);
    }
    @Override protected void onStop() {
        detach(); contactsBusy = false; loadingProfile = false;
        if (Backend.ready()) Backend.auth().removeAuthStateListener(authListener);
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (googleCancellation != null) googleCancellation.cancel(); super.onDestroy();
    }
    private void detach() { if (chatsListener != null) { chatsListener.remove(); chatsListener = null; } }
    private void page(String title) {
        detach(); generation++; root = Ui.root(this); root.addView(Ui.heading(this, title));
        body = Ui.column(this); int inset = getResources().getDimensionPixelSize(R.dimen.navigation_content_inset);
        body.setPadding(inset, getResources().getDimensionPixelSize(R.dimen.screen_header_content_gap), inset, 0);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private LinearLayout scrollBody() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout contents = Ui.column(this); scroll.addView(contents); body.addView(scroll, new LinearLayout.LayoutParams(-1, -1)); return contents;
    }
    private LinearLayout info(int title, int summary) {
        LinearLayout card = Ui.card(this); card.addView(Ui.text(this, getString(title), 19, true)); Ui.gap(card, 8);
        card.addView(Ui.text(this, getString(summary), 14, false)); body.addView(card); return card;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void showLogin() {
        home = false; profileLoaded = false; page(getString(R.string.app_name));
        LinearLayout card = Ui.card(this); scrollBody().addView(card);
        card.addView(Ui.text(this, getString(R.string.welcome), 21, true)); Ui.gap(card, 8);
        card.addView(Ui.text(this, getString(R.string.login_intro), 14, false));
        card.addView(Ui.button(this, R.string.sign_in_google, v -> {
            v.setEnabled(false); googleCancellation = new android.os.CancellationSignal();
            try {
                GoogleLogin.signIn(this, googleCancellation).addOnCompleteListener(task -> {
                    if (isDestroyed() || isFinishing() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return;
                    v.setEnabled(true);
                    if (task.isSuccessful()) { if (!loadingProfile && !home) loadProfile(); }
                    else if (task.getException() instanceof GoogleLogin.NoGoogleAccountException) toast(getString(R.string.google_no_account));
                    else if (!(task.getException() instanceof androidx.credentials.exceptions.GetCredentialCancellationException)) toast(getString(R.string.auth_error));
                });
            } catch (Exception error) { v.setEnabled(true); toast(getString(R.string.auth_error)); }
        }));
        Ui.gap(card, 16); card.addView(Ui.text(this, getString(R.string.login_notice), 12, false));
    }
    private void loadProfile() {
        loadingProfile = true;
        home = false; page(getString(R.string.app_name)); TextView loading = Ui.text(this, getString(R.string.loading), 15, false); body.addView(loading);
        Backend.db().collection("users").document(Backend.uid()).get().addOnCompleteListener(this, task -> {
            loadingProfile = false;
            if (!task.isSuccessful()) { body.removeAllViews(); info(R.string.profile, R.string.error_connection).addView(Ui.button(this, R.string.retry, v -> loadProfile())); return; }
            DocumentSnapshot profile = task.getResult();
            if (!profile.exists()) { showProfileForm(); return; }
            profileName = profile.getString("name"); email = profile.getString("email"); profileLoaded = true;
            Backend.registerDevice().addOnFailureListener(this, error -> {
                DeviceRegistrationWorker.schedule(); toast(getString(R.string.notifications_registration_error));
            }); showHome(tab); openNotificationIntent(getIntent());
            if (Build.VERSION.SDK_INT >= 33 && !getPreferences(MODE_PRIVATE).getBoolean("notification_prompted", false)) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("notification_prompted", true).apply();
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
    }
    private void showProfileForm() {
        page(getString(R.string.profile_title)); LinearLayout card = Ui.card(this); scrollBody().addView(card);
        card.addView(Ui.text(this, getString(R.string.profile_intro), 15, false)); EditText name = Ui.input(this, R.string.name_hint); name.setText(Backend.auth().getCurrentUser().getDisplayName()); card.addView(name);
        card.addView(Ui.button(this, R.string.continue_label, v -> saveName(name, () -> loadProfile(), card)));
    }
    private void saveName(EditText field, Runnable success, View controls) {
        String name = field.getText().toString().trim();
        if (name.isEmpty() || name.length() > 60) { field.setError(getString(R.string.invalid_name)); return; }
        Ui.enable(controls, false);
        Backend.call("registerProfile", Backend.data("name", name)).addOnCompleteListener(this, task -> {
            Ui.enable(controls, true); if (task.isSuccessful()) { profileName = name; success.run(); } else toast(Backend.error(this, task.getException()));
        });
    }
    private void showHome(int selected) {
        if (Backend.uid().isEmpty()) { showLogin(); return; }
        home = true; tab = selectingContact ? 1 : selected;
        page(getString(selectingContact ? R.string.select_contact : R.string.app_name));
        if (selectingContact) {
            View heading = root.getChildAt(0); root.removeViewAt(0);
            LinearLayout header = Ui.row(this);
            header.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, v -> finish()));
            header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header, 0);
            showContacts(); return;
        }
        LinearLayout nav = Ui.row(this); nav.setBaselineAligned(false); View selectedView = null;
        int[] titles = {R.string.conversations, R.string.contacts, R.string.settings}; int[] icons = {R.drawable.ic_chat, R.drawable.ic_contacts, R.drawable.ic_settings};
        for (int i = 0; i < 3; i++) {
            final int index = i; LinearLayout item = Ui.column(this); item.setGravity(Gravity.CENTER); item.setBaselineAligned(false); item.setClickable(true); item.setFocusable(true);
            item.setContentDescription(getString(titles[i])); item.setOnClickListener(v -> showHome(index));
            View indicator = new View(this); indicator.setBackground(Ui.shape(this, R.color.action_green, 2)); indicator.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            item.addView(indicator, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 3)));
            ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setImageResource(icons[i]); icon.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(i == selected ? R.color.action_green : R.color.text_secondary)));
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 25), Ui.dp(this, 25)));
            TextView label = Ui.text(this, getString(titles[i]), 12, false); label.setTextColor(getColor(i == selected ? R.color.action_green : R.color.text_secondary));
            label.setGravity(Gravity.CENTER); label.setIncludeFontPadding(false); label.setSingleLine(true); label.setPadding(0, Ui.dp(this, 4), 0, 0);
            item.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
            nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1)); if (i == selected) selectedView = item;
        }
        root.addView(nav, new LinearLayout.LayoutParams(-1, Ui.dp(this, 78))); NavigationSurface.attach(root, nav, selectedView);
        if (selected == 0) showChats(); else if (selected == 1) showContacts(); else showSettings();
    }
    private RecyclerView list(LinearLayout parent, RowsAdapter adapter) {
        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        list.setClipToPadding(false); list.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 9)); parent.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); return list;
    }
    private void showChats() {
        FrameLayout area = new FrameLayout(this); body.addView(area, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = Ui.column(this); area.addView(content, new FrameLayout.LayoutParams(-1, -1));
        int current = generation; TextView state = Ui.text(this, getString(R.string.loading), 14, false); state.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), 0, Ui.dp(this, 12)); content.addView(state);
        RowsAdapter adapter = new RowsAdapter(); RecyclerView conversations = list(content, adapter);
        conversations.setPadding(conversations.getPaddingLeft(), conversations.getPaddingTop(), conversations.getPaddingRight(), Ui.dp(this, 88));
        ImageButton start = new ImageButton(this); start.setImageResource(R.drawable.ic_new_chat);
        start.setContentDescription(getString(R.string.start_conversation)); start.setScaleType(ImageView.ScaleType.FIT_CENTER);
        start.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.background)));
        start.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        start.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33000000), Ui.shape(this, R.color.action_green, 16), null));
        start.setElevation(Ui.dp(this, 6));
        FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.BOTTOM | Gravity.END);
        floating.setMargins(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16)); area.addView(start, floating);
        start.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class).putExtra("selectContact", true)));
        chatsListener = Backend.db().collection("chats").whereArrayContains("members", Backend.uid()).orderBy("updatedAt", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener(this, (snap, error) -> {
                    if (generation != current) return;
                    if (error != null) { state.setVisibility(View.VISIBLE); state.setText(R.string.error_connection); return; }
                    if (snap == null) return;
                    List<RowsAdapter.Row> rows = new ArrayList<>();
                    for (DocumentSnapshot chat : snap.getDocuments()) {
                        @SuppressWarnings("unchecked") List<String> members = (List<String>) chat.get("members");
                        @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) chat.get("names");
                        if (members == null || names == null) continue;
                        String other = members.get(0).equals(Backend.uid()) ? members.get(1) : members.get(0); String name = contactName(other, names.get(other));
                        String preview = chat.getString("lastText");
                        com.google.firebase.Timestamp updated = chat.getTimestamp("updatedAt");
                        String dateTime = updated == null || preview == null || preview.isEmpty() ? "" : lastMessageDateTime(updated.toDate());
                        rows.add(new RowsAdapter.Row(chat.getId(), name, preview == null || preview.isEmpty() ? getString(R.string.new_conversation) : preview, dateTime, () -> ChatActivity.open(this, chat.getId(), name)));
                    }
                    adapter.submit(rows); state.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE); state.setText(R.string.empty_chats_body);
                });
    }
    private String contactName(String uid, String fallback) {
        if (contacts != null) for (ContactsRepository.Contact contact : contacts.contacts) if (contact.uid.equals(uid)) return contact.name;
        return fallback == null ? getString(R.string.app_name) : fallback;
    }
    private String lastMessageDateTime(java.util.Date value) {
        java.util.Calendar day = java.util.Calendar.getInstance(), today = java.util.Calendar.getInstance(); day.setTime(value);
        java.util.Calendar yesterday = (java.util.Calendar) today.clone(); yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1);
        java.util.Calendar week = (java.util.Calendar) today.clone(); week.add(java.util.Calendar.DAY_OF_YEAR, -6);
        week.set(java.util.Calendar.HOUR_OF_DAY, 0); week.set(java.util.Calendar.MINUTE, 0); week.set(java.util.Calendar.SECOND, 0); week.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
        String label;
        if (sameDate(day, today)) label = getString(R.string.day_today);
        else if (sameDate(day, yesterday)) label = getString(R.string.day_yesterday);
        else if (day.after(week) && day.before(today)) label = new java.text.SimpleDateFormat("EEEE", locale).format(value);
        else {
            String skeleton = day.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) ? "ddMM" : "ddMMyyyy";
            label = new java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(value);
        }
        return label + " · " + android.text.format.DateFormat.getTimeFormat(this).format(value);
    }
    private static boolean sameDate(java.util.Calendar a, java.util.Calendar b) {
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }
    private void showContactsPermission(boolean denied) {
        body.removeAllViews(); body.addView(Ui.button(this, R.string.find_by_email, v -> findByEmail())); LinearLayout card = info(R.string.contacts_title, denied ? R.string.contacts_denied : R.string.contacts_consent);
        card.addView(Ui.button(this, denied ? R.string.open_app_settings : R.string.allow_contacts, v -> {
            if (denied) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            else contactsPermission.launch(Manifest.permission.READ_CONTACTS);
        }));
    }
    private void showContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { showContactsPermission(false); return; }
        if (contacts != null && System.currentTimeMillis() - contactsAt < 600000) { renderContacts(); return; }
        if (contactsBusy) { body.addView(Ui.text(this, getString(R.string.loading), 15, false)); return; }
        contactsBusy = true; body.addView(Ui.text(this, getString(R.string.loading), 15, false));
        ContactsRepository.load(this, email).addOnCompleteListener(this, task -> {
            contactsBusy = false;
            if (task.isSuccessful()) { contacts = task.getResult(); contactsAt = System.currentTimeMillis(); }
            if (!home || tab != 1) return;
            body.removeAllViews();
            if (task.isSuccessful()) renderContacts();
            else info(R.string.contacts_title, R.string.error_connection).addView(Ui.button(this, R.string.retry, v -> { body.removeAllViews(); showContacts(); }));
        });
    }
    private void renderContacts() {
        body.removeAllViews(); body.addView(Ui.button(this, R.string.find_by_email, v -> findByEmail())); RowsAdapter adapter = new RowsAdapter(); EditText search = Ui.input(this, R.string.search_contacts); body.addView(search);
        TextView empty = Ui.text(this, getString(R.string.no_contacts_body), 14, false); body.addView(empty); list(body, adapter);
        Runnable filter = () -> {
            String query = search.getText().toString().toLowerCase(java.util.Locale.ROOT); List<RowsAdapter.Row> rows = new ArrayList<>();
            for (ContactsRepository.Contact c : contacts.contacts) if (c.name.toLowerCase(java.util.Locale.ROOT).contains(query) || c.email.contains(query)) rows.add(new RowsAdapter.Row(c.uid, c.name, c.email, () -> openContact(c)));
            adapter.submit(rows); empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE); empty.setText(query.isEmpty() ? R.string.no_contacts_body : R.string.no_search_results);
        };
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) { } public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); } public void afterTextChanged(Editable e) { } }); filter.run();
        if (contacts.truncated) body.addView(Ui.text(this, getString(R.string.contacts_partial), 12, false));
    }
    private void findByEmail() {
        EditText input = Ui.input(this, R.string.email_hint); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.find_by_email).setView(input)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.search, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = ContactsRepository.normalize(input.getText().toString());
            if (value == null) { input.setError(getString(R.string.invalid_email)); return; }
            v.setEnabled(false); int current = generation;
            Backend.call("discoverContacts", Backend.data("emails", java.util.Collections.singletonList(value))).addOnCompleteListener(this, task -> {
                v.setEnabled(true); if (current != generation || !dialog.isShowing()) return;
                if (!task.isSuccessful()) { toast(Backend.error(this, task.getException())); return; }
                @SuppressWarnings("unchecked") List<Map<String, Object>> found = (List<Map<String, Object>>) task.getResult().get("contacts");
                if (found == null || found.isEmpty()) { input.setError(getString(R.string.contact_not_found)); return; }
                Map<String, Object> contact = found.get(0); dialog.dismiss();
                openContact(new ContactsRepository.Contact((String) contact.get("uid"), (String) contact.get("name"), value));
            });
        })); dialog.show();
    }
    private void openContact(ContactsRepository.Contact contact) {
        Ui.enable(body, false); int current = generation;
        Backend.call("openChat", Backend.data("uid", contact.uid)).addOnCompleteListener(this, task -> {
            if (generation != current) return; Ui.enable(body, true);
            if (task.isSuccessful()) {
                ChatActivity.open(this, (String) task.getResult().get("chatId"), contact.name);
                if (selectingContact) finish();
            }
            else toast(Backend.error(this, task.getException()));
        });
    }
    private void showSettings() {
        LinearLayout contents = scrollBody(); LinearLayout profile = Ui.card(this); contents.addView(profile);
        profile.addView(Ui.contactRow(this, profileName, email)); profile.addView(Ui.button(this, R.string.edit_name, v -> editName()));
        LinearLayout notifications = Ui.card(this); contents.addView(notifications); notifications.addView(Ui.text(this, getString(R.string.notifications), 18, true));
        notifications.addView(Ui.text(this, getString(R.string.notifications_body), 14, false)); notifications.addView(Ui.button(this, R.string.enable_notifications, v -> {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            else {
                Backend.registerDevice();
                if (Build.VERSION.SDK_INT >= 26) startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        }));
        LinearLayout privacy = Ui.card(this); contents.addView(privacy); privacy.addView(Ui.text(this, getString(R.string.privacy), 18, true)); Ui.gap(privacy, 8); privacy.addView(Ui.text(this, getString(R.string.privacy_body), 14, false));
        contents.addView(Ui.button(this, R.string.sign_out, v -> new AlertDialog.Builder(this).setMessage(R.string.sign_out_question).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.sign_out, (dialog, which) -> {
            Backend.signOut().addOnCompleteListener(this, task -> {
                if (!task.isSuccessful() && !Backend.uid().isEmpty()) { toast(Backend.error(this, task.getException())); return; }
                GoogleLogin.clear(this); ContactsRepository.clear(); contacts = null; profileLoaded = false; profileName = ""; email = ""; getIntent().removeExtra("chatId"); showLogin();
                getSystemService(android.app.NotificationManager.class).cancelAll();
            });
        }).show()));
    }
    private void editName() {
        EditText field = Ui.input(this, R.string.name_hint); field.setText(profileName);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.edit_name).setView(field).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(click -> saveName(field, () -> { dialog.dismiss(); showHome(2); }, dialog.getButton(AlertDialog.BUTTON_POSITIVE)))); dialog.show();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (profileLoaded) openNotificationIntent(intent); }
    private void openNotificationIntent(Intent intent) {
        String id = intent.getStringExtra("chatId"), recipient = intent.getStringExtra("recipientId"); intent.removeExtra("chatId"); intent.removeExtra("recipientId");
        if (id != null && id.matches("[a-f0-9]{64}") && Backend.uid().equals(recipient)) ChatActivity.open(this, id, getString(R.string.app_name));
    }
}
