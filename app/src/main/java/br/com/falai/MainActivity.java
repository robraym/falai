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


public final class MainActivity extends FalaiActivity {
    private LinearLayout root, body;
    private ListenerRegistration chatsListener;
    private String email = "", profileName = "";
    private int tab, generation;
    private boolean home, profileLoaded, contactsBusy, loadingProfile;
    private boolean selectingContact;
    private ContactsRepository.Result contacts;
    private long contactsAt;
    private android.os.CancellationSignal googleCancellation;
    private FloatingSearchController floatingSearch;
    private String chatQuery = "", contactQuery = "";
    public static final class ConversationState extends androidx.lifecycle.ViewModel {
        final List<DocumentSnapshot> documents = new ArrayList<>();
        final Map<String, com.google.firebase.Timestamp> locallyCleared = new java.util.HashMap<>(); String owner = "";
    }
    private ConversationState chatState;
    private List<DocumentSnapshot> cachedChats;
    private boolean observingContacts;
    private final android.os.Handler contactsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshContacts = this::reloadContacts;
    private final android.database.ContentObserver contactsObserver = new android.database.ContentObserver(contactsHandler) {
        @Override public void onChange(boolean selfChange) { contactsHandler.removeCallbacks(refreshContacts); contactsHandler.postDelayed(refreshContacts, 500); }
    };
    private final com.google.firebase.auth.FirebaseAuth.AuthStateListener authListener = auth -> {
        if (!Backend.uid().isEmpty() && !home && !loadingProfile) loadProfile();
    };
    private final ActivityResultLauncher<String> contactsPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) { observeContacts(); contactsAt = 0; showHome(1); } else showContactsPermission(true);
    });
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) Backend.registerDevice();
    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        chatState = new androidx.lifecycle.ViewModelProvider(this).get(ConversationState.class); cachedChats = chatState.documents;
        selectingContact = getIntent().getBooleanExtra("selectContact", false);
        if (state != null) { tab = state.getInt("tab"); chatQuery = state.getString("chatQuery", ""); contactQuery = state.getString("contactQuery", ""); }
        if (!Backend.ready()) { page(getString(R.string.app_name)); info(R.string.not_ready_title, R.string.not_ready_body); return; }
        if (Backend.uid().isEmpty()) showLogin(); else loadProfile();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out); out.putInt("tab", tab); out.putString("chatQuery", chatQuery); out.putString("contactQuery", contactQuery); }
    @Override protected void onStart() {
        super.onStart(); if (!Backend.ready()) return;
        observeContacts();
        if (home && profileLoaded) showHome(tab);
        Backend.auth().addAuthStateListener(authListener);
    }
    @Override protected void onStop() {
        detach(); contactsBusy = false; loadingProfile = false;
        if (observingContacts) { getContentResolver().unregisterContentObserver(contactsObserver); observingContacts = false; }
        contactsHandler.removeCallbacks(refreshContacts);
        if (Backend.ready()) Backend.auth().removeAuthStateListener(authListener);
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (googleCancellation != null) googleCancellation.cancel(); super.onDestroy();
    }
    private void detach() { if (chatsListener != null) { chatsListener.remove(); chatsListener = null; } if (floatingSearch != null) { floatingSearch.dispose(); floatingSearch = null; } }
    private void observeContacts() {
        if (!observingContacts && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            getContentResolver().registerContentObserver(android.provider.ContactsContract.Data.CONTENT_URI, true, contactsObserver); observingContacts = true;
        }
    }
    private void reloadContacts() {
        if (contactsBusy) { contactsHandler.postDelayed(refreshContacts, 500); return; }
        ContactsRepository.clear(); contacts = null; contactsAt = 0; if (home && tab == 1) showHome(1);
    }
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
        page(getString(selectingContact ? R.string.contacts : selected == 0 ? R.string.conversations : selected == 1 ? R.string.contacts : R.string.settings));
        if (selectingContact) {
            View heading = root.getChildAt(0); root.removeViewAt(0);
            LinearLayout header = Ui.row(this);
            header.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, v -> finish()));
            header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header, 0);
            showContacts(); return;
        }
        View profileButton = root.findViewById(R.id.button_profile);
        profileButton.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(this)), Ui.shape(this, R.color.action_green_soft, 21), null));
        profileButton.setVisibility(View.VISIBLE); profileButton.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        LinearLayout nav = Ui.row(this); nav.setBaselineAligned(false); View selectedView = null;
        int[] titles = {R.string.conversations, R.string.contacts, R.string.settings}; int[] icons = {R.drawable.ic_chat, R.drawable.ic_contacts, R.drawable.ic_settings};
        for (int i = 0; i < 3; i++) {
            final int index = i; LinearLayout item = Ui.column(this); item.setGravity(Gravity.CENTER); item.setBaselineAligned(false); item.setClickable(true); item.setFocusable(true);
            item.setContentDescription(getString(titles[i])); item.setOnClickListener(v -> showHome(index));
            View indicator = new View(this); indicator.setBackground(Ui.shape(this, R.color.action_green, 2)); indicator.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            item.addView(indicator, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 3)));
            ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setImageResource(icons[i]); icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, i == selected ? R.color.action_green : R.color.text_secondary)));
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 25), Ui.dp(this, 25)));
            TextView label = Ui.text(this, getString(titles[i]), 12, false); label.setTextColor(Ui.color(this, i == selected ? R.color.action_green : R.color.text_secondary));
            label.setGravity(Gravity.CENTER); label.setIncludeFontPadding(false); label.setSingleLine(true); label.setPadding(0, Ui.dp(this, 4), 0, 0);
            item.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
            nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1)); if (i == selected) selectedView = item;
        }
        root.addView(nav, new LinearLayout.LayoutParams(-1, Ui.dp(this, 78))); NavigationSurface.attach(root, nav, selectedView);
        if (selected == 0) showChats(); else if (selected == 1) showContacts(); else showSettings();
    }
    private RecyclerView list(LinearLayout parent, RowsAdapter adapter) {
        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        list.setClipToPadding(false); list.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 9)); parent.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); return list;
    }
    private void showChats() {
        if (!chatState.owner.equals(Backend.uid())) { cachedChats.clear(); chatState.locallyCleared.clear(); chatState.owner = Backend.uid(); }
        FrameLayout area = new FrameLayout(this); body.addView(area, new LinearLayout.LayoutParams(-1, -1));
        area.setId(R.id.floating_search_content_area);
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
        EditText search = attachSearch(area, conversations, "conversations"); search.setText(chatQuery);
        List<RowsAdapter.Row> allRows = new ArrayList<>();
        Runnable filter = () -> {
            List<RowsAdapter.Row> filtered = new ArrayList<>(); String value = searchable(search.getText().toString());
            for (RowsAdapter.Row row : allRows) if (searchable(row.name + " " + row.detail).contains(value)) filtered.add(row);
            adapter.submit(filtered); state.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            state.setText(value.isEmpty() ? R.string.empty_chats_body : R.string.no_search_results);
        };
        java.util.function.Consumer<List<DocumentSnapshot>> display = docs -> {
            allRows.clear();
            for (DocumentSnapshot chat : docs) {
                @SuppressWarnings("unchecked") List<String> members = (List<String>) chat.get("members");
                @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) chat.get("names");
                if (members == null || names == null) continue;
                com.google.firebase.Timestamp locallyCleared = chatState.locallyCleared.get(chat.getId());
                if (locallyCleared != null) {
                    if (chat.getTimestamp("updatedAt") == null || chat.getTimestamp("updatedAt").compareTo(locallyCleared) <= 0) continue;
                    chatState.locallyCleared.remove(chat.getId());
                }
                com.google.firebase.Timestamp cutoff = (com.google.firebase.Timestamp) chat.get("clearedBefore." + Backend.uid());
                if (cutoff != null && chat.getTimestamp("updatedAt") != null && chat.getTimestamp("updatedAt").compareTo(cutoff) <= 0) continue;
                String other = members.get(0).equals(Backend.uid()) ? members.get(1) : members.get(0), name = contactName(other, names.get(other));
                String preview = chat.getString("lastText"); com.google.firebase.Timestamp updated = chat.getTimestamp("updatedAt");
                @SuppressWarnings("unchecked") List<String> previewHidden = (List<String>) chat.get("previewHiddenFor");
                if (Boolean.TRUE.equals(chat.getBoolean("lastDeleted")) || (previewHidden != null && previewHidden.contains(Backend.uid()))) preview = getString(R.string.message_deleted);
                String dateTime = updated == null || preview == null || preview.isEmpty() ? "" : lastMessageDateTime(updated.toDate());
                int checks = Backend.uid().equals(chat.getString("lastSenderId")) ? chat.getTimestamp("lastReadAt") != null ? 3 : chat.getTimestamp("lastDeliveredAt") != null ? 2 : 1 : 0;
                allRows.add(new RowsAdapter.Row(chat.getId(), name, preview == null || preview.isEmpty() ? getString(R.string.new_conversation) : preview, dateTime, other, () -> ChatActivity.open(this, chat.getId(), name), () -> chatActions(chat.getId()), checks));
            }
            filter.run();
        };
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) { } public void onTextChanged(CharSequence s, int start, int before, int count) { chatQuery = s.toString(); filter.run(); } public void afterTextChanged(Editable e) { } });
        if (!cachedChats.isEmpty()) display.accept(cachedChats);
        chatsListener = Backend.db().collection("chats").whereArrayContains("members", Backend.uid()).orderBy("updatedAt", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener(this, (snap, error) -> {
                    if (generation != current) return;
                    if (error != null) { state.setVisibility(View.VISIBLE); state.setText(R.string.error_connection); return; }
                    if (snap == null) return;
                    if (snap.isEmpty() && snap.getMetadata().isFromCache()) return;
                    cachedChats.clear(); cachedChats.addAll(snap.getDocuments()); display.accept(cachedChats);
                });
    }
    private EditText attachSearch(FrameLayout area, View dismiss, String key) {
        area.setId(R.id.floating_search_content_area); dismiss.setId(R.id.search_dismiss_surface);
        View bar = getLayoutInflater().inflate(R.layout.view_floating_search, area, false);
        bar.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(this)), Ui.shape(this, R.color.action_green_soft, 24), null));
        area.addView(bar); floatingSearch = FloatingSearchController.attach(this, key, R.id.search_dismiss_surface); return floatingSearch.getInput();
    }
    private static String searchable(String value) { return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT); }
    private void chatActions(String id) {
        com.google.firebase.Timestamp previous = null;
        for (DocumentSnapshot doc : cachedChats) if (doc.getId().equals(id)) previous = doc.getTimestamp("updatedAt");
        final com.google.firebase.Timestamp lastMessage = previous;
        new AlertDialog.Builder(this).setItems(new String[]{getString(R.string.delete_chat), getString(R.string.cancel)}, (dialog, choice) -> {
            if (choice != 0) return;
            new AlertDialog.Builder(this).setMessage(R.string.delete_chat_question).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete_chat, (confirmation, button) ->
                Backend.call("deleteConversation", Backend.data("chatId", id)).addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) { if (lastMessage != null) chatState.locallyCleared.put(id, lastMessage); cachedChats.removeIf(doc -> doc.getId().equals(id)); showHome(0); }
                    else toast(Backend.error(this, task.getException()));
                })).show();
        }).show();
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
        body.removeAllViews(); LinearLayout card = info(R.string.contacts_title, denied ? R.string.contacts_denied : R.string.contacts_consent);
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
        TextView count = root.findViewById(R.id.page_subtitle); count.setText(getResources().getQuantityString(R.plurals.contact_count, contacts.contacts.size(), contacts.contacts.size())); count.setVisibility(View.VISIBLE);
        View titleBox = root.findViewById(R.id.page_title_box); android.view.ViewGroup.LayoutParams titleParams = titleBox.getLayoutParams(); titleParams.height = Ui.dp(this, 60); titleBox.setLayoutParams(titleParams);
        body.removeAllViews(); RowsAdapter adapter = new RowsAdapter();
        FrameLayout area = new FrameLayout(this); body.addView(area, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = Ui.column(this); area.addView(content, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = Ui.text(this, getString(R.string.no_contacts_body), 14, false); content.addView(empty); RecyclerView people = list(content, adapter);
        people.setPadding(people.getPaddingLeft(), people.getPaddingTop(), people.getPaddingRight(), Ui.dp(this, 68));
        EditText search = attachSearch(area, people, "contacts"); search.setText(contactQuery);
        Runnable filter = () -> {
            String query = searchable(search.getText().toString()); List<RowsAdapter.Row> rows = new ArrayList<>();
            for (ContactsRepository.Contact c : contacts.contacts) if (searchable(c.name + " " + c.email).contains(query)) rows.add(new RowsAdapter.Row(c.uid, c.name, c.email, () -> openContact(c)));
            adapter.submit(rows); empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE); empty.setText(query.isEmpty() ? R.string.no_contacts_body : R.string.no_search_results);
        };
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) { } public void onTextChanged(CharSequence s, int start, int before, int count) { contactQuery = s.toString(); filter.run(); } public void afterTextChanged(Editable e) { } }); filter.run();
        if (contacts.truncated) content.addView(Ui.text(this, getString(R.string.contacts_partial), 12, false));
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
        LinearLayout contents = scrollBody();
        LinearLayout appearance = Ui.card(this); appearance.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0); contents.addView(appearance);
        LinearLayout options = Ui.column(this);
        boolean expanded = Appearance.prefs(this).getBoolean("expanded", true); options.setVisibility(expanded ? View.VISIBLE : View.GONE);
        LinearLayout heading = Ui.optionRow(this, R.drawable.ic_palette, getString(R.string.appearance), getString(R.string.appearance_summary), true, v -> {
            boolean show = options.getVisibility() != View.VISIBLE; options.setVisibility(show ? View.VISIBLE : View.GONE);
            Appearance.prefs(this).edit().putBoolean("expanded", show).apply();
            ((LinearLayout) v).getChildAt(2).setRotation(show ? 90 : 0);
        });
        heading.getChildAt(2).setRotation(expanded ? 90 : 0); appearance.addView(heading); appearance.addView(options);
        options.addView(Ui.divider(this));
        options.addView(Ui.optionRow(this, R.drawable.ic_palette, getString(R.string.app_color), getString(Appearance.LABELS[Appearance.selected(this)]), false, v -> chooseColor()));
        options.addView(Ui.divider(this));
        options.addView(Ui.optionRow(this, R.drawable.ic_font, getString(R.string.text_size), getString(R.string.text_size_value, Appearance.percent(this)), false, v -> chooseTextSize()));
        LinearLayout general = Ui.card(this); general.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0); contents.addView(general);
        general.addView(Ui.optionRow(this, R.drawable.ic_bell, getString(R.string.notifications), getString(R.string.notifications_summary), true, v -> new AlertDialog.Builder(this)
                .setTitle(R.string.notifications).setMessage(R.string.notifications_body).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.enable_notifications, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                    else {
                        Backend.registerDevice();
                        if (Build.VERSION.SDK_INT >= 26) startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                        else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                    }
                }).show()));
        general.addView(Ui.divider(this));
        general.addView(Ui.optionRow(this, R.drawable.ic_lock, getString(R.string.privacy), getString(R.string.privacy_summary), true,
                v -> new AlertDialog.Builder(this).setTitle(R.string.privacy).setMessage(R.string.privacy_body).setPositiveButton(android.R.string.ok, null).show()));
        general.addView(Ui.divider(this));
        general.addView(Ui.optionRow(this, R.drawable.ic_logout, getString(R.string.sign_out), getString(R.string.sign_out_summary), true, v -> new AlertDialog.Builder(this).setMessage(R.string.sign_out_question).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.sign_out, (dialog, which) -> {
            Backend.signOut().addOnCompleteListener(this, task -> {
                if (!task.isSuccessful() && !Backend.uid().isEmpty()) { toast(Backend.error(this, task.getException())); return; }
                GoogleLogin.clear(this); ContactsRepository.clear(); cachedChats.clear(); chatState.locallyCleared.clear(); chatState.owner = ""; contacts = null; profileLoaded = false; profileName = ""; email = ""; getIntent().removeExtra("chatId"); showLogin();
                getSystemService(android.app.NotificationManager.class).cancelAll();
            });
        }).show()));
    }
    private void chooseColor() {
        String[] names = new String[Appearance.LABELS.length]; for (int i = 0; i < names.length; i++) names[i] = getString(Appearance.LABELS[i]);
        new AlertDialog.Builder(this).setTitle(R.string.app_color).setSingleChoiceItems(names, Appearance.selected(this), (dialog, which) -> {
            Appearance.prefs(this).edit().putInt("color", which).apply(); dialog.dismiss(); recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }
    private void chooseTextSize() {
        LinearLayout content = Ui.column(this); content.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 8));
        TextView value = Ui.text(this, getString(R.string.text_size_value, Appearance.percent(this)), 18, true); content.addView(value);
        TextView preview = Ui.text(this, getString(R.string.text_size_preview), 16, false); content.addView(preview);
        android.widget.SeekBar slider = new android.widget.SeekBar(this); slider.setMax(80); slider.setProgress(Appearance.percent(this) - 70); content.addView(slider);
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) { value.setText(getString(R.string.text_size_value, progress + 70)); preview.setTextSize(16f * (progress + 70) / Appearance.percent(MainActivity.this)); }
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        new AlertDialog.Builder(this).setTitle(R.string.text_size).setView(content).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, (dialog, which) -> {
            Appearance.prefs(this).edit().putInt("text", slider.getProgress() + 70).apply(); recreate();
        }).show();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (profileLoaded) openNotificationIntent(intent); }
    private void openNotificationIntent(Intent intent) {
        String id = intent.getStringExtra("chatId"), recipient = intent.getStringExtra("recipientId"); intent.removeExtra("chatId"); intent.removeExtra("recipientId");
        if (id != null && id.matches("[a-f0-9]{64}") && Backend.uid().equals(recipient)) ChatActivity.open(this, id, getString(R.string.app_name));
    }
}
