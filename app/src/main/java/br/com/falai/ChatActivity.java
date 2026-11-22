package br.com.falai;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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

public final class ChatActivity extends AppCompatActivity {
    static volatile String visibleChat;
    private String chatId, name;
    private ChatViewModel model;
    private EditText composer;
    private LinearLayout composerRow, retryRow;
    private TextView status, title;
    private Button older;
    private RecyclerView messages;
    private LinearLayoutManager layoutManager;
    private MessageAdapter adapter;
    private ListenerRegistration listener;
    private final Map<String, DocumentSnapshot> loaded = new HashMap<>();
    private DocumentSnapshot oldest;
    private boolean loadingOlder, historyExhausted;
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
        LinearLayout header = Ui.row(this); header.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
        header.addView(Ui.icon(this, R.drawable.ic_back, R.string.back, v -> leave()));
        title = Ui.text(this, name, 20, true); title.setPadding(Ui.dp(this, 14), 0, 0, 0); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);
        older = Ui.button(this, R.string.older_messages, v -> loadOlder()); older.setVisibility(View.GONE); root.addView(older);
        messages = new RecyclerView(this); layoutManager = new LinearLayoutManager(this); layoutManager.setStackFromEnd(true);
        messages.setBackground(new ChatWallpaper(this));
        messages.setLayoutManager(layoutManager); adapter = new MessageAdapter(Backend.uid()); messages.setAdapter(adapter);
        messages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) { acknowledgeVisible(); }
        });
        root.addView(messages, new LinearLayout.LayoutParams(-1, 0, 1));
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
        emojiButton.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3345C98A), Ui.shape(this, R.color.card, 24), null));
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
            model.sendText();
        });
        sendButton.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.background)));
        sendButton.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33000000), Ui.shape(this, R.color.action_green, 24), null));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)); sendParams.leftMargin = Ui.dp(this, 8);
        composerRow.addView(sendButton, sendParams); root.addView(composerRow);
        composer.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { model.draft = s.toString(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        model.changes.observe(this, ignored -> updateSendState());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
    }
    @Override protected void onStart() {
        super.onStart(); if (model == null) return;
        loadingOlder = false; older.setEnabled(true);
        if (Backend.uid().isEmpty()) { finish(); return; }
        Backend.db().collection("chats").document(chatId).get().addOnSuccessListener(this, doc -> {
            @SuppressWarnings("unchecked") Map<String, String> names = (Map<String, String>) doc.get("names");
            if (getString(R.string.app_name).equals(name) && names != null) {
                for (Map.Entry<String, String> entry : names.entrySet()) if (!entry.getKey().equals(Backend.uid())) { name = entry.getValue(); title.setText(name); }
            }
        });
        listener = query().limit(50).addSnapshotListener(this, (snapshot, error) -> {
            if (error != null) { status.setVisibility(View.VISIBLE); status.setText(R.string.error_chat_loading); return; }
            if (snapshot == null) return;
            boolean bottom = loaded.isEmpty() || layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3;
            merge(snapshot.getDocuments());
            if (bottom && adapter.getItemCount() > 0) messages.scrollToPosition(adapter.getItemCount() - 1);
            older.setVisibility(!historyExhausted && (snapshot.size() >= 50 || loaded.size() > 50) ? View.VISIBLE : View.GONE);
            if (!model.busy && !model.failed) { status.setText(R.string.new_conversation); status.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE); }
        });
    }
    @Override protected void onResume() { super.onResume(); visibleChat = chatId; if (messages != null) messages.post(this::acknowledgeVisible); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && messages != null) messages.post(this::acknowledgeVisible); }
    @Override protected void onPause() { visibleChat = null; super.onPause(); }
    @Override protected void onStop() { if (listener != null) { listener.remove(); listener = null; } super.onStop(); }
    private Query query() {
        return Backend.db().collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.DESCENDING);
    }
    private void merge(List<DocumentSnapshot> docs) {
        List<String> delivered = new ArrayList<>();
        for (DocumentSnapshot doc : docs) if (!Backend.uid().equals(doc.getString("senderId")) && doc.getTimestamp("deliveredAt") == null && deliveryQueued.add(doc.getId())) delivered.add(doc.getId());
        ReceiptWorker.schedule(chatId, delivered, false);
        for (DocumentSnapshot doc : docs) loaded.put(doc.getId(), doc);
        List<DocumentSnapshot> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparing((DocumentSnapshot doc) -> doc.getTimestamp("createdAt")).thenComparing(DocumentSnapshot::getId));
        if (!sorted.isEmpty()) oldest = sorted.get(0); adapter.submit(sorted);
        messages.post(this::acknowledgeVisible);
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
        String[] emojis = {"😀", "😂", "🥰", "😍", "😊", "😎", "🤔", "😢", "😭", "❤️", "👍", "👎", "👏", "🙏", "🎉", "🔥", "✅", "👋", "💪", "🤝"};
        LinearLayout grid = Ui.column(this); grid.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.emoji).setView(grid).setNegativeButton(R.string.cancel, null).create();
        for (int row = 0; row < 4; row++) {
            LinearLayout line = Ui.row(this); grid.addView(line);
            for (int col = 0; col < 5; col++) {
                String value = emojis[row * 5 + col]; TextView option = Ui.text(this, value, 26, false); option.setGravity(Gravity.CENTER); option.setContentDescription(value);
                line.addView(option, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
                option.setOnClickListener(v -> {
                    int start = Math.max(0, composer.getSelectionStart()), end = Math.max(start, composer.getSelectionEnd());
                    composer.getText().replace(start, end, value); dialog.dismiss();
                });
            }
        }
        dialog.show();
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
        out.putString("draft", model.draft); out.putString("pendingId", model.pendingId); out.putString("pendingText", model.pendingText);
    }
    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
