package br.com.falai;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChatActivity extends FalaiActivity {
    private static final String[] REACTIONS = {"👍", "❤️", "😂", "😮", "😢", "🙏"};
    private static final int[] REACTION_NAMES = {R.string.reaction_like, R.string.reaction_love,
            R.string.reaction_laugh, R.string.reaction_surprised, R.string.reaction_sad, R.string.reaction_thanks};
    static volatile String visibleChat;
    private String chatId, name, otherUid = "";
    private ChatViewModel model;
    private EditText composer;
    private LinearLayout composerRow, retryRow, replyPreview;
    private LinearLayout emojiPanelHost;
    private android.widget.ImageButton emojiButton;
    private TextView status, title, searchSummary;
    private EditText messageSearch;
    private String messageQuery = "";
    private ChatPresence presence;
    private AvatarView avatar;
    private android.widget.ImageView timerIndicator;
    private RetentionNoticeController retentionNotice;
    private Button older;
    private RecyclerView messages;
    private LinearLayoutManager layoutManager;
    private MessageAdapter adapter;
    private ActivityResultLauncher<String> photoPicker;
    private ListenerRegistration listener;
    private ListenerRegistration chatListener;
    private final Map<String, DocumentSnapshot> loaded = new HashMap<>();
    private DocumentSnapshot oldest;
    private boolean loadingOlder, historyExhausted;
    private boolean chatReady;
    private boolean clearingChat;
    private com.google.firebase.Timestamp clearedBefore;
    private long localHistoryCutoff;
    private int timerHours;
    private DocumentSnapshot historyCursor;
    private DocumentSnapshot liveHistoryEnd;
    private final java.util.Set<String> reacting = new java.util.HashSet<>();
    private final java.util.Set<String> downloadingPhotos = new java.util.HashSet<>();
    private com.google.firebase.Timestamp timerFormatSince, listenerFormatSince;
    private final java.util.Set<String> deliveryQueued = new java.util.HashSet<>(), readQueued = new java.util.HashSet<>();
    static void open(Context context, String id, String name) {
        context.startActivity(new Intent(context, ChatActivity.class).putExtra("chatId", id).putExtra("name", name));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && model != null) model.preparePhoto(uri);
        });
        chatId = getIntent().getStringExtra("chatId"); name = getIntent().getStringExtra("name");
        if (!Backend.ready() || Backend.uid().isEmpty() || chatId == null || !chatId.matches("[a-f0-9]{64}")) { finish(); return; }
        if (name == null) name = getString(R.string.app_name);
        model = new ViewModelProvider(this).get(ChatViewModel.class);
        boolean freshModel = model.chatId == null; model.init(chatId);
        if (freshModel && state != null) {
            model.draft = state.getString("draft", ""); model.pendingId = state.getString("pendingId");
            model.pendingText = state.getString("pendingText"); model.pendingKind = state.getString("pendingKind"); model.failed = model.pendingId != null;
            model.reply = state.getBundle("reply");
        }
        LinearLayout root = Ui.root(this);
        root.setClipToPadding(false);
        NavigationSurface.attachCard(root);
        LinearLayout header = Ui.row(this); header.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        android.widget.ImageButton back = headerIcon(R.drawable.ic_back, R.string.back, v -> leave());
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 48)));
        FrameLayout portrait = new FrameLayout(this);
        avatar = new AvatarView(this); avatar.load("", name); avatar.setContentDescription(getString(R.string.profile)); avatar.setOnClickListener(v -> contactInfo());
        portrait.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        timerIndicator = new android.widget.ImageView(this); timerIndicator.setImageResource(R.drawable.ic_history_duration);
        timerIndicator.setImageTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(this)));
        timerIndicator.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        timerIndicator.setBackground(Ui.shape(this, R.color.background, 10));
        timerIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); timerIndicator.setVisibility(View.GONE);
        portrait.addView(timerIndicator, new FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.BOTTOM | Gravity.END));
        header.addView(portrait, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        title = new ProfileNameView(this); title.setText(name); title.setTextSize(20); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setTextColor(getColor(R.color.text_primary)); title.setPadding(Ui.dp(this, 10), 0, 0, 0); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setOnClickListener(v -> contactInfo()); title.setFocusable(true);
        LinearLayout heading = Ui.column(this); heading.addView(title);
        TextView availability = Ui.text(this, "", 12, false); availability.setPadding(Ui.dp(this, 10), 0, 0, 0); heading.addView(availability);
        presence = new ChatPresence(this, chatId, availability);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(headerIcon(R.drawable.ic_more, R.string.chat_options, this::showChatOptions));
        header.setBackground(new SoftGlowBackground(this));
        root.addView(header);
        // Cover the entire header, including the status bar and side insets.
        header.getViewTreeObserver().addOnPreDrawListener(() -> {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) header.getLayoutParams();
            int left = root.getPaddingLeft(), right = root.getPaddingRight(), top = root.getPaddingTop();
            if (params.leftMargin == -left && params.rightMargin == -right && params.topMargin == -top) return true;
            params.leftMargin = -left; params.rightMargin = -right; params.topMargin = -top;
            int side = getResources().getDimensionPixelSize(R.dimen.navigation_side_padding);
            header.setPadding(Math.max(0, left - side), top + Ui.dp(this, 12), Math.max(0, right - side), Ui.dp(this, 12));
            header.setLayoutParams(params); return false;
        });
        header.post(() -> {
            android.graphics.Rect touch = new android.graphics.Rect(); back.getHitRect(touch);
            touch.inset(-Ui.dp(this, 4), 0);
            header.setTouchDelegate(new android.view.TouchDelegate(touch, back));
        });
        messageSearch = (EditText) getLayoutInflater().inflate(R.layout.view_inline_search, root, false);
        messageSearch.setHint(R.string.search_messages);
        messageSearch.setVisibility(View.GONE);
        root.addView(messageSearch);
        root.setFocusableInTouchMode(true); root.requestFocus();
        messageSearch.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(messageSearch.getWindowToken(), 0);
            root.requestFocus(); return true;
        });
        searchSummary = Ui.text(this, "", 12, false); searchSummary.setVisibility(View.GONE); root.addView(searchSummary);
        older = Ui.button(this, R.string.older_messages, v -> loadOlder()); older.setVisibility(View.GONE); root.addView(older);
        messages = new RecyclerView(this); layoutManager = new LinearLayoutManager(this); layoutManager.setStackFromEnd(true);
        messages.setLayoutManager(layoutManager); adapter = new MessageAdapter(Backend.uid(), this::messageActions,
                loaded::get, () -> name, this::jumpToMessage, this::openPhoto); messages.setAdapter(adapter);
        messages.addOnItemTouchListener(new SwipeToReply(messages, adapter, this::canReply, this::beginReply));
        retentionNotice = new RetentionNoticeController(this, notices -> {
            boolean bottom = layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3;
            adapter.setNotices(notices);
            if (bottom) messages.post(() -> { if (adapter.getItemCount() > 0) messages.scrollToPosition(adapter.getItemCount() - 1); });
            messages.post(this::acknowledgeVisible);
        }, this::chooseChatTimer);
        messages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) { acknowledgeVisible(); }
        });
        root.addView(messages, new LinearLayout.LayoutParams(-1, 0, 1));
        status = Ui.text(this, getString(R.string.loading), 13, false); status.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6)); root.addView(status);
        retryRow = Ui.row(this);
        retryRow.addView(Ui.button(this, R.string.retry, v -> model.retry()), new LinearLayout.LayoutParams(0, -2, 1));
        retryRow.addView(Ui.button(this, R.string.discard, v -> model.discard()), new LinearLayout.LayoutParams(0, -2, 1));
        retryRow.setVisibility(View.GONE); root.addView(retryRow);
        replyPreview = Ui.row(this);
        replyPreview.setGravity(Gravity.CENTER_VERTICAL);
        replyPreview.setVisibility(View.GONE);
        root.addView(replyPreview);
        composerRow = Ui.row(this); composerRow.setGravity(Gravity.BOTTOM); composerRow.setBaselineAligned(false);
        composerRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8));
        LinearLayout inputBar = Ui.row(this); inputBar.setGravity(Gravity.BOTTOM); inputBar.setBaselineAligned(false);
        inputBar.setBackground(Ui.shape(this, R.color.card, 24));
        composerRow.addView(inputBar, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.ImageButton photoButton = Ui.icon(this, R.drawable.ic_photo, R.string.send_photo, v -> choosePhoto());
        photoButton.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary)));
        photoButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Appearance.ripple(this)),
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT), null));
        inputBar.addView(photoButton);
        emojiButton = Ui.icon(this, R.drawable.ic_emoji, R.string.emoji, v -> emoji());
        emojiButton.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary)));
        emojiButton.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(this)), Ui.shape(this, R.color.card, 24), null));
        inputBar.addView(emojiButton);
        composer = Ui.input(this, R.string.message_hint); composer.setSingleLine(false); composer.setMaxLines(5);
        composer.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setBackgroundColor(android.graphics.Color.TRANSPARENT); composer.setMinHeight(Ui.dp(this, 48));
        composer.setGravity(Gravity.TOP | Gravity.START); composer.setPadding(0, Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        composer.setText(model.draft); inputBar.addView(composer, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.ImageButton sendButton = Ui.icon(this, R.drawable.ic_send, R.string.send, v -> {
            model.draft = composer.getText().toString();
            if (model.draft.trim().isEmpty()) return;
            if (model.draft.trim().length() > 4000) { composer.setError(getString(R.string.message_too_long)); return; }
            presence.stopTyping(); model.sendText();
        });
        sendButton.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.background)));
        sendButton.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33000000), Ui.shape(this, R.color.action_green, 24), null));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)); sendParams.leftMargin = Ui.dp(this, 8);
        composerRow.addView(sendButton, sendParams); root.addView(composerRow);
        emojiPanelHost = Ui.column(this); emojiPanelHost.setVisibility(View.GONE);
        root.addView(emojiPanelHost, new LinearLayout.LayoutParams(-1, -2));
        composer.setOnClickListener(view -> closeEmojiPanel());
        composer.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { model.draft = s.toString(); presence.changed(s.length() > 0); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        model.changes.observe(this, ignored -> updateSendState());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
        EditText search = messageSearch;
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { messageQuery = s.toString(); renderMessages(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        if (state != null) {
            search.setText(state.getString("messageQuery", ""));
            if (state.getBoolean("messageSearchOpen", false) || !messageQuery.isEmpty()) messageSearch.setVisibility(View.VISIBLE);
        }
    }
    @Override protected void onStart() {
        super.onStart(); if (model == null) return;
        loadingOlder = false; older.setEnabled(true);
        if (Backend.uid().isEmpty()) { finish(); return; }
        chatReady = false;
        chatListener = Backend.db().collection("chats").document(chatId).addSnapshotListener(this, MetadataChanges.INCLUDE, (doc, error) -> {
            if (error != null || doc == null || (!doc.exists() && !doc.getMetadata().isFromCache())) {
                showLoadError("Conversation metadata", error);
                return;
            }
            if (!doc.exists()) return;
            @SuppressWarnings("unchecked") List<String> members = (List<String>) doc.get("members");
            if (members == null || !members.contains(Backend.uid())) { showLoadError("Conversation membership", null); return; }
            com.google.firebase.Timestamp previousCutoff = clearedBefore;
            boolean initial = !chatReady;
            clearedBefore = (com.google.firebase.Timestamp) doc.get("clearedBefore." + Backend.uid()); chatReady = true;
            Long hours = doc.getLong("timerHours"); timerHours = hours == null ? 0 : hours.intValue();
            timerIndicator.setVisibility(timerHours > 0 ? View.VISIBLE : View.GONE);
            timerFormatSince = doc.getTimestamp("timerFormatSince");
            if (listener == null || !java.util.Objects.equals(listenerFormatSince, timerFormatSince)) startMessageListener();
            for (String member : members) if (!member.equals(Backend.uid())) { otherUid = member; avatar.load(member, name); presence.listen(member); }
            @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) doc.get("names");
            String fallbackName = names != null && names.get(otherUid) != null ? names.get(otherUid) : name;
            retentionNotice.bind(doc, name);
            ((ProfileNameView) title).bind(otherUid, fallbackName, resolved -> {
                name = resolved; avatar.load(otherUid, resolved); retentionNotice.setContactName(resolved);
            });
            if (initial || !java.util.Objects.equals(previousCutoff, clearedBefore)) {
                merge(java.util.Collections.emptyList());
            }
        });
    }
    private void startMessageListener() {
        if (listener != null) listener.remove(); listenerFormatSince = timerFormatSince;
        Query history = liveHistoryEnd == null ? query().limit(50) : query().endAt(liveHistoryEnd);
        listener = history.addSnapshotListener(this, (snapshot, error) -> {
            if (error != null) { showLoadError("Message history", error); return; }
            if (snapshot == null) return;
            boolean bottom = loaded.isEmpty() || layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3;
            merge(snapshot.getDocuments());
            if (bottom && adapter.getItemCount() > 0) messages.scrollToPosition(adapter.getItemCount() - 1);
            older.setVisibility(!historyExhausted && (snapshot.size() >= 50 || loaded.size() > 50) ? View.VISIBLE : View.GONE);
            if (!model.busy && !model.failed) { status.setText(R.string.new_conversation); status.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE); }
        });
    }
    private void showLoadError(String source, com.google.firebase.firestore.FirebaseFirestoreException error) {
        if (error != null) android.util.Log.w("ChatActivity", source + ": " + error.getCode());
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.error_chat_loading);
    }
    @Override protected void onResume() { super.onResume(); visibleChat = chatId; if (presence != null) presence.resume(); if (messages != null) messages.post(this::acknowledgeVisible); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && messages != null) messages.post(this::acknowledgeVisible); }
    @Override protected void onPause() { visibleChat = null; if (presence != null) presence.pause(); super.onPause(); }
    @Override protected void onStop() { if (listener != null) { listener.remove(); listener = null; } if (chatListener != null) { chatListener.remove(); chatListener = null; } super.onStop(); }
    private Query query() {
        Query query = Backend.db().collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.DESCENDING);
        if (timerFormatSince != null) query = query.where(com.google.firebase.firestore.Filter.or(
            com.google.firebase.firestore.Filter.lessThan("createdAt", timerFormatSince),
            com.google.firebase.firestore.Filter.equalTo("expired", false)));
        long cutoff = HistoryRetention.cutoff(this);
        return cutoff > 0 ? query.whereGreaterThan("createdAt", new com.google.firebase.Timestamp(new java.util.Date(cutoff))) : query;
    }
    private void contactInfo() {
        if (otherUid.isEmpty()) { toast(R.string.loading); return; }
        ContactProfileDialog.show(this, otherUid, name);
    }
    private void chooseChatTimer() {
        if (!chatReady) { toast(R.string.loading); return; }
        MessageTimerDialog.show(this, chatId, timerHours, () -> { });
    }
    private android.widget.ImageButton headerIcon(int drawable, int description, View.OnClickListener action) {
        android.widget.ImageButton button = Ui.icon(this, drawable, description, action);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Appearance.ripple(this)), null,
                Ui.shape(this, R.color.card, 24)));
        button.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary)));
        return button;
    }
    private void showChatOptions(View anchor) {
        if (!chatReady) { toast(R.string.loading); return; }
        androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 4, 0, R.string.search_hint);
        menu.getMenu().add(0, 1, 1, R.string.chat_timer_title);
        menu.getMenu().add(0, 2, 2, R.string.nudge_action).setEnabled(!model.nudgeBusy);
        menu.getMenu().add(0, 3, 3, R.string.clear_chat).setEnabled(!clearingChat);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) chooseChatTimer();
            else if (item.getItemId() == 2) new AlertDialog.Builder(this)
                    .setTitle(R.string.nudge_action).setMessage(getString(R.string.nudge_question, name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.nudge_action, (dialog, which) -> model.sendNudge()).show();
            else if (item.getItemId() == 3) confirmClearChat();
            else if (item.getItemId() == 4) {
                messageSearch.setVisibility(View.VISIBLE); messageSearch.requestFocus();
                messageSearch.post(() -> {
                    if (!isFinishing() && !isDestroyed() && messageSearch.getVisibility() == View.VISIBLE)
                        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                                .showSoftInput(messageSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
            }
            else return false;
            return true;
        });
        menu.show();
    }
    private void confirmClearChat() {
        if (clearingChat) return;
        new AlertDialog.Builder(this).setTitle(R.string.clear_chat).setMessage(R.string.clear_chat_question)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_chat, (dialog, which) -> {
                    if (clearingChat) return;
                    clearingChat = true;
                    String owner = Backend.uid();
                    Backend.call("clearConversation", Backend.data("chatId", chatId)).addOnCompleteListener(result -> {
                        clearingChat = false;
                        if (isFinishing() || isDestroyed() || !owner.equals(Backend.uid())) return;
                        if (result.isSuccessful()) { for (DocumentSnapshot message : loaded.values()) if ("image".equals(message.getString("kind"))) ChatMedia.delete(this, message.getString("mediaId")); toast(R.string.chat_cleared); }
                        else Toast.makeText(this, Backend.error(this, result.getException()), Toast.LENGTH_LONG).show();
                    });
                }).show();
    }
    private void merge(List<DocumentSnapshot> docs) {
        localHistoryCutoff = HistoryRetention.cutoff(this);
        for (DocumentSnapshot doc : docs) if (doc.getTimestamp("createdAt") != null && (historyCursor == null
            || doc.getTimestamp("createdAt").compareTo(historyCursor.getTimestamp("createdAt")) < 0
            || (doc.getTimestamp("createdAt").equals(historyCursor.getTimestamp("createdAt")) && doc.getId().compareTo(historyCursor.getId()) < 0))) historyCursor = doc;
        if (clearedBefore != null) for (DocumentSnapshot doc : docs) if (doc.getTimestamp("createdAt") != null && doc.getTimestamp("createdAt").compareTo(clearedBefore) <= 0) historyExhausted = true;
        List<String> delivered = new ArrayList<>();
        for (DocumentSnapshot doc : docs) if (!Backend.uid().equals(doc.getString("senderId")) && doc.getTimestamp("deliveredAt") == null && deliveryQueued.add(doc.getId())) delivered.add(doc.getId());
        ReceiptWorker.schedule(chatId, delivered, false);
        for (DocumentSnapshot doc : docs) {
            if ("image".equals(doc.getString("kind")) && (HistoryRetention.expired(doc)
                    || Boolean.TRUE.equals(doc.getBoolean("deletedForAll"))
                    || (doc.get("hiddenFor") instanceof List && ((List<?>) doc.get("hiddenFor")).contains(Backend.uid())))) {
                ChatMedia.delete(this, doc.getString("mediaId"));
            }
            if ("image".equals(doc.getString("kind")) && visible(doc)
                    && !Backend.uid().equals(doc.getString("senderId"))) {
                downloadPhoto(doc, false);
            }
            if (visible(doc)) loaded.put(doc.getId(), doc); else loaded.remove(doc.getId());
        }
        loaded.values().removeIf(doc -> !visible(doc));
        List<DocumentSnapshot> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparing((DocumentSnapshot doc) -> doc.getTimestamp("createdAt")).thenComparing(DocumentSnapshot::getId));
        oldest = historyCursor; renderMessages();
        messages.post(this::acknowledgeVisible);
    }
    private void renderMessages() {
        List<DocumentSnapshot> result = new ArrayList<>(loaded.values());
        String needle = searchable(messageQuery.trim());
        result.removeIf(doc -> HistoryRetention.expired(doc) || (!needle.isEmpty() && (Boolean.TRUE.equals(doc.getBoolean("deletedForAll")) || !searchable(doc.getString("text")).contains(needle))));
        result.sort(Comparator.comparing((DocumentSnapshot doc) -> doc.getTimestamp("createdAt")).thenComparing(DocumentSnapshot::getId));
        adapter.submit(result, needle.isEmpty()); searchSummary.setVisibility(needle.isEmpty() ? View.GONE : View.VISIBLE);
        searchSummary.setText(getString(R.string.search_loaded_results, result.size()));
        messages.post(this::acknowledgeVisible);
    }
    private static String searchable(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }
    @Override protected void onDestroy() {
        if (presence != null) presence.pause();
        super.onDestroy();
    }
    private boolean visible(DocumentSnapshot doc) {
        @SuppressWarnings("unchecked") List<String> hidden = (List<String>) doc.get("hiddenFor");
        com.google.firebase.Timestamp created = doc.getTimestamp("createdAt");
        return (hidden == null || !hidden.contains(Backend.uid()))
            && !HistoryRetention.expired(doc)
            && (localHistoryCutoff == 0 || (created != null && created.toDate().getTime() > localHistoryCutoff))
            && (clearedBefore == null || (created != null && created.compareTo(clearedBefore) > 0));
    }
    @Override protected void onHistoryRetentionChanged() {
        if (model != null && messages != null && chatReady) {
            merge(java.util.Collections.emptyList());
            if (oldest == null) older.setVisibility(View.GONE);
            updateSendState();
        }
    }
    private void messageActions(DocumentSnapshot doc) {
        if (doc.getId().equals(model.pendingId)) { toast(model.busy ? R.string.sending : R.string.error_send); return; }
        if (HistoryRetention.expired(doc)) return;
        boolean mine = Backend.uid().equals(doc.getString("senderId"));
        boolean deleted = Boolean.TRUE.equals(doc.getBoolean("deletedForAll"));
        boolean nonEditable = "nudge".equals(doc.getString("kind")) || "image".equals(doc.getString("kind")) || GifCatalog.index(doc.getString("text")) >= 0;
        if (!mine && deleted) return;
        AlertDialog.Builder menuBuilder = new AlertDialog.Builder(this);
        LinearLayout picker = Ui.row(this);
        if (!deleted) {
            picker.setGravity(Gravity.CENTER);
            picker.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
            menuBuilder.setCustomTitle(picker);
        }
        if (!mine) menuBuilder.setNegativeButton(R.string.cancel, null);
        String currentReaction = currentReaction(doc);
        if (!deleted && currentReaction != null) {
            menuBuilder.setNeutralButton(R.string.remove_reaction, (menu, selected) -> saveReaction(doc, null));
        }
        java.util.List<String> choices = new ArrayList<>();
        if (!deleted) choices.add(getString(R.string.reply_message));
        if (mine) {
            if (!deleted && !nonEditable) choices.add(getString(R.string.edit_message));
            if (!deleted) choices.add(getString(R.string.delete_for_all));
            choices.add(getString(R.string.delete_for_me));
            choices.add(getString(R.string.cancel));
        }
        menuBuilder.setItems(choices.toArray(new String[0]), (menu, item) -> {
            if (!deleted && item == 0) { beginReply(doc); return; }
            if (item == choices.size() - 1) return;
            int selected = deleted ? item : item - 1;
            if (!deleted && !nonEditable && selected == 0) {
                EditText field = Ui.input(this, R.string.message_hint); field.setSingleLine(false); field.setText(doc.getString("text"));
                AlertDialog edit = new AlertDialog.Builder(this).setTitle(R.string.edit_message).setView(field).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
                edit.setOnShowListener(ignored -> edit.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String text = field.getText().toString().trim(); if (text.isEmpty() || text.length() > 4000) { field.setError(getString(R.string.message_too_long)); return; }
                    view.setEnabled(false); Backend.call("editMessage", Backend.data("chatId", chatId, "messageId", doc.getId(), "text", text)).addOnCompleteListener(this, task -> {
                        view.setEnabled(true); if (task.isSuccessful()) edit.dismiss(); else Toast.makeText(this, Backend.error(this, task.getException()), Toast.LENGTH_LONG).show();
                    });
                })); edit.show();
            } else {
                String scope = !deleted && selected == (nonEditable ? 0 : 1) ? "all" : "self";
                new AlertDialog.Builder(this).setMessage(R.string.delete_message_question).setNegativeButton(R.string.cancel, null).setPositiveButton("all".equals(scope) ? R.string.delete_for_all : R.string.delete_for_me, (confirm, button) ->
                    Backend.call("deleteMessage", Backend.data("chatId", chatId, "messageId", doc.getId(), "scope", scope)).addOnCompleteListener(this, task -> {
                        if (task.isSuccessful() && "image".equals(doc.getString("kind"))) ChatMedia.delete(this, doc.getString("mediaId"));
                        else if (!task.isSuccessful()) Toast.makeText(this, Backend.error(this, task.getException()), Toast.LENGTH_LONG).show();
                    })).show();
            }
        });
        AlertDialog dialog = menuBuilder.create();
        if (!deleted) populateReactionPicker(picker, doc, currentReaction, dialog);
        dialog.show();
    }
    private void beginReply(DocumentSnapshot doc) {
        if (!canReply(doc)) return;
        Bundle reply = new Bundle();
        reply.putString("id", doc.getId());
        reply.putString("sender", doc.getString("senderId"));
        reply.putString("kind", doc.getString("kind"));
        if (doc.getTimestamp("expiresAt") == null) reply.putString("text", doc.getString("text"));
        model.reply = reply;
        model.changed();
        composer.requestFocus();
        composer.post(() -> ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(composer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT));
    }
    private boolean canReply(DocumentSnapshot doc) {
        return model.pendingId == null && !Boolean.TRUE.equals(doc.getBoolean("deletedForAll"))
                && !HistoryRetention.expired(doc);
    }
    private void renderReplyPreview() {
        replyPreview.removeAllViews();
        Bundle reply = model.reply;
        replyPreview.setVisibility(reply == null ? View.GONE : View.VISIBLE);
        if (reply == null) return;
        String id = reply.getString("id");
        DocumentSnapshot original = loaded.get(id);
        String text = original == null ? reply.getString("text") : original.getString("text");
        boolean mine = Backend.uid().equals(reply.getString("sender"));
        LinearLayout quote = MessageAdapter.quoteView(this, mine ? getString(R.string.reply_you) : name,
                MessageAdapter.quoteText(this, reply.getString("kind"), text, mine, original));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(Ui.dp(this, 4));
        replyPreview.addView(quote, params);
        TextView close = Ui.text(this, "×", 24, true);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(getString(R.string.cancel_reply));
        close.setEnabled(model.pendingId == null);
        close.setOnClickListener(view -> { model.reply = null; model.changed(); });
        replyPreview.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    }
    private void jumpToMessage(String id) {
        if (adapter.scrollToMessage(messages, id)) return;
        if (loaded.containsKey(id) && !messageQuery.isEmpty() && messageSearch != null) {
            messageSearch.setText("");
            if (adapter.scrollToMessage(messages, id)) return;
        }
        toast(older.getVisibility() == View.VISIBLE ? R.string.reply_load_older : R.string.reply_unavailable);
    }
    private String currentReaction(DocumentSnapshot doc) {
        Object values = doc.get("reactions");
        Object current = values instanceof java.util.Map ? ((java.util.Map<?, ?>) values).get(Backend.uid()) : null;
        return current instanceof String ? (String) current : null;
    }
    private void populateReactionPicker(LinearLayout picker, DocumentSnapshot doc, String current, AlertDialog dialog) {
        for (int index = 0; index < REACTIONS.length; index++) {
            String emoji = REACTIONS[index];
            boolean selected = emoji.equals(current);
            TextView option = Ui.text(this, emoji, 23, false);
            option.setGravity(Gravity.CENTER);
            option.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Appearance.ripple(this)),
                    Ui.shape(this, selected ? R.color.action_green_soft : R.color.card, 24), null));
            option.setSelected(selected);
            option.setFocusable(true);
            option.setContentDescription(getString(REACTION_NAMES[index]));
            option.setOnClickListener(view -> {
                dialog.dismiss();
                saveReaction(doc, selected ? null : emoji);
            });
            picker.addView(option, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        }
        TextView more = Ui.text(this, "+", 23, true);
        more.setGravity(Gravity.CENTER);
        more.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Appearance.ripple(this)),
                Ui.shape(this, R.color.action_green_soft, 24), null));
        more.setFocusable(true);
        more.setContentDescription(getString(R.string.more_reactions));
        more.setOnClickListener(view -> {
            dialog.dismiss();
            EmojiPicker.show(this, emoji -> saveReaction(doc, emoji.equals(current) ? null : emoji));
        });
        picker.addView(more, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    }
    private void saveReaction(DocumentSnapshot doc, String emoji) {
        if (!reacting.add(doc.getId())) return;
        Backend.call("setMessageReaction", Backend.data("chatId", chatId, "messageId", doc.getId(),
                "emoji", emoji == null ? org.json.JSONObject.NULL : emoji)).addOnCompleteListener(task -> {
            reacting.remove(doc.getId());
            if (!task.isSuccessful() && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                Toast.makeText(this, Backend.error(this, task.getException()), Toast.LENGTH_LONG).show();
            }
        });
    }
    private void acknowledgeVisible() {
        if (!chatId.equals(visibleChat) || !hasWindowFocus() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        List<String> read = new ArrayList<>();
        int first = layoutManager.findFirstVisibleItemPosition(), last = layoutManager.findLastVisibleItemPosition();
        for (int position = Math.max(0, first); position <= last; position++) {
            DocumentSnapshot doc = adapter.at(position);
            if (doc != null && !HistoryRetention.expired(doc) && !Backend.uid().equals(doc.getString("senderId")) && doc.getTimestamp("readAt") == null && readQueued.add(doc.getId())) read.add(doc.getId());
        }
        ReceiptWorker.schedule(chatId, read, true);
    }
    private void loadOlder() {
        if (loadingOlder || oldest == null) return;
        loadingOlder = true; older.setEnabled(false);
        int before = adapter.getItemCount(), position = layoutManager.findFirstVisibleItemPosition();
        query().startAfter(oldest).limit(50).get().addOnCompleteListener(this, task -> {
            loadingOlder = false; older.setEnabled(true);
            if (!task.isSuccessful()) { toast(R.string.error_connection); return; }
            merge(task.getResult().getDocuments());
            if (historyCursor != null && !java.util.Objects.equals(historyCursor, liveHistoryEnd)) {
                liveHistoryEnd = historyCursor;
                startMessageListener();
            }
            layoutManager.scrollToPositionWithOffset(Math.max(0, position + adapter.getItemCount() - before), 0);
            historyExhausted = task.getResult().size() < 50; older.setVisibility(historyExhausted ? View.GONE : View.VISIBLE);
        });
    }
    private void updateSendState() {
        if (model.nudgeNotice != 0) { toast(model.nudgeNotice); model.nudgeNotice = 0; }
        Ui.enable(composerRow, !model.busy && model.pendingId == null); retryRow.setVisibility(model.failed ? View.VISIBLE : View.GONE);
        Ui.enable(emojiPanelHost, !model.busy && model.pendingId == null);
        if (!composer.getText().toString().equals(model.draft)) composer.setText(model.draft);
        renderReplyPreview();
        if (model.busy) { status.setVisibility(View.VISIBLE); status.setText(R.string.sending); }
        else if (model.failed) { status.setVisibility(View.VISIBLE); status.setText(model.sendError == 0 ? R.string.error_send : model.sendError); }
        else { status.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE); status.setText(R.string.new_conversation); }
    }
    private void choosePhoto() {
        if (!chatReady || model.busy || model.pendingId != null) { toast(R.string.photo_wait); return; }
        closeEmojiPanel();
        photoPicker.launch("image/*");
    }
    private void openPhoto(DocumentSnapshot doc) {
        String id = doc.getString("mediaId");
        if (id == null) return;
        java.io.File local = ChatMedia.file(this, id);
        if (local != null && local.isFile()) {
            android.graphics.Bitmap bitmap = ChatMedia.preview(this, id, 1600);
            if (bitmap == null) { local.delete(); openPhoto(doc); return; }
            android.widget.ImageView image = new android.widget.ImageView(this); image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true); image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            image.setMaxHeight(getResources().getDisplayMetrics().heightPixels - Ui.dp(this, 96));
            AlertDialog dialog = new AlertDialog.Builder(this).setView(image).setPositiveButton(R.string.close, null).create();
            dialog.setOnDismissListener(ignored -> bitmap.recycle()); dialog.show(); return;
        }
        if (Backend.uid().equals(doc.getString("senderId"))) { toast(R.string.photo_expired); return; }
        downloadPhoto(doc, true);
    }
    private void downloadPhoto(DocumentSnapshot doc, boolean showFeedback) {
        String id = doc.getString("mediaId");
        if (id == null || ChatMedia.file(this, id).isFile() || !downloadingPhotos.add(id)) return;
        if (showFeedback) toast(R.string.photo_downloading);
        String owner = Backend.uid();
        Backend.downloadChatMedia(chatId, id).onSuccessTask(bytes -> com.google.android.gms.tasks.Tasks.call(ContactsRepository.IO, () -> { ChatMedia.save(getApplicationContext(), id, bytes); return null; }))
                .onSuccessTask(ignored -> Backend.call("deleteChatMedia", Backend.data("chatId", chatId, "messageId", id)))
                .addOnCompleteListener(this, task -> {
                    downloadingPhotos.remove(id);
                    if (!owner.equals(Backend.uid()) || isFinishing() || isDestroyed()) return;
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();
                        int message = error instanceof Backend.ApiException && "not-found".equals(((Backend.ApiException) error).code)
                                ? R.string.photo_expired : R.string.photo_error;
                        if (showFeedback) Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show();
                    }
                    int position = adapter.findPosition(doc.getId()); if (position >= 0) adapter.notifyItemChanged(position);
                });
    }
    private void emoji() {
        if (emojiPanelHost.getVisibility() == View.VISIBLE) {
            closeEmojiPanel(); composer.requestFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(composer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            return;
        }
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(), 0);
        emojiPanelHost.removeAllViews();
        emojiPanelHost.addView(EmojiPicker.inline(this, value -> {
            int gif = GifCatalog.index(value);
            if (gif >= 0) {
                LinearLayout preview = Ui.column(this); preview.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
                preview.addView(new LocalGifView(this, gif), new LinearLayout.LayoutParams(-1, Ui.dp(this, 180)));
                new AlertDialog.Builder(this).setView(preview).setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.send, (dialog, which) -> { presence.stopTyping(); model.sendGif(value); }).show();
                return;
            }
            int start = Math.max(0, composer.getSelectionStart()), end = Math.max(start, composer.getSelectionEnd());
            composer.getText().replace(start, end, value);
        }, this::closeEmojiPanel));
        emojiPanelHost.setVisibility(View.VISIBLE);
        emojiButton.setImageResource(R.drawable.ic_keyboard); emojiButton.setContentDescription(getString(R.string.keyboard));
    }
    private void closeEmojiPanel() {
        if (emojiPanelHost == null || emojiPanelHost.getVisibility() != View.VISIBLE) return;
        emojiPanelHost.setVisibility(View.GONE); emojiPanelHost.removeAllViews();
        emojiButton.setImageResource(R.drawable.ic_emoji); emojiButton.setContentDescription(getString(R.string.emoji));
    }
    private void leave() {
        if (emojiPanelHost != null && emojiPanelHost.getVisibility() == View.VISIBLE) { closeEmojiPanel(); return; }
        if (messageSearch != null && messageSearch.getVisibility() == View.VISIBLE) {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(messageSearch.getWindowToken(), 0);
            messageSearch.setText(""); messageSearch.setVisibility(View.GONE); messageSearch.clearFocus();
            return;
        }
        if (model.busy) { toast(R.string.sending); return; }
        if (model.pendingId != null || !model.draft.trim().isEmpty()) {
            new AlertDialog.Builder(this).setMessage(R.string.draft_discard).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.discard, (dialog, which) -> { model.discard(); finish(); }).show();
        } else finish();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); if (model == null) return;
        out.putString("messageQuery", messageQuery); out.putString("draft", model.draft); out.putString("pendingId", model.pendingId); out.putString("pendingText", model.pendingText);
        out.putString("pendingKind", model.pendingKind);
        out.putBundle("reply", model.reply);
        out.putBoolean("messageSearchOpen", messageSearch != null && messageSearch.getVisibility() == View.VISIBLE);
    }
    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
