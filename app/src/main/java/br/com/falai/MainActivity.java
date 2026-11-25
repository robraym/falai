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
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public final class MainActivity extends FalaiActivity {
    private static final long LOADING_INDICATOR_DELAY_MS = 1000;
    private static final long CONVERSATIONS_SPLASH_TIMEOUT_MS = 2500;
    private LinearLayout root, body;
    private View conversationsSplash;
    private TextView messageSoundSummary;
    private ListenerRegistration chatsListener;
    private ListenerRegistration defaultTimerListener;
    private String defaultTimerOwner = "";
    private String email = "", profileName = "";
    private int tab, generation;
    private boolean home, profileLoaded, contactsBusy, loadingProfile;
    private boolean selectingContact;
    private boolean showingArchived;
    private final androidx.activity.OnBackPressedCallback archiveBack = new androidx.activity.OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { showingArchived = false; showHome(0); }
    };
    private ContactsRepository.Result contacts;
    private long contactsAt;
    private android.os.CancellationSignal googleCancellation;
    private Runnable retentionRefresh;
    private String chatQuery = "", contactQuery = "";
    public static final class ConversationState extends androidx.lifecycle.ViewModel {
        final List<DocumentSnapshot> documents = new ArrayList<>();
        final Map<String, com.google.firebase.Timestamp> locallyCleared = new java.util.HashMap<>(); String owner = "";
        final Map<String, Boolean> archiveOverrides = new java.util.HashMap<>();
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
    private final MessageSoundPicker messageSounds = new MessageSoundPicker(this);
    private AppUpdateController appUpdates;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, archiveBack);
        showingArchived = state != null && state.getBoolean("showingArchived", false);
        appUpdates = new AppUpdateController(this);
        if (state != null && state.getBoolean("updateDialog", false)) appUpdates.restore();
        chatState = new androidx.lifecycle.ViewModelProvider(this).get(ConversationState.class); cachedChats = chatState.documents;
        selectingContact = getIntent().getBooleanExtra("selectContact", false);
        if (state != null) { tab = state.getInt("tab"); chatQuery = state.getString("chatQuery", ""); contactQuery = state.getString("contactQuery", ""); }
        if (!Backend.ready()) { page(getString(R.string.app_name)); info(R.string.not_ready_title, R.string.not_ready_body); return; }
        if (Backend.uid().isEmpty()) showLogin(); else loadProfile();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out); out.putInt("tab", tab); out.putString("chatQuery", chatQuery); out.putString("contactQuery", contactQuery); out.putBoolean("showingArchived", showingArchived); out.putBoolean("updateDialog", appUpdates != null && appUpdates.isOpen()); }
    @Override protected void onStart() {
        super.onStart(); if (!Backend.ready()) return;
        listenDefaultTimer();
        observeContacts();
        if (home && profileLoaded) showHome(tab);
        Backend.auth().addAuthStateListener(authListener);
    }
    @Override protected void onStop() {
        stopDefaultTimer();
        detach(); contactsBusy = false; loadingProfile = false;
        if (observingContacts) { getContentResolver().unregisterContentObserver(contactsObserver); observingContacts = false; }
        contactsHandler.removeCallbacks(refreshContacts);
        if (Backend.ready()) Backend.auth().removeAuthStateListener(authListener);
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (googleCancellation != null) googleCancellation.cancel(); super.onDestroy();
    }
    private void detach() { retentionRefresh = null; if (chatsListener != null) { chatsListener.remove(); chatsListener = null; } }
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
        archiveBack.setEnabled(false);
        dismissConversationsSplash();
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
        LinearLayout card = Ui.section(this); card.addView(Ui.text(this, getString(title), 19, true)); Ui.gap(card, 8);
        card.addView(Ui.text(this, getString(summary), 14, false)); body.addView(card); return card;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void showLogin() {
        stopDefaultTimer();
        home = false; profileLoaded = false; page(getString(R.string.app_name));
        LinearLayout card = Ui.section(this); scrollBody().addView(card);
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
        home = false;
        detach();
        generation++;
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.view_startup_splash);
        String owner = Backend.uid();
        Backend.db().collection("users").document(owner).get(Source.CACHE).addOnCompleteListener(this, cached -> {
            if (!owner.equals(Backend.uid())) return;
            boolean openedFromCache = cached.isSuccessful() && cached.getResult().exists();
            if (openedFromCache) {
                loadingProfile = false;
                openHomeWithProfile(cached.getResult());
            }
            refreshProfile(owner, openedFromCache);
        });
    }
    private void showLoadingAfterDelay(TextView indicator) {
        indicator.postDelayed(() -> {
            if (indicator.isAttachedToWindow() && getText(R.string.loading).equals(indicator.getText())) {
                indicator.setVisibility(View.VISIBLE);
            }
        }, LOADING_INDICATOR_DELAY_MS);
    }
    private void refreshProfile(String owner, boolean openedFromCache) {
        Backend.db().collection("users").document(owner).get().addOnCompleteListener(this, task -> {
            if (!owner.equals(Backend.uid())) return;
            loadingProfile = false;
            if (!task.isSuccessful()) {
                if (!openedFromCache) {
                    page(getString(R.string.app_name));
                    info(R.string.profile, R.string.error_connection).addView(Ui.button(this, R.string.retry, v -> loadProfile()));
                }
                return;
            }
            DocumentSnapshot profile = task.getResult();
            if (!profile.exists()) {
                home = false;
                profileLoaded = false;
                showProfileForm();
            } else if (openedFromCache) {
                profileName = profile.getString("name");
                email = profile.getString("email");
            } else {
                openHomeWithProfile(profile);
            }
        });
    }
    private void openHomeWithProfile(DocumentSnapshot profile) {
        profileName = profile.getString("name"); email = profile.getString("email"); profileLoaded = true; listenDefaultTimer();
        Backend.registerDevice().addOnFailureListener(this, error -> {
            DeviceRegistrationWorker.schedule(); toast(getString(R.string.notifications_registration_error));
        });
        View splash = findViewById(R.id.startup_splash);
        showHome(tab);
        if (splash != null && tab == 0 && !selectingContact && cachedChats.isEmpty() && !getIntent().hasExtra("chatId")) {
            TextView status = splash.findViewById(R.id.startup_status);
            status.setText(R.string.startup_loading_conversations);
            conversationsSplash = splash;
            addContentView(splash, new FrameLayout.LayoutParams(-1, -1));
            splash.postDelayed(() -> {
                if (conversationsSplash == splash) dismissConversationsSplash();
            }, CONVERSATIONS_SPLASH_TIMEOUT_MS);
        }
        openNotificationIntent(getIntent());
        if (Build.VERSION.SDK_INT >= 33 && !getPreferences(MODE_PRIVATE).getBoolean("notification_prompted", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_prompted", true).apply();
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
    private void dismissConversationsSplash() {
        if (conversationsSplash == null) return;
        android.view.ViewParent parent = conversationsSplash.getParent();
        if (parent instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) parent).removeView(conversationsSplash);
        }
        conversationsSplash = null;
    }
    private void showProfileForm() {
        page(getString(R.string.profile_title)); LinearLayout card = Ui.section(this); scrollBody().addView(card);
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
        if (selected == 3) { showHome(2); return; }
        if (!selectingContact && selected == 1) { showHome(0); return; }
        home = true; tab = selectingContact ? 1 : selected;
        if (selected != 0) showingArchived = false;
        page(getString(selectingContact ? R.string.contacts : showingArchived ? R.string.archived_chats : selected == 0 ? R.string.conversations : selected == 1 ? R.string.contacts : R.string.settings));
        root.setBackground(new SoftGlowBackground(this));
        if (!selectingContact && showingArchived) {
            archiveBack.setEnabled(true);
            View heading = root.getChildAt(0); root.removeViewAt(0);
            LinearLayout header = Ui.row(this);
            header.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, v -> { showingArchived = false; showHome(0); }));
            header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header, 0);
            showChats(); return;
        }
        if (selectingContact) {
            View heading = root.getChildAt(0); root.removeViewAt(0);
            LinearLayout header = Ui.row(this);
            header.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, v -> finish()));
            header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header, 0);
            showContacts(); return;
        }
        LinearLayout nav = Ui.row(this); nav.setBaselineAligned(false); View selectedView = null;
        int[] titles = {R.string.conversations, R.string.contacts, R.string.settings};
        int[] icons = {R.drawable.ic_chat, R.drawable.ic_contacts, R.drawable.ic_settings};
        for (int i : new int[]{0, 2}) {
            final int index = i; LinearLayout item = Ui.column(this); item.setGravity(Gravity.CENTER); item.setBaselineAligned(false); item.setClickable(true); item.setFocusable(true);
            item.setSelected(i == selected);
            item.setContentDescription(getString(titles[i])); item.setOnClickListener(v -> showHome(index));
            ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setImageResource(icons[i]); icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, i == selected ? R.color.action_green : R.color.text_secondary)));
            FrameLayout iconArea = new FrameLayout(this);
            if (i == selected) {
                android.graphics.drawable.GradientDrawable highlight = new android.graphics.drawable.GradientDrawable();
                highlight.setCornerRadius(Ui.dp(this, 12));
                highlight.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(Appearance.primary(this), 40));
                iconArea.setBackground(highlight);
            }
            iconArea.addView(icon, new FrameLayout.LayoutParams(Ui.dp(this, 29), Ui.dp(this, 29), Gravity.CENTER));
            item.addView(iconArea, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 42)));
            TextView label = Ui.text(this, getString(titles[i]), 14, i == selected); label.setTextColor(Ui.color(this, i == selected ? R.color.action_green : R.color.text_secondary));
            label.setGravity(Gravity.CENTER); label.setIncludeFontPadding(false); label.setSingleLine(true); label.setPadding(0, Ui.dp(this, 4), 0, 0);
            item.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
            nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1)); if (i == selected) selectedView = item;
        }
        root.addView(nav, new LinearLayout.LayoutParams(-1, Ui.dp(this, 86))); NavigationSurface.attach(root, nav, selectedView);
        if (selected == 2) root.setBackgroundColor(getColor(R.color.background)); else root.setBackground(new SoftGlowBackground(this));
        if (selected == 0) showChats(); else if (selected == 1) showContacts(); else showSettings();
    }
    private RecyclerView list(LinearLayout parent, RowsAdapter adapter) {
        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        list.setClipToPadding(false); list.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 9)); parent.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); return list;
    }
    private void showChats() {
        if (!chatState.owner.equals(Backend.uid())) { cachedChats.clear(); chatState.locallyCleared.clear(); chatState.archiveOverrides.clear(); chatState.owner = Backend.uid(); }
        FrameLayout area = new FrameLayout(this); body.addView(area, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = Ui.column(this); area.addView(content, new FrameLayout.LayoutParams(-1, -1));
        int current = generation; TextView state = Ui.text(this, getString(R.string.loading), 14, false); state.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), 0, Ui.dp(this, 12)); content.addView(state);
        state.setVisibility(View.GONE);
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
        if (showingArchived) { start.setVisibility(View.GONE); conversations.setPadding(0, conversations.getPaddingTop(), 0, Ui.dp(this, 9)); }
        EditText search = attachSearch(content); search.setText(showingArchived ? "" : chatQuery);
        LinearLayout archives = Ui.row(this); archives.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ImageView archiveIcon = new ImageView(this); archiveIcon.setImageResource(R.drawable.ic_archive);
        archiveIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(this)));
        archives.addView(archiveIcon, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView archiveLabel = Ui.text(this, getString(R.string.archived_chats), 16, false);
        archiveLabel.setPadding(Ui.dp(this, 24), 0, 0, 0); archives.addView(archiveLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView archiveCount = Ui.text(this, "", 13, true); archiveCount.setTextColor(Appearance.primary(this)); archives.addView(archiveCount);
        archives.setFocusable(true); archives.setOnClickListener(v -> { showingArchived = true; showHome(0); });
        content.addView(archives, 1); archives.setVisibility(View.GONE);
        List<RowsAdapter.Row> allRows = new ArrayList<>();
        java.util.Set<String> archivedIds = new java.util.HashSet<>();
        Runnable filter = () -> {
            List<RowsAdapter.Row> filtered = new ArrayList<>(); String value = searchable(search.getText().toString());
            for (RowsAdapter.Row row : allRows) if ((showingArchived ? archivedIds.contains(row.key) : !value.isEmpty() || !archivedIds.contains(row.key))
                    && searchable(ProfileNameView.current(row.avatarUid, row.name) + " " + row.detail).contains(value)) filtered.add(row);
            archives.setVisibility(!showingArchived && value.isEmpty() && !archivedIds.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.submit(filtered); state.setVisibility(filtered.isEmpty() && (showingArchived || !value.isEmpty() || archivedIds.isEmpty()) ? View.VISIBLE : View.GONE);
            state.setText(value.isEmpty() ? showingArchived ? R.string.empty_archived_chats : R.string.empty_chats_body : R.string.no_search_results);
        };
        java.util.function.Consumer<List<DocumentSnapshot>> display = docs -> {
            long retentionCutoff = HistoryRetention.cutoff(this);
            allRows.clear(); archivedIds.clear(); int unreadArchived = 0;
            for (DocumentSnapshot chat : docs) {
                boolean archived = isArchived(chat);
                ArchivedChats.save(this, Backend.uid(), chat.getId(), archived);
                @SuppressWarnings("unchecked") List<String> members = (List<String>) chat.get("members");
                @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) chat.get("names");
                if (members == null || names == null) continue;
                com.google.firebase.Timestamp locallyCleared = chatState.locallyCleared.get(chat.getId());
                if (locallyCleared != null) {
                    if (chat.getTimestamp("updatedAt") == null || chat.getTimestamp("updatedAt").compareTo(locallyCleared) <= 0) continue;
                    chatState.locallyCleared.remove(chat.getId());
                }
                com.google.firebase.Timestamp cutoff = (com.google.firebase.Timestamp) chat.get("clearedBefore." + Backend.uid());
                boolean cleared = cutoff != null && chat.getTimestamp("updatedAt") != null && chat.getTimestamp("updatedAt").compareTo(cutoff) <= 0;
                if (cleared && !Boolean.TRUE.equals(chat.getBoolean("clearedVisible." + Backend.uid()))) continue;
                if (chat.getTimestamp("updatedAt") != null && chat.getTimestamp("updatedAt").toDate().getTime() <= retentionCutoff) cleared = true;
                String other = members.get(0).equals(Backend.uid()) ? members.get(1) : members.get(0), name = contactName(other, names.get(other));
                String preview = chat.getString("lastText"); com.google.firebase.Timestamp updated = chat.getTimestamp("updatedAt");
                @SuppressWarnings("unchecked") List<String> previewHidden = (List<String>) chat.get("previewHiddenFor");
                if (Boolean.TRUE.equals(chat.getBoolean("lastDeleted")) || (previewHidden != null && previewHidden.contains(Backend.uid()))) preview = getString(R.string.message_deleted);
                else if ("nudge".equals(chat.getString("lastKind"))) preview = getString(Backend.uid().equals(chat.getString("lastSenderId")) ? R.string.nudge_sent : R.string.nudge_received);
                String dateTime = updated == null || preview == null || preview.isEmpty() ? "" : lastMessageDateTime(updated.toDate());
                int checks = Backend.uid().equals(chat.getString("lastSenderId")) ? chat.getTimestamp("lastReadAt") != null ? 3 : chat.getTimestamp("lastDeliveredAt") != null ? 2 : 1 : 0;
                long pending = HistoryRetention.unread(chat, Backend.uid());
                // Older summaries have no counter: still identify the last unread incoming message.
                int unread = pending >= 0 ? (int) Math.min(9999, pending)
                    : other.equals(chat.getString("lastSenderId")) && chat.getTimestamp("lastReadAt") == null
                        && !Boolean.TRUE.equals(chat.getBoolean("lastDeleted"))
                        && (previewHidden == null || !previewHidden.contains(Backend.uid())) ? -1 : 0;
                if (cleared) { preview = getString(R.string.chat_cleared); dateTime = ""; checks = 0; unread = 0; }
                com.google.firebase.Timestamp lastExpiry = chat.getTimestamp("lastExpiresAt");
                if (!cleared && lastExpiry != null && lastExpiry.toDate().getTime() <= System.currentTimeMillis()) {
                    preview = getString(R.string.timer_message_expired); dateTime = ""; checks = 0;
                }
                allRows.add(new RowsAdapter.Row(chat.getId(), name, preview == null || preview.isEmpty() ? getString(R.string.new_conversation) : preview, dateTime, other, () -> ChatActivity.open(this, chat.getId(), name), () -> chatActions(chat.getId()), checks, false, unread));
                if (archived) { archivedIds.add(chat.getId()); if (unread != 0) unreadArchived++; }
            }
            archiveCount.setText(unreadArchived == 0 ? "" : String.valueOf(unreadArchived));
            filter.run();
        };
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) { } public void onTextChanged(CharSequence s, int start, int before, int count) { if (!showingArchived) chatQuery = s.toString(); filter.run(); } public void afterTextChanged(Editable e) { } });
        retentionRefresh = () -> display.accept(cachedChats);
        if (!cachedChats.isEmpty()) display.accept(cachedChats);
        chatsListener = Backend.db().collection("chats").whereArrayContains("members", Backend.uid()).orderBy("updatedAt", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener(this, MetadataChanges.INCLUDE, (snap, error) -> {
                    if (generation != current) return;
                    if (error != null) { dismissConversationsSplash(); state.setVisibility(View.VISIBLE); state.setText(R.string.error_connection); return; }
                    if (snap == null) return;
                    if (snap.isEmpty() && snap.getMetadata().isFromCache()) {
                        if (cachedChats.isEmpty()) {
                            state.setText(R.string.loading);
                            showLoadingAfterDelay(state);
                        }
                        return;
                    }
                    cachedChats.clear(); cachedChats.addAll(snap.getDocuments()); display.accept(cachedChats);
                    dismissConversationsSplash();
                });
    }
    private EditText attachSearch(LinearLayout content) {
        content.setFocusableInTouchMode(true);
        content.requestFocus();
        EditText search = (EditText) getLayoutInflater().inflate(R.layout.view_inline_search, content, false);
        content.addView(search, 0);
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
            content.requestFocus();
            return true;
        });
        return search;
    }
    private static String searchable(String value) { return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT); }
    private void chatActions(String id) {
        com.google.firebase.Timestamp previous = null;
        for (DocumentSnapshot doc : cachedChats) if (doc.getId().equals(id)) previous = doc.getTimestamp("updatedAt");
        final com.google.firebase.Timestamp lastMessage = previous;
        boolean archived = ArchivedChats.contains(this, Backend.uid(), id);
        new AlertDialog.Builder(this).setItems(new String[]{getString(R.string.delete_chat), getString(R.string.clear_chat), getString(archived ? R.string.unarchive_chat : R.string.archive_chat), getString(R.string.cancel)}, (dialog, choice) -> {
            if (choice == 3) return;
            if (choice == 2) {
                String owner = Backend.uid();
                Backend.call("archiveConversation", Backend.data("chatId", id, "archived", !archived)).addOnCompleteListener(this, task -> {
                    if (!owner.equals(Backend.uid())) return;
                    if (task.isSuccessful()) {
                        chatState.archiveOverrides.put(id, !archived); ArchivedChats.save(this, owner, id, !archived);
                        if (home && tab == 0) showHome(0);
                    } else toast(Backend.error(this, task.getException()));
                }); return;
            }
            boolean clear = choice == 1;
            new AlertDialog.Builder(this).setMessage(clear ? R.string.clear_chat_question : R.string.delete_chat_question).setNegativeButton(R.string.cancel, null).setPositiveButton(clear ? R.string.clear_chat : R.string.delete_chat, (confirmation, button) ->
                Backend.call(clear ? "clearConversation" : "deleteConversation", Backend.data("chatId", id)).addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        if (!clear) { if (lastMessage != null) chatState.locallyCleared.put(id, lastMessage); cachedChats.removeIf(doc -> doc.getId().equals(id)); }
                        showHome(0);
                    }
                    else toast(Backend.error(this, task.getException()));
                })).show();
        }).show();
    }
    private boolean isArchived(DocumentSnapshot chat) {
        Object value = chat.get("archivedFor");
        boolean server = value instanceof List && ((List<?>) value).contains(Backend.uid());
        Boolean pending = chatState.archiveOverrides.get(chat.getId());
        if (pending != null && pending == server) { chatState.archiveOverrides.remove(chat.getId()); pending = null; }
        return pending == null ? server : pending;
    }
    private String contactName(String uid, String fallback) {
        return ProfileNameView.current(uid, fallback == null ? getString(R.string.app_name) : fallback);
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
        String owner = Backend.uid();
        ContactsRepository.local(this).addOnCompleteListener(this, saved -> {
            if (!owner.equals(Backend.uid())) return;
            if (saved.isSuccessful() && saved.getResult() != null) {
                contacts = saved.getResult(); contactsAt = contacts.savedAt;
                if (home && tab == 1) { body.removeAllViews(); renderContacts(); }
            }
            refreshContacts(owner);
        });
    }
    private void refreshContacts(String owner) {
        ContactsRepository.load(this, email).addOnCompleteListener(this, task -> {
            if (!owner.equals(Backend.uid())) return;
            contactsBusy = false;
            if (task.isSuccessful()) { contacts = task.getResult(); contactsAt = contacts.savedAt; }
            if (!home || tab != 1) return;
            body.removeAllViews();
            if (contacts != null) renderContacts();
            else info(R.string.contacts_title, R.string.error_connection).addView(Ui.button(this, R.string.retry, v -> { body.removeAllViews(); showContacts(); }));
        });
    }
    private void renderContacts() {
        TextView count = root.findViewById(R.id.page_subtitle); count.setText(getResources().getQuantityString(R.plurals.contact_count, contacts.contacts.size(), contacts.contacts.size()));
        count.setTextSize(10.5f); count.setTypeface(null, android.graphics.Typeface.BOLD); count.setGravity(Gravity.CENTER); count.setMinHeight(Ui.dp(this, 24));
        count.setPadding(Ui.dp(this, 7), Ui.dp(this, 3), Ui.dp(this, 7), Ui.dp(this, 3)); count.setTextColor(Appearance.primary(this));
        android.graphics.drawable.GradientDrawable badge = Ui.shape(this, R.color.presence_badge_surface, 8);
        badge.setStroke(Ui.dp(this, 1), androidx.core.graphics.ColorUtils.setAlphaComponent(Appearance.primary(this), 80)); count.setBackground(badge);
        ((LinearLayout) root.findViewById(R.id.page_title_box)).setBaselineAligned(false);
        LinearLayout.LayoutParams countParams = (LinearLayout.LayoutParams) count.getLayoutParams(); countParams.gravity = Gravity.CENTER_VERTICAL; count.setLayoutParams(countParams);
        count.setVisibility(View.VISIBLE);
        body.removeAllViews(); RowsAdapter adapter = new RowsAdapter();
        FrameLayout area = new FrameLayout(this); body.addView(area, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = Ui.column(this); area.addView(content, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = Ui.text(this, getString(R.string.no_contacts_body), 14, false); content.addView(empty); RecyclerView people = list(content, adapter);
        EditText search = attachSearch(content); search.setText(contactQuery);
        Runnable filter = () -> {
            String query = searchable(search.getText().toString()); List<RowsAdapter.Row> rows = new ArrayList<>();
            for (ContactsRepository.Contact c : contacts.contacts) if (searchable(ProfileNameView.current(c.uid, c.name) + " " + c.email).contains(query)) rows.add(new RowsAdapter.Row(c.uid, c.name, c.email, () -> openContact(c)));
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
        LinearLayout contents = Ui.section(this); scrollBody().addView(contents);
        LinearLayout profileRow = Ui.optionRow(this, R.drawable.ic_profile, getString(R.string.profile), profileName, false,
                v -> startActivity(new Intent(this, ProfileActivity.class)));
        LinearLayout profileLabels = (LinearLayout) profileRow.getChildAt(1);
        profileLabels.removeViewAt(1);
        ProfileNameView currentName = new ProfileNameView(this); currentName.setTextSize(13);
        currentName.setTextColor(getColor(R.color.text_secondary)); currentName.setMaxLines(2);
        currentName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        currentName.bind(Backend.uid(), profileName, null);
        profileLabels.addView(currentName);
        contents.addView(profileRow);
        contents.addView(Ui.optionRow(this, R.drawable.ic_history_duration, getString(R.string.history_duration), getString(HistoryRetention.LABELS[HistoryRetention.selected(this)]), false, v -> chooseHistoryDuration()));
        contents.addView(Ui.optionRow(this, R.drawable.ic_palette, getString(R.string.app_color), getString(Appearance.LABELS[Appearance.selected(this)]), false, v -> chooseColor()));
        contents.addView(Ui.optionRow(this, R.drawable.ic_font, getString(R.string.text_size), getString(R.string.text_size_value, Appearance.percent(this)), false, v -> chooseTextSize()));
        LinearLayout general = Ui.column(this); contents.addView(general);
        LinearLayout soundRow = Ui.optionRow(this, R.drawable.ic_volume_preview, getString(R.string.message_sounds), MessageSounds.label(this, MessageSounds.selected(this)), true, view -> messageSounds.show(() -> {
            if (messageSoundSummary != null) messageSoundSummary.setText(MessageSounds.label(this, MessageSounds.selected(this)));
        }));
        messageSoundSummary = (TextView) ((LinearLayout) soundRow.getChildAt(1)).getChildAt(1);
        general.addView(soundRow);
        general.addView(Ui.optionRow(this, R.drawable.ic_settings, getString(R.string.app_update),
                installedApkSummary(),
                true, view -> showAppUpdate()));
        SettingsPage.separateRows(general); SettingsPage.separateRows(contents);
    }
    private void showAppUpdate() {
        if (appUpdates.openExisting()) return;
        AppUpdateDialog about = new AppUpdateDialog(this);
        about.setInstalledDetails(installedApkDetails());
        about.setMessage("");
        about.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
        about.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> checkAppUpdate(about));
        about.show();
    }
    private void checkAppUpdate(AppUpdateDialog about) {
        Button check = about.getButton(AlertDialog.BUTTON_POSITIVE);
        check.setEnabled(false);
        about.setMessage(getString(R.string.checking_apk_update));
        about.setProgressState(true, 0, "");
        AppUpdate.check().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed() || !about.isShowing()) return;
            check.setEnabled(true);
            about.hideProgress();
            if (!task.isSuccessful()) {
                about.setMessage(getString(R.string.apk_update_check_failed));
                return;
            }
            AppUpdate update = task.getResult();
            if (update == null) {
                about.setMessage(getString(R.string.apk_update_unpublished));
                return;
            }
            if (!update.isNewerThanInstalled()) {
                about.setMessage(getString(R.string.apk_up_to_date));
                return;
            }
            appUpdates.download(update);
            about.dismiss();
        });
    }
    String installedApkDetails() {
        return BuildConfig.VERSION_NAME;
    }
    private String installedApkSummary() {
        long bytes = new java.io.File(getApplicationInfo().sourceDir).length();
        String size = android.text.format.Formatter.formatShortFileSize(this, bytes);
        return getString(R.string.installed_apk_summary, BuildConfig.VERSION_NAME, size,
                formatApkDate(AppUpdate.buildTimeMillis(BuildConfig.VERSION_CODE)));
    }
    String availableApkDetails(AppUpdate update) {
        return update.versionName + " · " + android.text.format.Formatter.formatShortFileSize(this, update.size);
    }
    private String formatApkDate(long millis) {
        java.util.Date date = new java.util.Date(millis);
        return android.text.format.DateFormat.getDateFormat(this).format(date) + " "
                + android.text.format.DateFormat.getTimeFormat(this).format(date);
    }
    @Override protected void onHistoryRetentionChanged() {
        if (home && profileLoaded && tab == 0 && retentionRefresh != null) retentionRefresh.run();
    }
    private void chooseHistoryDuration() {
        MessageTimerDialog.show(this, null, HistoryRetention.HOURS[HistoryRetention.selected(this)], () -> showHome(2));
    }
    private void listenDefaultTimer() {
        if (Backend.uid().isEmpty()) return;
        if (defaultTimerListener != null && defaultTimerOwner.equals(Backend.uid())) return;
        stopDefaultTimer(); String owner = Backend.uid(); defaultTimerOwner = owner;
        defaultTimerListener = Backend.db().collection("messageDefaults").document(owner).addSnapshotListener((doc, error) -> {
            if (!owner.equals(Backend.uid()) || error != null || doc == null || (!doc.exists() && doc.getMetadata().isFromCache())) return;
            int before = HistoryRetention.selected(this); Long hours = doc.getLong("hours");
            HistoryRetention.saveDefault(this, hours == null ? 0 : hours);
            if (before != HistoryRetention.selected(this) && home && tab == 2) showHome(2);
        });
    }
    private void stopDefaultTimer() {
        if (defaultTimerListener != null) { defaultTimerListener.remove(); defaultTimerListener = null; }
        defaultTimerOwner = "";
    }
    private void chooseColor() {
        AppearanceColorPicker.show(this);
    }
    private void chooseTextSize() {
        SettingsPage page = new SettingsPage(this, R.string.text_size);
        int initialPercent = Appearance.percent(this);
        LinearLayout content = Ui.column(this);
        LinearLayout controls = Ui.section(this); content.addView(controls);
        LinearLayout summary = Ui.row(this);
        summary.addView(Ui.text(this, getString(R.string.settings_text_scale), 14, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = Ui.text(this, getString(R.string.text_size_value, Appearance.percent(this)), 22, true);
        value.setTextColor(Appearance.primary(this)); summary.addView(value); controls.addView(summary); Ui.gap(controls, 16);
        android.widget.SeekBar slider = new android.widget.SeekBar(this); slider.setMax(80); slider.setProgress(Appearance.percent(this) - 70);
        slider.setContentDescription(getString(R.string.text_size));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(this)));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(this))); controls.addView(slider);
        LinearLayout limits = Ui.row(this);
        limits.addView(Ui.text(this, getString(R.string.text_size_value, 70), 12, false), new LinearLayout.LayoutParams(0, -2, 1));
        limits.addView(Ui.text(this, getString(R.string.text_size_value, 150), 12, false)); controls.addView(limits);
        LinearLayout sample = Ui.section(this); content.addView(sample);
        sample.addView(Ui.text(this, getString(R.string.settings_preview), 13, true)); Ui.gap(sample, 16);
        TextView preview = Ui.text(this, getString(R.string.text_size_preview), 16, false);
        preview.setTextColor(getColor(R.color.text_primary));
        preview.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        preview.setBackground(Ui.shape(this, R.color.action_green_soft, 16)); sample.addView(preview);
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                value.setText(getString(R.string.text_size_value, progress + 70));
                preview.setTextSize(16f * (progress + 70) / initialPercent);
                if (fromUser) Appearance.prefs(MainActivity.this).edit().putInt("text", progress + 70).apply();
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        page.body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        page.setOnDismissListener(ignored -> {
            if (Appearance.percent(this) != initialPercent) SettingsPage.recreateAfterExit(this);
        });
        page.show();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (profileLoaded) openNotificationIntent(intent); }
    private void openNotificationIntent(Intent intent) {
        String id = intent.getStringExtra("chatId"), recipient = intent.getStringExtra("recipientId"); intent.removeExtra("chatId"); intent.removeExtra("recipientId");
        if (id != null && id.matches("[a-f0-9]{64}") && Backend.uid().equals(recipient)) ChatActivity.open(this, id, getString(R.string.app_name));
    }
}
