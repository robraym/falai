package br.com.falai;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

final class Appearance {
    static final int[] LABELS = {R.string.color_green, R.string.color_blue, R.string.color_purple, R.string.color_orange, R.string.color_pink, R.string.color_teal};
    private static final String[] LIGHT = {"#168A55", "#2478D8", "#7651D6", "#C65A18", "#C63F77", "#007F86"};
    private static final String[] DARK = {"#45C98A", "#4DA3FF", "#A88BFF", "#FF9A5A", "#FF78A8", "#47C6CA"};
    private static final String[] SOFT_LIGHT = {"#E1F4E9", "#E7F1FF", "#EEE8FF", "#FFE9DA", "#FFE5F0", "#DDF3F3"};
    private static final String[] SOFT_DARK = {"#142D22", "#162235", "#261F3D", "#352217", "#351D29", "#163033"};
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("appearance", Context.MODE_PRIVATE); }
    static int selected(Context c) { return Math.max(0, Math.min(LABELS.length - 1, prefs(c).getInt("color", 0))); }
    static int percent(Context c) { return Math.max(70, Math.min(150, prefs(c).getInt("text", 100))); }
    static boolean dark(Context c) { return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    static int primary(Context c) { return Color.parseColor((dark(c) ? DARK : LIGHT)[selected(c)]); }
    static int soft(Context c) { return Color.parseColor((dark(c) ? SOFT_DARK : SOFT_LIGHT)[selected(c)]); }
    static int ripple(Context c) { return androidx.core.graphics.ColorUtils.setAlphaComponent(primary(c), 50); }
    static int theme(Context c) { return new int[]{R.style.Theme_Falai, R.style.Theme_Falai_Blue, R.style.Theme_Falai_Purple, R.style.Theme_Falai_Orange, R.style.Theme_Falai_Pink, R.style.Theme_Falai_Teal}[selected(c)]; }
    private Appearance() { }
}
