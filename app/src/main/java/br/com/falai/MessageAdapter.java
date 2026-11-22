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
            public boolean areContentsTheSame(int oldPosition, int newPosition) { return java.util.Objects.equals(before.get(oldPosition).getData(), docs.get(newPosition).getData()); }
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
        root.setGravity(mine ? Gravity.END : Gravity.START); root.setPadding(Ui.dp(c, mine ? 44 : 4), Ui.dp(c, 5), Ui.dp(c, mine ? 4 : 44), Ui.dp(c, 5));
        LinearLayout bubble = Ui.column(c); bubble.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10)); bubble.setBackground(Ui.shape(c, mine ? R.color.action_green_soft : R.color.card, 18));
        TextView message = Ui.text(c, doc.getString("text"), 16, true); message.setTypeface(android.graphics.Typeface.DEFAULT); message.setTextIsSelectable(true); bubble.addView(message);
        Timestamp timestamp = doc.getTimestamp("createdAt");
        String time = timestamp == null ? "" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(timestamp.toDate());
        LinearLayout footer = Ui.row(c); footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); footer.setPadding(0, Ui.dp(c, 4), 0, 0);
        TextView label = Ui.text(c, time, 11, false); footer.addView(label);
        if (mine) {
            MessageChecks checks = new MessageChecks(c, doc.getTimestamp("deliveredAt") != null, doc.getTimestamp("readAt") != null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 18)); params.leftMargin = Ui.dp(c, 4); footer.addView(checks, params);
        }
        bubble.addView(footer);
        root.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
    }
    @Override public int getItemCount() { return messages.size(); }
    DocumentSnapshot at(int position) { return position >= 0 && position < messages.size() ? messages.get(position) : null; }
}
