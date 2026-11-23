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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChatActivity extends FalaiActivity {
    static volatile String visibleChat;
    private String chatId, name, otherUid = "";
    private ChatViewModel model;
    private EditText composer;
    private LinearLayout composerRow, retryRow;
    private TextView status, title, searchSummary;
    private FloatingSearchController floatingSearch;
    private String messageQuery = "";
    private ChatPresence presence;
    private AvatarView avatar;
    private Button older;
    private RecyclerView messages;
    private LinearLayoutManager layoutManager;
    private MessageAdapter adapter;
    private ListenerRegistration listener;
    private ListenerRegistration chatListener;
    private final Map<String, DocumentSnapshot> loaded = new HashMap<>();
    private DocumentSnapshot oldest;
    private boolean loadingOlder, historyExhausted;
    private boolean chatReady;
    private List<DocumentSnapshot> pendingDocs;
    private com.google.firebase.Timestamp clearedBefore;
    private final java.util.Set<String> deliveryQueued = new java.util.HashSet<>(), readQueued = new java.util.HashSet<>();
    static void open(Context context, String id, String name) {
        context.startActivity(new Intent(context, ChatActivity.class).putExtra("chatId", id).putExtra("name", name));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        chatId = getIntent().getStringExtra("chatId"); name = getIntent().getStringExtra("name");
        if (!Backend.ready() || Backend.uid().isEmpty() || chatId == null || !chatId.matches("[a-f0-9]{64}")) { finish(); return; }
        if (name == null) name = getString(R.string.app_name);
        model = new ViewModelProvider(this).get(ChatViewModel.class);
        boolean freshModel = model.chatId == null; model.init(chatId);
        if (freshModel && state != null) {
            model.draft = state.getString("draft", ""); model.pendingId = state.getString("pendingId");
            model.pendingText = state.getString("pendingText"); model.failed = model.pendingId != null;
        }
        LinearLayout root = Ui.root(this);
        NavigationSurface.attachCard(root);
        LinearLayout header = Ui.row(this); header.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
        header.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, v -> leave()));
        avatar = new AvatarView(this); avatar.load("", name); avatar.setContentDescription(getString(R.string.profile)); avatar.setOnClickListener(v -> contactInfo()); header.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        title = Ui.text(this, name, 20, true); title.setPadding(Ui.dp(this, 14), 0, 0, 0); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setOnClickListener(v -> contactInfo()); title.setFocusable(true);
        LinearLayout heading = Ui.column(this); heading.addView(title);
        TextView availability = Ui.text(this, "", 12, false); availability.setPadding(Ui.dp(this, 14), 0, 0, 0); heading.addView(availability);
        presence = new ChatPresence(this, chatId, availability);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);
        searchSummary = Ui.text(this, "", 12, false); searchSummary.setVisibility(View.GONE); root.addView(searchSummary);
        older = Ui.button(this, R.string.older_messages, v -> loadOlder()); older.setVisibility(View.GONE); root.addView(older);
        messages = new RecyclerView(this); layoutManager = new LinearLayoutManager(this); layoutManager.setStackFromEnd(true);
        messages.setLayoutManager(layoutManager); adapter = new MessageAdapter(Backend.uid(), this::messageActions); messages.setAdapter(adapter);
        messages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) { acknowledgeVisible(); }
        });
        FrameLayout searchArea = new FrameLayout(this); searchArea.setId(R.id.floating_search_content_area);
        messages.setId(R.id.search_dismiss_surface); searchArea.addView(messages, new FrameLayout.LayoutParams(-1, -1));
        root.addView(searchArea, new LinearLayout.LayoutParams(-1, 0, 1));
        status = Ui.text(this, getString(R.string.loading), 13, false); status.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6)); root.addView(status);
        retryRow = Ui.row(this);
        retryRow.addView(Ui.button(this, R.string.retry, v -> model.retry()), new LinearLayout.LayoutParams(0, -2, 1));
        retryRow.addView(Ui.button(this, R.string.discard, v -> model.discard()), new LinearLayout.LayoutParams(0, -2, 1));
        retryRow.setVisibility(View.GONE); root.addView(retryRow);
        composerRow = Ui.row(this); composerRow.setGravity(Gravity.BOTTOM); composerRow.setBaselineAligned(false);
        composerRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8));
        LinearLayout inputBar = Ui.row(this); inputBar.setGravity(Gravity.BOTTOM); inputBar.setBaselineAligned(false);
        inputBar.setBackground(Ui.shape(this, R.color.card, 24));
        composerRow.addView(inputBar, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.ImageButton emojiButton = Ui.icon(this, R.drawable.ic_emoji, R.string.emoji, v -> emoji());
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
        composer.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { model.draft = s.toString(); presence.changed(s.length() > 0); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        model.changes.observe(this, ignored -> updateSendState());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
        View searchBar = getLayoutInflater().inflate(R.layout.view_floating_search, searchArea, false);
        searchBar.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(this)), Ui.shape(this, R.color.action_green_soft, 24), null));
        searchArea.addView(searchBar); floatingSearch = FloatingSearchController.attach(this, "messages", R.id.search_dismiss_surface);
        EditText search = floatingSearch.getInput(); search.setHint(R.string.search_messages);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { messageQuery = s.toString(); renderMessages(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        if (state != null) search.setText(state.getString("messageQuery", ""));
    }
    @Override protected void onStart() {
        super.onStart(); if (model == null) return;
        loadingOlder = false; older.setEnabled(true);
        if (Backend.uid().isEmpty()) { finish(); return; }
        chatReady = false; pendingDocs = null;
        chatListener = Backend.db().collection("chats").document(chatId).addSnapshotListener(this, (doc, error) -> {
            if (error != null || doc == null || !doc.exists()) { status.setVisibility(View.VISIBLE); status.setText(R.string.error_chat_loading); return; }
            com.google.firebase.Timestamp previousCutoff = clearedBefore;
            boolean initial = !chatReady;
            clearedBefore = (com.google.firebase.Timestamp) doc.get("clearedBefore." + Backend.uid()); chatReady = true;
            @SuppressWarnings("unchecked") List<String> members = (List<String>) doc.get("members");
            if (members != null) for (String member : members) if (!member.equals(Backend.uid())) { otherUid = member; avatar.load(member, name); presence.listen(member); }
            @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) doc.get("names");
            if (getString(R.string.app_name).equals(name) && names != null) {
                for (Map.Entry<String, String> entry : names.entrySet()) if (!entry.getKey().equals(Backend.uid())) { name = entry.getValue(); title.setText(name); }
            }
            if (pendingDocs != null) {
                merge(pendingDocs); older.setVisibility(!historyExhausted && pendingDocs.size() >= 50 && !loaded.isEmpty() ? View.VISIBLE : View.GONE);
                if (adapter.getItemCount() > 0) messages.scrollToPosition(adapter.getItemCount() - 1); pendingDocs = null;
                if (!model.busy && !model.failed) { status.setText(R.string.new_conversation); status.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE); }
            }
            else if (initial || !java.util.Objects.equals(previousCutoff, clearedBefore)) {
                merge(java.util.Collections.emptyList());
                if (!model.busy && !model.failed) { status.setText(R.string.new_conversation); status.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE); }
            }
        });
        listener = query().limit(50).addSnapshotListener(this, (snapshot, error) -> {
            if (error != null) { status.setVisibility(View.VISIBLE); status.setText(R.string.error_chat_loading); return; }
            if (snapshot == null) return;
            if (!chatReady) { pendingDocs = snapshot.getDocuments(); return; }
            boolean bottom = loaded.isEmpty() || layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3;
            merge(snapshot.getDocuments());
            if (bottom && adapter.getItemCount() > 0) messages.scrollToPosition(adapter.getItemCount() - 1);
            older.setVisibility(!historyExhausted && (snapshot.size() >= 50 || loaded.size() > 50) ? View.VISIBLE : View.GONE);
            if (!model.busy && !model.failed) { status.setText(R.string.new_conversation); status.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE); }
        });
    }
    @Override protected void onResume() { super.onResume(); visibleChat = chatId; if (presence != null) presence.resume(); if (messages != null) messages.post(this::acknowledgeVisible); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && messages != null) messages.post(this::acknowledgeVisible); }
    @Override protected void onPause() { visibleChat = null; if (presence != null) presence.pause(); if (floatingSearch != null) floatingSearch.collapse(false); super.onPause(); }
    @Override protected void onStop() { if (listener != null) { listener.remove(); listener = null; } if (chatListener != null) { chatListener.remove(); chatListener = null; } super.onStop(); }
    private Query query() {
        return Backend.db().collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.DESCENDING);
    }
    private void contactInfo() {
        if (otherUid.isEmpty()) { toast(R.string.loading); return; }
        LinearLayout content = Ui.column(this); content.setGravity(Gravity.CENTER_HORIZONTAL); content.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 16));
        AvatarView photo = new AvatarView(this); photo.load(otherUid, name); content.addView(photo, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));
        TextView contactName = Ui.text(this, name, 22, false); contactName.setGravity(Gravity.CENTER); contactName.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8)); content.addView(contactName);
        AboutTextView phrase = new AboutTextView(this); phrase.setTextSize(16); phrase.setTextColor(getColor(R.color.text_secondary)); phrase.setGravity(Gravity.CENTER);
        phrase.bind(otherUid, ""); content.addView(phrase, new LinearLayout.LayoutParams(-1, -2));
        BuildDateView buildDate = new BuildDateView(this, otherUid); buildDate.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buildDateParams = new LinearLayout.LayoutParams(-1, -2); buildDateParams.topMargin = Ui.dp(this, 16);
        content.addView(buildDate, buildDateParams);
        new AlertDialog.Builder(this).setView(content).setPositiveButton(android.R.string.ok, null).show();
    }
    private void merge(List<DocumentSnapshot> docs) {
        if (clearedBefore != null) for (DocumentSnapshot doc : docs) if (doc.getTimestamp("createdAt") != null && doc.getTimestamp("createdAt").compareTo(clearedBefore) <= 0) historyExhausted = true;
        List<String> delivered = new ArrayList<>();
        for (DocumentSnapshot doc : docs) if (!Backend.uid().equals(doc.getString("senderId")) && doc.getTimestamp("deliveredAt") == null && deliveryQueued.add(doc.getId())) delivered.add(doc.getId());
        ReceiptWorker.schedule(chatId, delivered, false);
        for (DocumentSnapshot doc : docs) { if (visible(doc)) loaded.put(doc.getId(), doc); else loaded.remove(doc.getId()); }
        loaded.values().removeIf(doc -> !visible(doc));
        List<DocumentSnapshot> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparing((DocumentSnapshot doc) -> doc.getTimestamp("createdAt")).thenComparing(DocumentSnapshot::getId));
        oldest = sorted.isEmpty() ? null : sorted.get(0); renderMessages();
        messages.post(this::acknowledgeVisible);
    }
    private void renderMessages() {
        List<DocumentSnapshot> result = new ArrayList<>(loaded.values());
        String needle = searchable(messageQuery.trim());
        result.removeIf(doc -> !needle.isEmpty() && (Boolean.TRUE.equals(doc.getBoolean("deletedForAll")) || !searchable(doc.getString("text")).contains(needle)));
        result.sort(Comparator.comparing((DocumentSnapshot doc) -> doc.getTimestamp("createdAt")).thenComparing(DocumentSnapshot::getId));
        adapter.submit(result); searchSummary.setVisibility(needle.isEmpty() ? View.GONE : View.VISIBLE);
        searchSummary.setText(getString(R.string.search_loaded_results, result.size()));
        messages.post(this::acknowledgeVisible);
    }
    private static String searchable(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }
    @Override protected void onDestroy() {
        if (floatingSearch != null) floatingSearch.dispose();
        if (presence != null) presence.pause();
        super.onDestroy();
    }
    private boolean visible(DocumentSnapshot doc) {
        @SuppressWarnings("unchecked") List<String> hidden = (List<String>) doc.get("hiddenFor");
        return (hidden == null || !hidden.contains(Backend.uid())) && (clearedBefore == null || (doc.getTimestamp("createdAt") != null && doc.getTimestamp("createdAt").compareTo(clearedBefore) > 0));
    }
    private void messageActions(DocumentSnapshot doc) {
        if (!Backend.uid().equals(doc.getString("senderId"))) return;
        if (doc.getId().equals(model.pendingId)) { toast(model.busy ? R.string.sending : R.string.error_send); return; }
        boolean deleted = Boolean.TRUE.equals(doc.getBoolean("deletedForAll"));
        String[] choices = deleted ? new String[]{getString(R.string.delete_for_me), getString(R.string.cancel)} : new String[]{getString(R.string.edit_message), getString(R.string.delete_for_all), getString(R.string.delete_for_me), getString(R.string.cancel)};
        new AlertDialog.Builder(this).setItems(choices, (menu, selected) -> {
            if (selected == choices.length - 1) return;
            if (!deleted && selected == 0) {
                EditText field = Ui.input(this, R.string.message_hint); field.setSingleLine(false); field.setText(doc.getString("text"));
                AlertDialog edit = new AlertDialog.Builder(this).setTitle(R.string.edit_message).setView(field).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
                edit.setOnShowListener(ignored -> edit.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String text = field.getText().toString().trim(); if (text.isEmpty() || text.length() > 4000) { field.setError(getString(R.string.message_too_long)); return; }
                    view.setEnabled(false); Backend.call("editMessage", Backend.data("chatId", chatId, "messageId", doc.getId(), "text", text)).addOnCompleteListener(this, task -> {
                        view.setEnabled(true); if (task.isSuccessful()) edit.dismiss(); else Toast.makeText(this, Backend.error(this, task.getException()), Toast.LENGTH_LONG).show();
                    });
                })); edit.show();
            } else {
                String scope = !deleted && selected == 1 ? "all" : "self";
                new AlertDialog.Builder(this).setMessage(R.string.delete_message_question).setNegativeButton(R.string.cancel, null).setPositiveButton("all".equals(scope) ? R.string.delete_for_all : R.string.delete_for_me, (confirm, button) ->
                    Backend.call("deleteMessage", Backend.data("chatId", chatId, "messageId", doc.getId(), "scope", scope)).addOnCompleteListener(this, task -> {
                        if (!task.isSuccessful()) Toast.makeText(this, Backend.error(this, task.getException()), Toast.LENGTH_LONG).show();
                    })).show();
            }
        }).show();
    }
    private void acknowledgeVisible() {
        if (!chatId.equals(visibleChat) || !hasWindowFocus() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        List<String> read = new ArrayList<>();
        int first = layoutManager.findFirstVisibleItemPosition(), last = layoutManager.findLastVisibleItemPosition();
        for (int position = Math.max(0, first); position <= last; position++) {
            DocumentSnapshot doc = adapter.at(position);
            if (doc != null && !Backend.uid().equals(doc.getString("senderId")) && doc.getTimestamp("readAt") == null && readQueued.add(doc.getId())) read.add(doc.getId());
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
            layoutManager.scrollToPositionWithOffset(Math.max(0, position + adapter.getItemCount() - before), 0);
            historyExhausted = task.getResult().size() < 50; older.setVisibility(historyExhausted ? View.GONE : View.VISIBLE);
        });
    }
    private void updateSendState() {
        Ui.enable(composerRow, !model.busy && model.pendingId == null); retryRow.setVisibility(model.failed ? View.VISIBLE : View.GONE);
        if (!composer.getText().toString().equals(model.draft)) composer.setText(model.draft);
        if (model.busy) { status.setVisibility(View.VISIBLE); status.setText(R.string.sending); }
        else if (model.failed) { status.setVisibility(View.VISIBLE); status.setText(R.string.error_send); }
        else { status.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE); status.setText(R.string.new_conversation); }
    }
    private void emoji() {
        EmojiPicker.show(this, value -> {
            int start = Math.max(0, composer.getSelectionStart()), end = Math.max(start, composer.getSelectionEnd());
            composer.getText().replace(start, end, value);
        });
    }
    private void leave() {
        if (model.busy) { toast(R.string.sending); return; }
        if (model.pendingId != null || !model.draft.trim().isEmpty()) {
            new AlertDialog.Builder(this).setMessage(R.string.draft_discard).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.discard, (dialog, which) -> { model.discard(); finish(); }).show();
        } else finish();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); if (model == null) return;
        out.putString("messageQuery", messageQuery); out.putString("draft", model.draft); out.putString("pendingId", model.pendingId); out.putString("pendingText", model.pendingText);
    }
    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
