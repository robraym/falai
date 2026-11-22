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
    MessageAdapter(String uid) { this.uid = uid; }
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
        LinearLayout bubble = Ui.column(c); bubble.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10)); bubble.setBackground(Ui.shape(c, mine ? R.color.action_green_soft : R.color.card, 18));
        TextView message = Ui.text(c, doc.getString("text"), 16, true); message.setTypeface(android.graphics.Typeface.DEFAULT); message.setTextIsSelectable(true); bubble.addView(message);
        String time = timestamp == null ? "" : android.text.format.DateFormat.getTimeFormat(c).format(timestamp.toDate());
        LinearLayout footer = Ui.row(c); footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); footer.setPadding(0, Ui.dp(c, 4), 0, 0);
        TextView label = Ui.text(c, time, 11, false); footer.addView(label);
        if (mine) {
            MessageChecks checks = new MessageChecks(c, doc.getTimestamp("deliveredAt") != null, doc.getTimestamp("readAt") != null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 18)); params.leftMargin = Ui.dp(c, 4); footer.addView(checks, params);
        }
        bubble.addView(footer);
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
