package br.com.falai;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.ColorUtils;

/** The Alertou contour: one rounded surface joined to the selected bottom tab. */
final class NavigationSurface extends Drawable {
    private final ViewGroup root;
    private final View navigation, selected;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect selectedBounds = new Rect();
    static void attach(ViewGroup root, View navigation, View selected) {
        NavigationSurface surface = new NavigationSurface(root, navigation, selected);
        int left = root.getPaddingLeft(), top = root.getPaddingTop(), right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        root.setBackground(new LayerDrawable(new Drawable[]{new android.graphics.drawable.ColorDrawable(root.getContext().getColor(R.color.background)), surface}));
        root.setPadding(left, top, right, bottom);
        navigation.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.getViewTreeObserver().addOnPreDrawListener(() -> {
            boolean changed = surface.alignNavigation();
            if (changed) surface.invalidateSelf();
            return !changed;
        });
        root.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> surface.invalidateSelf());
    }
    private NavigationSurface(ViewGroup root, View navigation, View selected) {
        this.root = root; this.navigation = navigation; this.selected = selected;
        fill.setColor(ColorUtils.blendARGB(root.getContext().getColor(R.color.background), root.getContext().getColor(R.color.icon_surface), .28f));
        outline.setStyle(Paint.Style.STROKE); outline.setStrokeWidth(dp(1)); outline.setColor(ColorUtils.setAlphaComponent(root.getContext().getColor(R.color.action_green), 80));
    }
    private float dp(float value) { return value * root.getResources().getDisplayMetrics().density; }
    private int sideInset() { return Math.round(dp(10) + outline.getStrokeWidth() / 2); }
    private boolean alignNavigation() {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) navigation.getLayoutParams();
        int left = sideInset() - root.getPaddingLeft(), right = sideInset() - root.getPaddingRight();
        if (params.leftMargin == left && params.rightMargin == right) return false;
        params.leftMargin = left; params.rightMargin = right; navigation.setLayoutParams(params); return true;
    }
    @Override public void draw(Canvas canvas) {
        if (selected.getWidth() == 0 || root.getChildCount() < 3) return;
        selected.getDrawingRect(selectedBounds); root.offsetDescendantRectToMyCoords(selected, selectedBounds);
        float left = sideInset(), right = root.getWidth() - sideInset();
        float top = Math.max(root.getPaddingTop(), root.getChildAt(0).getTop()) + dp(4), bottom = navigation.getTop() + dp(4);
        float tabLeft = Math.max(left, selectedBounds.left), tabRight = Math.min(right, selectedBounds.right), tabBottom = selectedBounds.bottom - dp(8);
        float radius = dp(20), tabRadius = Math.min(dp(16), (tabRight - tabLeft) / 2);
        boolean flushLeft = tabLeft <= left, flushRight = tabRight >= right;
        if (flushLeft) tabLeft = left; if (flushRight) tabRight = right;
        float shoulderL = Math.max(0, Math.min(dp(10), (tabLeft - left) / 2));
        float shoulderR = Math.max(0, Math.min(dp(10), (right - tabRight) / 2));
        float cornerL = Math.min(radius, Math.max(0, tabLeft - shoulderL - left));
        float cornerR = Math.min(radius, Math.max(0, right - tabRight - shoulderR));
        path.reset(); path.moveTo(left + radius, top); path.lineTo(right - radius, top); path.quadTo(right, top, right, top + radius);
        if (!flushRight) {
            path.lineTo(right, bottom - cornerR); path.quadTo(right, bottom, right - cornerR, bottom); path.lineTo(tabRight + shoulderR, bottom);
            path.cubicTo(tabRight + shoulderR * .35f, bottom, tabRight, bottom + shoulderR * .35f, tabRight, bottom + shoulderR);
        }
        path.lineTo(tabRight, tabBottom - tabRadius); path.quadTo(tabRight, tabBottom, tabRight - tabRadius, tabBottom);
        path.lineTo(tabLeft + tabRadius, tabBottom); path.quadTo(tabLeft, tabBottom, tabLeft, tabBottom - tabRadius);
        if (!flushLeft) {
            path.lineTo(tabLeft, bottom + shoulderL); path.cubicTo(tabLeft, bottom + shoulderL * .35f, tabLeft - shoulderL * .35f, bottom, tabLeft - shoulderL, bottom);
            path.lineTo(left + cornerL, bottom); path.quadTo(left, bottom, left, bottom - cornerL);
        }
        path.lineTo(left, top + radius); path.quadTo(left, top, left + radius, top); path.close(); canvas.drawPath(path, fill); canvas.drawPath(path, outline);
    }
    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); outline.setAlpha(alpha * 80 / 255); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); outline.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
