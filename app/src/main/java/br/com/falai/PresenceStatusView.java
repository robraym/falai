package br.com.falai;

import android.content.Context;
import androidx.appcompat.widget.AppCompatTextView;

/** Reuses chat presence while the row is attached, without another typing subscription. */
final class PresenceStatusView extends AppCompatTextView {
    private final ChatPresence presence;
    private int badgeColor;
    PresenceStatusView(Context context, String uid) {
        super(context); setTextSize(10.5f); setSingleLine(true);
        setTypeface(null, android.graphics.Typeface.BOLD);
        setGravity(android.view.Gravity.CENTER); setIncludeFontPadding(true);
        setMinHeight(Ui.dp(context, 24));
        setPadding(Ui.dp(context, 7), Ui.dp(context, 3), Ui.dp(context, 7), Ui.dp(context, 3));
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        setTextColor(context.getColor(R.color.text_secondary)); setText(R.string.presence_unknown);
        refreshBadge();
        presence = new ChatPresence(context, "", this, false); presence.listen(uid);
    }
    void refreshBadge() {
        int color = getCurrentTextColor();
        if (getBackground() != null && badgeColor == color) return;
        badgeColor = color;
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(getContext().getColor(R.color.presence_badge_surface));
        background.setCornerRadius(Ui.dp(getContext(), 8));
        background.setStroke(Ui.dp(getContext(), 1), androidx.core.graphics.ColorUtils.setAlphaComponent(color, 80));
        setBackground(background);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); if (getWindowVisibility() == VISIBLE) presence.resume();
    }
    @Override protected void onDetachedFromWindow() { presence.pause(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (presence == null) return;
        if (visibility == VISIBLE && isAttachedToWindow()) presence.resume(); else presence.pause();
    }
}
