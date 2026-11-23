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
    private final List<DocumentSnapshot> messages = new ArrayList<>();
    private final String uid;
    private final java.util.function.Consumer<DocumentSnapshot> actions;
    MessageAdapter(String uid, java.util.function.Consumer<DocumentSnapshot> actions) { this.uid = uid; this.actions = actions; }
    void submit(List<DocumentSnapshot> docs) {
        List<DocumentSnapshot> before = new ArrayList<>(messages);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return docs.size(); }
            public boolean areItemsTheSame(int oldPosition, int newPosition) { return before.get(oldPosition).getId().equals(docs.get(newPosition).getId()); }
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return java.util.Objects.equals(before.get(oldPosition).getData(), docs.get(newPosition).getData())
                        && startsDay(before, oldPosition) == startsDay(docs, newPosition);
            }
        });
        messages.clear(); messages.addAll(docs); diff.dispatchUpdatesTo(this);
    }
    static final class Holder extends RecyclerView.ViewHolder { Holder(LinearLayout v) { super(v); } }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LinearLayout root = Ui.column(parent.getContext()); root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); return new Holder(root);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LinearLayout root = (LinearLayout) holder.itemView; android.content.Context c = root.getContext(); root.removeAllViews();
        DocumentSnapshot doc = messages.get(position); boolean mine = uid.equals(doc.getString("senderId"));
        root.setGravity(Gravity.START); root.setPadding(Ui.dp(c, 4), Ui.dp(c, 5), Ui.dp(c, 4), Ui.dp(c, 5));
        Timestamp timestamp = doc.getTimestamp("createdAt");
        if (timestamp != null && startsDay(messages, position)) {
            TextView day = Ui.text(c, dayLabel(c, timestamp), 12, false);
            day.setGravity(Gravity.CENTER); day.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
            day.setBackground(Ui.shape(c, R.color.card, 8));
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
            dateParams.gravity = Gravity.CENTER_HORIZONTAL; dateParams.topMargin = Ui.dp(c, 10); dateParams.bottomMargin = Ui.dp(c, 10);
            root.addView(day, dateParams);
        }
        LinearLayout bubble = Ui.column(c); bubble.setPadding(Ui.dp(c, mine ? 11 : 17), Ui.dp(c, 7), Ui.dp(c, mine ? 17 : 11), Ui.dp(c, 7)); bubble.setBackground(new MessageBubble(c, mine));
        boolean deleted = Boolean.TRUE.equals(doc.getBoolean("deletedForAll"));
        String content = deleted ? c.getString(R.string.message_deleted) : doc.getString("text");
        TextView message = Ui.text(c, "", 16, false); message.setTextColor(c.getColor(deleted ? R.color.text_secondary : R.color.text_primary));
        message.setTypeface(android.graphics.Typeface.DEFAULT, deleted ? android.graphics.Typeface.ITALIC : android.graphics.Typeface.NORMAL);
        String time = timestamp == null ? "" : android.text.format.DateFormat.getTimeFormat(c).format(timestamp.toDate());
        if (!deleted && doc.getTimestamp("editedAt") != null) time = c.getString(R.string.message_edited) + " · " + time;
        android.text.SpannableString text = new android.text.SpannableString(content + "  \uFFFC");
        int status = mine ? doc.getTimestamp("readAt") != null ? 3 : doc.getTimestamp("deliveredAt") != null ? 2 : 1 : 0;
        text.setSpan(new MessageMetaSpan(c, time, status), text.length() - 1, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); message.setText(text); bubble.addView(message);
        message.setContentDescription(content + " " + time + (mine ? " " + c.getString(status == 3 ? R.string.message_read : status == 2 ? R.string.message_delivered : R.string.message_sent) : ""));
        if (mine) {
            android.view.View.OnLongClickListener hold = view -> { actions.accept(doc); return true; };
            message.setOnLongClickListener(hold); bubble.setOnLongClickListener(hold);
        }
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(-2, -2);
        bubbleParams.gravity = mine ? Gravity.END : Gravity.START;
        if (mine) bubbleParams.leftMargin = Ui.dp(c, 40); else bubbleParams.rightMargin = Ui.dp(c, 40);
        root.addView(bubble, bubbleParams);
    }
    private static boolean startsDay(List<DocumentSnapshot> docs, int position) {
        if (position == 0) return true;
        Timestamp current = docs.get(position).getTimestamp("createdAt"), previous = docs.get(position - 1).getTimestamp("createdAt");
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
    @Override public int getItemCount() { return messages.size(); }
    DocumentSnapshot at(int position) { return position >= 0 && position < messages.size() ? messages.get(position) : null; }
}
