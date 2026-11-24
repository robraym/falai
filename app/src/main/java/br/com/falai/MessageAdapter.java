package br.com.falai;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
    static final class Notice {
        final String key, text, details; final long time;
        final Runnable action;
        Notice(String key, String text, String details, long time, Runnable action) { this.key = key; this.text = text; this.details = details; this.time = time; this.action = action; }
        boolean same(Notice other) { return other != null && time == other.time && text.equals(other.text) && details.equals(other.details); }
    }
    private static final class Item {
        final DocumentSnapshot message; final Notice notice;
        final DocumentSnapshot original;
        Item(DocumentSnapshot message, Notice notice, DocumentSnapshot original) {
            this.message = message; this.notice = notice; this.original = original;
        }
        String key() { return message != null ? "message:" + message.getId() : notice.key; }
        Timestamp time() { return message != null ? message.getTimestamp("createdAt") : new Timestamp(new java.util.Date(notice.time)); }
    }
    private final List<DocumentSnapshot> messages = new ArrayList<>();
    private final List<Notice> notices = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private boolean includeNotices = true;
    private final String uid;
    private final java.util.function.Consumer<DocumentSnapshot> actions;
    private final java.util.function.Function<String, DocumentSnapshot> findMessage;
    private final java.util.function.Supplier<String> contactName;
    private final java.util.function.Consumer<String> openQuote;
    MessageAdapter(String uid, java.util.function.Consumer<DocumentSnapshot> actions,
            java.util.function.Function<String, DocumentSnapshot> findMessage,
            java.util.function.Supplier<String> contactName, java.util.function.Consumer<String> openQuote) {
        this.uid = uid; this.actions = actions; this.findMessage = findMessage;
        this.contactName = contactName; this.openQuote = openQuote;
    }
    void submit(List<DocumentSnapshot> docs) {
        submit(docs, includeNotices);
    }
    void setNotices(List<Notice> values) { notices.clear(); notices.addAll(values); submit(new ArrayList<>(messages)); }
    void submit(List<DocumentSnapshot> docs, boolean includeNotices) {
        this.includeNotices = includeNotices;
        List<Item> before = new ArrayList<>(items), next = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            String id = doc.getString("reply.messageId");
            next.add(new Item(doc, null, id == null ? null : findMessage.apply(id)));
        }
        if (includeNotices) for (Notice notice : notices) next.add(new Item(null, notice, null));
        next.sort(java.util.Comparator.comparing(Item::time, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                .thenComparingInt(item -> item.notice == null ? 1 : 0).thenComparing(Item::key));
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return next.size(); }
            public boolean areItemsTheSame(int oldPosition, int newPosition) { return before.get(oldPosition).key().equals(next.get(newPosition).key()); }
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Item old = before.get(oldPosition), value = next.get(newPosition);
                if (old.notice != null || value.notice != null) return old.notice != null && old.notice.same(value.notice);
                return java.util.Objects.equals(old.message.getData(), value.message.getData())
                        && java.util.Objects.equals(old.original, value.original)
                        && startsDay(before, oldPosition) == startsDay(next, newPosition);
            }
        });
        messages.clear(); messages.addAll(docs); items.clear(); items.addAll(next); diff.dispatchUpdatesTo(this);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        android.view.View bubble, reactions;
        AnimatedEmojiView animated;
        Holder(LinearLayout root) { super(root); }
        void setReplyOffset(float offset, boolean animate) {
            move(bubble, offset, animate);
            move(reactions, offset, animate);
        }
        private void move(android.view.View view, float offset, boolean animate) {
            if (view == null) return;
            view.animate().cancel();
            if (animate && view.getTranslationX() != offset) {
                view.animate().translationX(offset).setDuration(140).start();
            } else {
                view.setTranslationX(offset);
            }
        }
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LinearLayout root = Ui.column(parent.getContext()); root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); return new Holder(root);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LinearLayout root = (LinearLayout) holder.itemView; android.content.Context c = root.getContext(); root.removeAllViews();
        holder.bubble = null;
        holder.reactions = null;
        holder.animated = null;
        Item item = items.get(position);
        if (item.notice != null) { bindNotice(root, item.notice); return; }
        DocumentSnapshot doc = item.message; boolean mine = uid.equals(doc.getString("senderId"));
        root.setGravity(Gravity.START); root.setPadding(Ui.dp(c, 4), Ui.dp(c, 5), Ui.dp(c, 4), Ui.dp(c, 5));
        Timestamp timestamp = doc.getTimestamp("createdAt");
        if (timestamp != null && startsDay(items, position)) {
            TextView day = Ui.text(c, dayLabel(c, timestamp), 12, false);
            day.setGravity(Gravity.CENTER); day.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
            day.setBackground(Ui.shape(c, R.color.card, 8));
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
            dateParams.gravity = Gravity.CENTER_HORIZONTAL; dateParams.topMargin = Ui.dp(c, 10); dateParams.bottomMargin = Ui.dp(c, 10);
            root.addView(day, dateParams);
        }
        LinearLayout bubble = Ui.column(c); bubble.setPadding(Ui.dp(c, mine ? 11 : 17), Ui.dp(c, 7), Ui.dp(c, mine ? 17 : 11), Ui.dp(c, 7)); bubble.setBackground(new MessageBubble(c, mine));
        holder.bubble = bubble;
        boolean deleted = Boolean.TRUE.equals(doc.getBoolean("deletedForAll"));
        String replyId = doc.getString("reply.messageId");
        if (!deleted && replyId != null) {
            boolean quotedMine = uid.equals(doc.getString("reply.senderId"));
            String quoteBody = item.original == null ? doc.getString("reply.text") : item.original.getString("text");
            Timestamp expiry = doc.getTimestamp("reply.expiresAt");
            String label = expiry != null && expiry.toDate().getTime() <= System.currentTimeMillis()
                    ? c.getString(R.string.reply_unavailable)
                    : quoteText(c, doc.getString("reply.kind"), quoteBody, quotedMine, item.original);
            LinearLayout quote = quoteView(c, quotedMine ? c.getString(R.string.reply_you) : contactName.get(), label);
            quote.setOnClickListener(view -> openQuote.accept(replyId));
            quote.setOnLongClickListener(view -> { actions.accept(doc); return true; });
            LinearLayout.LayoutParams quoteParams = new LinearLayout.LayoutParams(-1, -2);
            quoteParams.bottomMargin = Ui.dp(c, 5);
            bubble.addView(quote, quoteParams);
        }
        String content = deleted ? c.getString(R.string.message_deleted) : doc.getString("text");
        if (!deleted && "nudge".equals(doc.getString("kind"))) content = c.getString(mine ? R.string.nudge_sent : R.string.nudge_received);
        TextView message = Ui.text(c, "", 16, false); message.setTextColor(c.getColor(deleted ? R.color.text_secondary : R.color.text_primary));
        message.setTypeface(android.graphics.Typeface.DEFAULT, deleted ? android.graphics.Typeface.ITALIC : android.graphics.Typeface.NORMAL);
        String time = timestamp == null ? "" : android.text.format.DateFormat.getTimeFormat(c).format(timestamp.toDate());
        if (!deleted && doc.getTimestamp("editedAt") != null) time = c.getString(R.string.message_edited) + " · " + time;
        android.text.SpannableString text = new android.text.SpannableString(content + "  \uFFFC");
        int status = mine ? doc.getTimestamp("readAt") != null ? 3 : doc.getTimestamp("deliveredAt") != null ? 2 : 1 : 0;
        if (!deleted && !"nudge".equals(doc.getString("kind")) && AnimatedEmoji.index(content) >= 0) {
            AnimatedEmojiView animated = new AnimatedEmojiView(c, content);
            holder.animated = animated;
            animated.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = Ui.dp(c, 104 * Math.min(1.5f, c.getResources().getConfiguration().fontScale));
            bubble.addView(animated, new LinearLayout.LayoutParams(size, size));
            animated.setOnLongClickListener(view -> { actions.accept(doc); return true; });
            text = new android.text.SpannableString("\uFFFC");
            message.setGravity(Gravity.END);
        }
        text.setSpan(new MessageMetaSpan(c, time, status), text.length() - 1, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); message.setText(text); bubble.addView(message);
        message.setContentDescription(content + " " + time + (mine ? " " + c.getString(status == 3 ? R.string.message_read : status == 2 ? R.string.message_delivered : R.string.message_sent) : ""));
        if (mine || !deleted) {
            android.view.View.OnLongClickListener hold = view -> { actions.accept(doc); return true; };
            message.setOnLongClickListener(hold); bubble.setOnLongClickListener(hold);
        }
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(-2, -2);
        bubbleParams.gravity = mine ? Gravity.END : Gravity.START;
        if (mine) bubbleParams.leftMargin = Ui.dp(c, 40); else bubbleParams.rightMargin = Ui.dp(c, 40);
        root.addView(bubble, bubbleParams);
        if (!deleted && doc.get("reactions") instanceof java.util.Map) {
            java.util.Map<?, ?> reactions = (java.util.Map<?, ?>) doc.get("reactions");
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for (Object value : reactions.values()) {
                if (value instanceof String) counts.merge((String) value, 1, Integer::sum);
            }
            if (!counts.isEmpty()) {
                List<String> labels = new ArrayList<>();
                for (java.util.Map.Entry<String, Integer> reaction : counts.entrySet()) {
                    labels.add(reaction.getKey() + (reaction.getValue() > 1 ? " " + reaction.getValue() : ""));
                }
                String label = String.join("  ", labels);
                TextView badge = Ui.text(c, label, 13, false);
                holder.reactions = badge;
                badge.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
                badge.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Appearance.ripple(c)),
                        Ui.shape(c, reactions.containsKey(uid) ? R.color.action_green_soft : R.color.card, 14), null));
                badge.setContentDescription(c.getString(R.string.message_reactions, label));
                badge.setOnClickListener(view -> actions.accept(doc));
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
                badgeParams.gravity = bubbleParams.gravity;
                badgeParams.setMarginStart(Ui.dp(c, mine ? 0 : 8));
                badgeParams.setMarginEnd(Ui.dp(c, mine ? 8 : 0));
                root.addView(badge, badgeParams);
            }
        }
    }
    static String quoteText(android.content.Context context, String kind, String text, boolean mine, DocumentSnapshot original) {
        if (original != null && (HistoryRetention.expired(original) || Boolean.TRUE.equals(original.getBoolean("deletedForAll")))) {
            return context.getString(R.string.reply_unavailable);
        }
        if ("nudge".equals(kind)) return context.getString(mine ? R.string.nudge_sent : R.string.nudge_received);
        return text == null || text.isEmpty() ? context.getString(R.string.reply_unavailable) : text;
    }
    static LinearLayout quoteView(android.content.Context context, String author, String excerpt) {
        LinearLayout quote = Ui.row(context);
        quote.setBackground(Ui.shape(context, R.color.action_green_soft, 8));
        quote.setPadding(Ui.dp(context, 6), Ui.dp(context, 5), Ui.dp(context, 8), Ui.dp(context, 5));
        android.view.View rule = new android.view.View(context);
        rule.setBackground(Ui.shape(context, R.color.action_green, 2));
        quote.addView(rule, new LinearLayout.LayoutParams(Ui.dp(context, 3), -1));
        LinearLayout copy = Ui.column(context);
        copy.setPadding(Ui.dp(context, 7), 0, 0, 0);
        TextView title = Ui.text(context, author, 12, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView preview = Ui.text(context, excerpt, 12, false);
        preview.setMaxLines(2);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title);
        copy.addView(preview);
        quote.addView(copy);
        return quote;
    }
    private void bindNotice(LinearLayout root, Notice notice) {
        android.content.Context context = root.getContext(); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 8));
        TextView label = Ui.text(context, notice.text, 12, false); label.setGravity(Gravity.CENTER);
        label.setMaxWidth(Math.min(Ui.dp(context, 400), context.getResources().getDisplayMetrics().widthPixels - Ui.dp(context, 64)));
        label.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8));
        label.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(context)), Ui.shape(context, R.color.card, 10), null));
        android.graphics.drawable.Drawable clock = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.ic_history_duration);
        if (clock != null) {
            clock = clock.mutate(); clock.setTint(context.getColor(R.color.text_secondary)); clock.setBounds(0, 0, Ui.dp(context, 14), Ui.dp(context, 14));
            label.setCompoundDrawablesRelative(clock, null, null, null); label.setCompoundDrawablePadding(Ui.dp(context, 6));
        }
        label.setFocusable(true); label.setOnClickListener(view -> notice.action.run());
        root.addView(label, new LinearLayout.LayoutParams(-2, -2));
    }
    private static boolean startsDay(List<Item> docs, int position) {
        int previousPosition = position - 1;
        while (previousPosition >= 0 && docs.get(previousPosition).message == null) previousPosition--;
        if (previousPosition < 0) return true;
        Timestamp current = docs.get(position).message.getTimestamp("createdAt"), previous = docs.get(previousPosition).message.getTimestamp("createdAt");
        if (current == null || previous == null) return current != previous;
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance(); a.setTime(current.toDate()); b.setTime(previous.toDate());
        return !sameDay(a, b);
    }
    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.ERA) == b.get(Calendar.ERA) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
    private static String dayLabel(android.content.Context context, Timestamp timestamp) {
        Calendar day = Calendar.getInstance(), today = Calendar.getInstance(); day.setTime(timestamp.toDate());
        if (sameDay(day, today)) return context.getString(R.string.day_today);
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(day, today)) return context.getString(R.string.day_yesterday);
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(timestamp.toDate());
    }
    @Override public int getItemCount() { return items.size(); }
    @Override public void onViewAttachedToWindow(@NonNull Holder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder.animated != null) holder.animated.setPlaybackAllowed(true);
    }
    @Override public void onViewDetachedFromWindow(@NonNull Holder holder) {
        if (holder.animated != null) holder.animated.setPlaybackAllowed(false);
        super.onViewDetachedFromWindow(holder);
    }
    @Override public void onViewRecycled(@NonNull Holder holder) {
        if (holder.animated != null) holder.animated.setPlaybackAllowed(false);
        super.onViewRecycled(holder);
    }
    boolean scrollToMessage(RecyclerView list, String id) {
        for (int position = 0; position < items.size(); position++) {
            DocumentSnapshot doc = items.get(position).message;
            if (doc != null && id.equals(doc.getId())) {
                list.scrollToPosition(position);
                return true;
            }
        }
        return false;
    }
    DocumentSnapshot at(int position) { return position >= 0 && position < items.size() ? items.get(position).message : null; }
}
