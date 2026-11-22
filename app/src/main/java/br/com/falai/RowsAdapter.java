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
        final String key, name, detail, dateTime; final Runnable click;
        Row(String key, String name, String detail, Runnable click) { this(key, name, detail, "", click); }
        Row(String key, String name, String detail, String dateTime, Runnable click) { this.key = key; this.name = name; this.detail = detail; this.dateTime = dateTime; this.click = click; }
    }
    private final List<Row> rows = new ArrayList<>();
    void submit(List<Row> values) {
        List<Row> before = new ArrayList<>(rows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return values.size(); }
            public boolean areItemsTheSame(int oldPosition, int newPosition) { return before.get(oldPosition).key.equals(values.get(newPosition).key); }
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Row old = before.get(oldPosition), next = values.get(newPosition); return old.name.equals(next.name) && old.detail.equals(next.detail) && old.dateTime.equals(next.dateTime);
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
        LinearLayout content = Ui.contactRow(root.getContext(), item.name, item.detail, item.dateTime); content.setOnClickListener(v -> item.click.run()); root.addView(content);
        if (position < rows.size() - 1) root.addView(Ui.divider(root.getContext()));
    }
    @Override public int getItemCount() { return rows.size(); }
}
