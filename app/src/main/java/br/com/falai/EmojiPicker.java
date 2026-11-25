package br.com.falai;

import android.content.Context;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;
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
        AppCompatDialog dialog = new AppCompatDialog(context); dialog.supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(createPanel(context, allowAnimated, selected, dialog::dismiss, false)); dialog.setCanceledOnTouchOutside(true);
        dialog.show(); dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setGravity(Gravity.BOTTOM); dialog.getWindow().setLayout(-1, -2);
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    static View inline(Context context, Consumer<String> selected, Runnable close) {
        return createPanel(context, true, selected, close, true);
    }
    private static View createPanel(Context context, boolean allowAnimated, Consumer<String> selected, Runnable close, boolean inline) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("emoji_picker", Context.MODE_PRIVATE);
        String owner = Backend.uid(), key = "recent_" + owner, gifKey = "recent_gifs_" + owner;
        List<String> recent = readRecent(prefs, key, false), gifRecent = readRecent(prefs, gifKey, true);
        boolean[] gifs = {false}; int[] category = {recent.isEmpty() ? 0 : -1};
        int cell = Math.max(48, Math.round(36 * context.getResources().getConfiguration().fontScale));
        int columns = Math.max(2, Math.min(8, (context.getResources().getConfiguration().screenWidthDp - 24) / cell));
        int height = Math.max(240, Math.min(inline ? 340 : 480, Math.round(context.getResources().getConfiguration().screenHeightDp * (inline ? .42f : .58f))));
        LinearLayout body = Ui.column(context); body.setPadding(Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 6));
        body.setBackground(Ui.shape(context, R.color.background, 20));
        LinearLayout container = Ui.column(context); container.addView(body, new LinearLayout.LayoutParams(-1, Ui.dp(context, height)));
        Consumer<String> pick = value -> {
            if (!owner.equals(Backend.uid())) { close.run(); return; }
            boolean gif = GifCatalog.index(value) >= 0; List<String> history = gif ? gifRecent : recent;
            history.remove(value); history.add(0, value); if (history.size() > 24) history.subList(24, history.size()).clear();
            prefs.edit().putString(gif ? gifKey : key, new JSONArray(history).toString()).apply();
            selected.accept(value);
            if (!inline || gif) close.run();
        };
        View handle = new View(context); handle.setBackground(Ui.shape(context, R.color.text_secondary, 2));
        LinearLayout.LayoutParams handleSize = new LinearLayout.LayoutParams(Ui.dp(context, 32), Ui.dp(context, 4));
        handleSize.gravity = Gravity.CENTER_HORIZONTAL; handleSize.bottomMargin = Ui.dp(context, 6); body.addView(handle, handleSize);
        LinearLayout tabs = Ui.row(context); tabs.setPadding(Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4));
        tabs.setBackground(Ui.shape(context, R.color.input_background, 24));
        android.widget.ImageView emojiTab = new android.widget.ImageView(context); emojiTab.setImageResource(R.drawable.ic_emoji);
        emojiTab.setPadding(Ui.dp(context, 9), Ui.dp(context, 9), Ui.dp(context, 9), Ui.dp(context, 9));
        emojiTab.setContentDescription(context.getString(R.string.emoji_animated)); emojiTab.setFocusable(true);
        TextView gifTab = Ui.text(context, context.getString(R.string.gifs), 14, true); gifTab.setGravity(Gravity.CENTER); gifTab.setFocusable(true);
        if (allowAnimated) {
            tabs.addView(emojiTab, new LinearLayout.LayoutParams(0, Ui.dp(context, 40), 1));
            tabs.addView(gifTab, new LinearLayout.LayoutParams(0, Ui.dp(context, 40), 1));
            LinearLayout.LayoutParams tabSize = new LinearLayout.LayoutParams(Ui.dp(context, 184), -2); tabSize.gravity = Gravity.CENTER_HORIZONTAL;
            body.addView(tabs, tabSize);
        }
        TextView title = Ui.text(context, "", 13, true); title.setPadding(Ui.dp(context, 6), Ui.dp(context, 8), 0, Ui.dp(context, 4));
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true); body.addView(title);
        TextView empty = Ui.text(context, context.getString(R.string.picker_recent_empty), 13, false); body.addView(empty);
        List<String> values = new ArrayList<>(), recentValues = new ArrayList<>();
        GridAdapter mainAdapter = new GridAdapter(context, values, pick, cell);
        GridAdapter recentAdapter = new GridAdapter(context, recentValues, pick, cell); recentAdapter.horizontal = true;
        RecyclerView recentGrid = new RecyclerView(context);
        recentGrid.setItemAnimator(null);
        recentGrid.setLayoutManager(new GridLayoutManager(context, 2, RecyclerView.HORIZONTAL, false)); recentGrid.setAdapter(recentAdapter);
        recentGrid.setHorizontalScrollBarEnabled(false); body.addView(recentGrid, new LinearLayout.LayoutParams(-1, Ui.dp(context, cell * 2)));
        RecyclerView grid = new RecyclerView(context); GridLayoutManager layout = new GridLayoutManager(context, columns);
        grid.setItemAnimator(null);
        grid.setLayoutManager(layout); grid.setAdapter(mainAdapter); body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        View recentSpace = new View(context); body.addView(recentSpace, new LinearLayout.LayoutParams(-1, 0, 1));
        HorizontalScrollView navigation = new HorizontalScrollView(context); navigation.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryTabs = Ui.row(context); navigation.addView(categoryTabs); body.addView(navigation);
        String[] emojiNames = context.getResources().getStringArray(R.array.emoji_catalog_categories);
        String[] gifNames = context.getResources().getStringArray(R.array.gif_categories);
        int[] gifIcons = {0, 9, 12, 10, 2, 3, 11};
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            String[] names = gifs[0] ? gifNames : emojiNames;
            if (category[0] >= names.length) category[0] = 0;
            emojiTab.setSelected(!gifs[0]); gifTab.setSelected(gifs[0]);
            emojiTab.setBackground(!gifs[0] ? Ui.shape(context, R.color.action_green_soft, 20) : null);
            gifTab.setBackground(gifs[0] ? Ui.shape(context, R.color.action_green_soft, 20) : null);
            emojiTab.setImageTintList(android.content.res.ColorStateList.valueOf(context.getColor(gifs[0] ? R.color.text_secondary : R.color.text_primary)));
            gifTab.setTextColor(context.getColor(gifs[0] ? R.color.text_primary : R.color.text_secondary));
            title.setText(category[0] < 0 ? context.getString(R.string.emoji_recent) : names[category[0]]);
            values.clear(); recentValues.clear();
            if (category[0] < 0) recentValues.addAll(gifs[0] ? gifRecent : recent);
            else if (gifs[0]) {
                for (int i = 0; i < GifCatalog.IDS.length; i++) if (GifCatalog.GROUPS[i] == category[0]) values.add(GifCatalog.message(i));
            } else if (supported != null) values.addAll(supported.get(category[0]));
            boolean waiting = !gifs[0] && category[0] >= 0 && supported == null;
            empty.setText(waiting ? R.string.loading : R.string.picker_recent_empty);
            empty.setVisibility(waiting || (category[0] < 0 && recentValues.isEmpty()) ? View.VISIBLE : View.GONE);
            recentAdapter.cell = cell;
            int compactHeight = (allowAnimated ? 152 : 104) + recentAdapter.cell * 2;
            boolean gifCategory = gifs[0] && category[0] >= 0;
            int gifRows = Math.max(1, Math.min(3, (values.size() + columns - 1) / columns));
            LinearLayout.LayoutParams gridSize = (LinearLayout.LayoutParams) grid.getLayoutParams();
            gridSize.height = gifCategory ? Ui.dp(context, gifRows * cell) : 0;
            gridSize.weight = gifCategory ? 0 : 1; grid.setLayoutParams(gridSize);
            body.getLayoutParams().height = gifCategory ? -2 : Ui.dp(context, category[0] < 0 ? Math.min(height, compactHeight) : height); body.requestLayout();
            recentGrid.getLayoutParams().height = Ui.dp(context, recentAdapter.cell * 2); recentGrid.requestLayout();
            recentGrid.setVisibility(category[0] < 0 && !recentValues.isEmpty() ? View.VISIBLE : View.GONE);
            grid.setVisibility(category[0] < 0 ? View.GONE : View.VISIBLE); layout.setSpanCount(columns);
            recentSpace.setVisibility(category[0] < 0 ? View.VISIBLE : View.GONE);
            mainAdapter.animated = allowAnimated; recentAdapter.animated = allowAnimated;
            mainAdapter.notifyDataSetChanged(); recentAdapter.notifyDataSetChanged(); grid.scrollToPosition(0); recentGrid.scrollToPosition(0);
            categoryTabs.removeAllViews();
            for (int i = -1; i < names.length; i++) {
                final int target = i;
                EmojiCategoryView shortcut = new EmojiCategoryView(context, i < 0 ? -1 : gifs[0] ? gifIcons[i] : i);
                String label = i < 0 ? context.getString(R.string.emoji_recent) : names[i]; shortcut.setContentDescription(label);
                androidx.appcompat.widget.TooltipCompat.setTooltipText(shortcut, label); shortcut.setSelected(category[0] == i);
                if (category[0] == i) shortcut.setBackground(Ui.shape(context, R.color.input_background, 24));
                shortcut.setOnClickListener(view -> { category[0] = target; render[0].run(); });
                categoryTabs.addView(shortcut, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
            }
        };
        emojiTab.setOnClickListener(view -> { gifs[0] = false; category[0] = recent.isEmpty() ? 0 : -1; navigation.scrollTo(0, 0); render[0].run(); });
        gifTab.setOnClickListener(view -> { gifs[0] = true; category[0] = gifRecent.isEmpty() ? 0 : -1; navigation.scrollTo(0, 0); render[0].run(); });
        render[0].run();
        if (supported == null) Tasks.call(ContactsRepository.IO, () -> {
            Paint paint = new Paint(); paint.setTextSize(Ui.dp(context, 26)); List<List<String>> available = new ArrayList<>();
            for (List<String> group : EmojiCatalog.GROUPS) {
                List<String> found = new ArrayList<>(); for (String emoji : group) if (paint.hasGlyph(emoji)) found.add(emoji); available.add(found);
            }
            supported = available; return available;
        }).addOnCompleteListener(task -> {
            if (!container.isAttachedToWindow()) return;
            if (task.isSuccessful()) render[0].run(); else { empty.setText(R.string.emoji_load_error); empty.setVisibility(View.VISIBLE); }
        });
        return container;
    }
    private static List<String> readRecent(android.content.SharedPreferences prefs, String key, boolean gif) {
        List<String> values = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < Math.min(24, stored.length()); i++) {
                String value = stored.optString(i);
                if (!value.isEmpty() && !values.contains(value) && (gif ? GifCatalog.index(value) >= 0 : AnimatedEmoji.index(value) >= 0)) values.add(value);
            }
        } catch (org.json.JSONException ignored) { }
        return values;
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout box; AnimatedEmojiView animation; LocalGifView gif;
        Holder(LinearLayout box) { super(box); this.box = box; }
        void playback(boolean allowed) { if (animation != null) animation.setPlaybackAllowed(allowed); if (gif != null) gif.setPlaybackAllowed(allowed); }
    }
    private static final class GridAdapter extends RecyclerView.Adapter<Holder> {
        final Context context; final List<String> values; final Consumer<String> pick; int cell; boolean animated, horizontal;
        GridAdapter(Context context, List<String> values, Consumer<String> pick, int cell) { this.context = context; this.values = values; this.pick = pick; this.cell = cell; }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) { return new Holder(Ui.column(context)); }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.playback(false); holder.box.removeAllViews(); holder.animation = null; holder.gif = null;
            String value = values.get(position); int gif = GifCatalog.index(value);
            holder.box.setLayoutParams(new RecyclerView.LayoutParams(horizontal ? Ui.dp(context, cell) : -1, Ui.dp(context, cell)));
            View content;
            if (gif >= 0) {
                holder.gif = new LocalGifView(context, gif); content = holder.gif;
                androidx.appcompat.widget.TooltipCompat.setTooltipText(content, context.getString(GifCatalog.LABELS[gif]));
            } else if (animated) { holder.animation = new AnimatedEmojiView(context, value); content = holder.animation; }
            else { TextView glyph = Ui.text(context, value, 26, false); glyph.setGravity(Gravity.CENTER); content = glyph; }
            content.setContentDescription(GifCatalog.preview(context, value)); content.setFocusable(true); content.setOnClickListener(view -> pick.accept(value));
            content.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(context)), null, Ui.shape(context, R.color.card, 12)));
            holder.box.addView(content, new LinearLayout.LayoutParams(-1, -1));
        }
        @Override public int getItemCount() { return values.size(); }
        @Override public void onViewAttachedToWindow(@NonNull Holder holder) { super.onViewAttachedToWindow(holder); holder.playback(true); }
        @Override public void onViewDetachedFromWindow(@NonNull Holder holder) { holder.playback(false); super.onViewDetachedFromWindow(holder); }
        @Override public void onViewRecycled(@NonNull Holder holder) { holder.playback(false); super.onViewRecycled(holder); }
    }
}
