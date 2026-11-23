package br.com.falai;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import java.util.ArrayList;
import java.util.List;

final class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.Holder> {
    static final class Row {
        final String key, name, detail, dateTime, avatarUid; final Runnable click, hold; final int status; final boolean contact;
        Row(String key, String name, String detail, Runnable click) { this(key, name, detail, "", key, click, null, 0, true); }
        Row(String key, String name, String detail, String dateTime, Runnable click) { this(key, name, detail, dateTime, key, click); }
        Row(String key, String name, String detail, String dateTime, String avatarUid, Runnable click) { this(key, name, detail, dateTime, avatarUid, click, null, 0); }
        Row(String key, String name, String detail, String dateTime, String avatarUid, Runnable click, Runnable hold, int status) { this(key, name, detail, dateTime, avatarUid, click, hold, status, false); }
        Row(String key, String name, String detail, String dateTime, String avatarUid, Runnable click, Runnable hold, int status, boolean contact) { this.key = key; this.name = name; this.detail = detail; this.dateTime = dateTime; this.avatarUid = avatarUid; this.click = click; this.hold = hold; this.status = status; this.contact = contact; }
    }
    private final List<Row> rows = new ArrayList<>();
    void submit(List<Row> values) {
        List<Row> before = new ArrayList<>(rows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return values.size(); }
            public boolean areItemsTheSame(int oldPosition, int newPosition) { return before.get(oldPosition).key.equals(values.get(newPosition).key); }
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Row old = before.get(oldPosition), next = values.get(newPosition); return old.name.equals(next.name) && old.detail.equals(next.detail) && old.dateTime.equals(next.dateTime) && old.status == next.status;
            }
        });
        rows.clear(); rows.addAll(values); diff.dispatchUpdatesTo(this);
    }
    static final class Holder extends RecyclerView.ViewHolder { Holder(LinearLayout root) { super(root); } }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LinearLayout root = Ui.column(parent.getContext()); root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); return new Holder(root);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LinearLayout root = (LinearLayout) holder.itemView; root.removeAllViews(); Row item = rows.get(position);
        LinearLayout content = Ui.contactRow(root.getContext(), item.name, item.detail, item.dateTime, item.avatarUid, item.status, item.contact, item.contact ? "" : item.key); content.setOnClickListener(v -> item.click.run()); root.addView(content);
        if (item.hold != null) content.setOnLongClickListener(v -> { item.hold.run(); return true; });
    }
    @Override public int getItemCount() { return rows.size(); }
}
