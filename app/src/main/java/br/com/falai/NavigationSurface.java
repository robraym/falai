package br.com.falai;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

/** Shared full-screen wallpaper and navigation alignment, without an outer contour. */
final class NavigationSurface {
    static void attachCard(ViewGroup root) {
        int left = root.getPaddingLeft(), top = root.getPaddingTop(), right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        root.setBackground(new ChatWallpaper(root.getContext()));
        root.setPadding(left, top, right, bottom);
    }
    static void attach(ViewGroup root, View navigation, View selected) {
        attachCard(root);
        navigation.setBackgroundColor(Color.TRANSPARENT);
        root.getViewTreeObserver().addOnPreDrawListener(() -> {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) navigation.getLayoutParams();
            int inset = Ui.dp(root.getContext(), 10);
            int left = inset - root.getPaddingLeft(), right = inset - root.getPaddingRight();
            if (params.leftMargin == left && params.rightMargin == right) return true;
            params.leftMargin = left; params.rightMargin = right; navigation.setLayoutParams(params); return false;
        });
    }
    private NavigationSurface() { }
}
