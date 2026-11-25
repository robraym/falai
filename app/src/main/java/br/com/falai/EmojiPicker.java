package br.com.falai;

import android.content.Context;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.tasks.Tasks;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class EmojiPicker {
    private static volatile List<List<String>> supported;
    static void show(Context context, Consumer<String> selected) { show(context, false, selected); }
    static void show(Context context, boolean allowAnimated, Consumer<String> selected) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("emoji_picker", Context.MODE_PRIVATE);
        String owner = Backend.uid(), key = "recent_" + owner;
        List<String> recent = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < Math.min(24, stored.length()); i++) {
                String value = stored.optString(i); if (!value.isEmpty() && !recent.contains(value)) recent.add(value);
            }
        } catch (org.json.JSONException ignored) { }
        LinearLayout body = Ui.column(context); body.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        int height = Math.max(160, Math.min(520, Math.round(context.getResources().getConfiguration().screenHeightDp * .65f)));
        LinearLayout container = Ui.column(context); container.addView(body, new LinearLayout.LayoutParams(-1, Ui.dp(context, height)));
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.emoji).setView(container).setNegativeButton(R.string.cancel, null).create();
        Consumer<String> pick = value -> {
            if (!owner.equals(Backend.uid())) { dialog.dismiss(); return; }
            recent.remove(value); recent.add(0, value);
            if (recent.size() > 24) recent.subList(24, recent.size()).clear();
            prefs.edit().putString(key, new JSONArray(recent).toString()).apply(); selected.accept(value); dialog.dismiss();
        };
        int cell = Math.max(48, Math.round(36 * context.getResources().getConfiguration().fontScale));
        int columns = Math.max(2, Math.min(8, (context.getResources().getConfiguration().screenWidthDp - 72) / cell));
        List<Cell> cells = new ArrayList<>();
        GridAdapter mainAdapter = new GridAdapter(context, cells, pick, cell);
        GridAdapter recentAdapter = new GridAdapter(context, new ArrayList<>(), pick, cell);
        boolean[] animated = {allowAnimated && prefs.getInt("tab_" + owner, 0) == 1};
        LinearLayout tabs = Ui.row(context);
        tabs.setPadding(Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4));
        tabs.setBackground(Ui.shape(context, R.color.input_background, 24));
        TextView normalTab = tab(context, R.string.emoji_tab_standard), animatedTab = tab(context, R.string.emoji_animated);
        if (allowAnimated) {
            tabs.addView(normalTab, new LinearLayout.LayoutParams(0, -2, 1));
            tabs.addView(animatedTab, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(tabs);
        }
        body.addView(heading(context, context.getString(R.string.emoji_recent)));
        if (recent.isEmpty()) body.addView(Ui.text(context, context.getString(R.string.emoji_recent_empty), 12, false));
        else {
            for (String emoji : recent) recentAdapter.cells.add(new Cell(emoji, false));
            recentAdapter.horizontal = true;
            RecyclerView strip = new RecyclerView(context);
            strip.setLayoutManager(new GridLayoutManager(context, 2, RecyclerView.HORIZONTAL, false));
            strip.setAdapter(recentAdapter); strip.setHorizontalScrollBarEnabled(false);
            body.addView(strip, new LinearLayout.LayoutParams(-1, Ui.dp(context, cell * 2)));
        }
        TextView loading = Ui.text(context, context.getString(R.string.loading), 13, false); body.addView(loading);
        RecyclerView grid = new RecyclerView(context); GridLayoutManager layout = new GridLayoutManager(context, columns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return cells.get(position).heading ? columns : 1; }
        });
        grid.setLayoutManager(layout); grid.setAdapter(mainAdapter);
        body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        int[] categoryPositions = new int[9]; java.util.Arrays.fill(categoryPositions, -1);
        HorizontalScrollView navigation = new HorizontalScrollView(context); navigation.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryTabs = Ui.row(context); navigation.addView(categoryTabs);
        String[] categoryNames = context.getResources().getStringArray(R.array.emoji_catalog_categories);
        String[] categoryIcons = {"😀", "👋", "🐾", "🍔", "✈️", "⚽", "💡", "🔣", "🏳️"};
        for (int i = 0; i < categoryIcons.length; i++) {
            final int category = i;
            TextView shortcut = Ui.text(context, categoryIcons[i], 22, false); shortcut.setGravity(Gravity.CENTER);
            shortcut.setContentDescription(categoryNames[i]); shortcut.setFocusable(true);
            shortcut.setOnClickListener(view -> { if (categoryPositions[category] >= 0) layout.scrollToPositionWithOffset(categoryPositions[category], 0); });
            categoryTabs.addView(shortcut, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        }
        body.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        Runnable render = () -> {
            for (TextView item : new TextView[]{normalTab, animatedTab}) {
                boolean active = item == (animated[0] ? animatedTab : normalTab);
                item.setSelected(active); item.setTextColor(active ? Appearance.primary(context) : context.getColor(R.color.text_secondary));
                item.setBackground(active ? Ui.shape(context, R.color.action_green_soft, 20) : null);
            }
            mainAdapter.animated = animated[0]; recentAdapter.animated = animated[0]; recentAdapter.notifyDataSetChanged();
            cells.clear(); java.util.Arrays.fill(categoryPositions, -1); List<List<String>> groups = supported;
            if (groups != null) {
                loading.setVisibility(View.GONE);
                String[] names = context.getResources().getStringArray(R.array.emoji_catalog_categories);
                for (int i = 0; i < groups.size(); i++) if (!groups.get(i).isEmpty()) {
                    categoryPositions[i] = cells.size();
                    cells.add(new Cell(names[i], true)); for (String emoji : groups.get(i)) cells.add(new Cell(emoji, false));
                }
            }
            mainAdapter.notifyDataSetChanged(); grid.scrollToPosition(0);
        };
        normalTab.setOnClickListener(view -> { animated[0] = false; prefs.edit().putInt("tab_" + owner, 0).apply(); render.run(); });
        animatedTab.setOnClickListener(view -> { animated[0] = true; prefs.edit().putInt("tab_" + owner, 1).apply(); render.run(); });
        dialog.show(); render.run();
        if (supported == null) Tasks.call(ContactsRepository.IO, () -> {
            Paint paint = new Paint(); paint.setTextSize(Ui.dp(context, 26));
            List<List<String>> available = new ArrayList<>();
            for (List<String> group : EmojiCatalog.GROUPS) {
                List<String> values = new ArrayList<>(); for (String emoji : group) if (paint.hasGlyph(emoji)) values.add(emoji);
                available.add(values);
            }
            supported = available; return available;
        }).addOnCompleteListener(task -> {
            if (!dialog.isShowing()) return;
            if (task.isSuccessful()) render.run(); else loading.setText(R.string.emoji_load_error);
        });
    }
    private static TextView tab(Context context, int label) {
        TextView tab = Ui.text(context, context.getString(label), 14, true);
        tab.setGravity(Gravity.CENTER); tab.setMinHeight(Ui.dp(context, 48)); tab.setFocusable(true); return tab;
    }
    private static TextView heading(Context context, String title) {
        TextView label = Ui.text(context, title, 13, true); label.setPadding(Ui.dp(context, 4), Ui.dp(context, 10), Ui.dp(context, 4), Ui.dp(context, 6));
        androidx.core.view.ViewCompat.setAccessibilityHeading(label, true); return label;
    }
    private static final class Cell {
        final String text; final boolean heading;
        Cell(String text, boolean heading) { this.text = text; this.heading = heading; }
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout box; AnimatedEmojiView animation;
        Holder(LinearLayout box) { super(box); this.box = box; }
    }
    private static final class GridAdapter extends RecyclerView.Adapter<Holder> {
        final Context context; final List<Cell> cells; final Consumer<String> pick; final int cell;
        boolean animated, horizontal;
        GridAdapter(Context context, List<Cell> cells, Consumer<String> pick, int cell) { this.context = context; this.cells = cells; this.pick = pick; this.cell = cell; }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) { return new Holder(Ui.column(context)); }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.box.removeAllViews(); holder.animation = null; Cell item = cells.get(position);
            holder.box.setLayoutParams(new RecyclerView.LayoutParams(horizontal ? Ui.dp(context, cell) : -1, item.heading ? -2 : Ui.dp(context, cell)));
            View content;
            if (item.heading) content = heading(context, item.text);
            else if (animated && AnimatedEmoji.index(item.text) >= 0) { holder.animation = new AnimatedEmojiView(context, item.text); content = holder.animation; }
            else { TextView glyph = Ui.text(context, item.text, 26, false); glyph.setGravity(Gravity.CENTER); content = glyph; }
            if (!item.heading) {
                content.setContentDescription(item.text); content.setFocusable(true); content.setOnClickListener(view -> pick.accept(item.text));
                content.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(context)), null, Ui.shape(context, R.color.card, 12)));
            }
            holder.box.addView(content, new LinearLayout.LayoutParams(-1, item.heading ? -2 : -1));
        }
        @Override public int getItemCount() { return cells.size(); }
        @Override public void onViewAttachedToWindow(@NonNull Holder holder) { super.onViewAttachedToWindow(holder); if (holder.animation != null) holder.animation.setPlaybackAllowed(true); }
        @Override public void onViewDetachedFromWindow(@NonNull Holder holder) { if (holder.animation != null) holder.animation.setPlaybackAllowed(false); super.onViewDetachedFromWindow(holder); }
        @Override public void onViewRecycled(@NonNull Holder holder) { if (holder.animation != null) holder.animation.setPlaybackAllowed(false); super.onViewRecycled(holder); }
    }
}
