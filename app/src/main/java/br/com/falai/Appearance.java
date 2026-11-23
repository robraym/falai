package br.com.falai;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

final class Appearance {
    static final int[] LABELS = {R.string.color_green, R.string.color_blue, R.string.color_purple, R.string.color_orange, R.string.color_pink, R.string.color_teal, R.string.color_red, R.string.color_coral, R.string.color_salmon, R.string.color_amber, R.string.color_gold, R.string.color_lime, R.string.color_olive, R.string.color_moss, R.string.color_mint, R.string.color_jade, R.string.color_emerald, R.string.color_forest, R.string.color_aqua, R.string.color_cyan, R.string.color_sky, R.string.color_indigo, R.string.color_navy, R.string.color_violet, R.string.color_lavender, R.string.color_magenta, R.string.color_rose, R.string.color_wine, R.string.color_brown, R.string.color_slate};
    private static final String[] LIGHT = {"#168A55", "#2478D8", "#7651D6", "#C65A18", "#C63F77", "#007F86", "#BF3546", "#BB4B3C", "#AC5548", "#986600", "#8F711B", "#637D16", "#73742E", "#56733C", "#247E60", "#267D73", "#087C61", "#336749", "#157C91", "#087DAD", "#2774AE", "#4C5AC1", "#3A577F", "#8B43B7", "#7960A1", "#AF389D", "#A85270", "#903D5D", "#805B42", "#586C7D"};
    private static final String[] DARK = {"#45C98A", "#4DA3FF", "#A88BFF", "#FF9A5A", "#FF78A8", "#47C6CA", "#FF7785", "#FF8E7A", "#F4A38F", "#F1B847", "#E1C464", "#BDD45D", "#B9BC71", "#98BA7C", "#7DD9B2", "#72D0BC", "#3DD2A4", "#86B99B", "#68CEDF", "#52C8F3", "#8AC7FF", "#919FFF", "#8DAEDB", "#C68BF1", "#C8ABE7", "#EF80DB", "#DE9DB3", "#D38BA8", "#C5A080", "#A2B6C7"};
    private static final String[] SOFT_LIGHT = {"#E1F4E9", "#E7F1FF", "#EEE8FF", "#FFE9DA", "#FFE5F0", "#DDF3F3", "#F9EBED", "#F8EDEC", "#F7EEED", "#F5F0E6", "#F4F1E8", "#EFF2E8", "#F1F1EA", "#EEF1EC", "#E9F2EF", "#E9F2F1", "#E6F2EF", "#EBF0ED", "#E8F2F4", "#E6F2F7", "#E9F1F7", "#EDEFF9", "#EBEEF2", "#F3ECF8", "#F2EFF6", "#F7EBF5", "#F6EEF1", "#F4ECEF", "#F2EFEC", "#EEF0F2"};
    private static final String[] SOFT_DARK = {"#142D22", "#162235", "#261F3D", "#352217", "#351D29", "#163033", "#34242C", "#34272A", "#322A2D", "#322D22", "#2F2F27", "#2A3226", "#292E29", "#242E2A", "#203233", "#1F3134", "#173130", "#222E2F", "#1D3139", "#1A303C", "#22303E", "#232A3E", "#232C39", "#2B273C", "#2C2C3A", "#312539", "#2F2933", "#2D2731", "#2B2A2B", "#262D36"};
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("appearance", Context.MODE_PRIVATE); }
    static int selected(Context c) { return Math.max(0, Math.min(LABELS.length - 1, prefs(c).getInt("color", 0))); }
    static int percent(Context c) { return Math.max(70, Math.min(150, prefs(c).getInt("text", 100))); }
    static boolean dark(Context c) { return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    static int primary(Context c) { return primary(c, selected(c)); }
    static int primary(Context c, int index) { return Color.parseColor((dark(c) ? DARK : LIGHT)[index]); }
    static int soft(Context c) { return soft(c, selected(c)); }
    static int soft(Context c, int index) { return Color.parseColor((dark(c) ? SOFT_DARK : SOFT_LIGHT)[index]); }
    static int ripple(Context c) { return androidx.core.graphics.ColorUtils.setAlphaComponent(primary(c), 50); }
    static int theme(Context c) { return new int[]{R.style.Theme_Falai, R.style.Theme_Falai_Blue, R.style.Theme_Falai_Purple, R.style.Theme_Falai_Orange, R.style.Theme_Falai_Pink, R.style.Theme_Falai_Teal, R.style.Theme_Falai_Red, R.style.Theme_Falai_Coral, R.style.Theme_Falai_Salmon, R.style.Theme_Falai_Amber, R.style.Theme_Falai_Gold, R.style.Theme_Falai_Lime, R.style.Theme_Falai_Olive, R.style.Theme_Falai_Moss, R.style.Theme_Falai_Mint, R.style.Theme_Falai_Jade, R.style.Theme_Falai_Emerald, R.style.Theme_Falai_Forest, R.style.Theme_Falai_Aqua, R.style.Theme_Falai_Cyan, R.style.Theme_Falai_Sky, R.style.Theme_Falai_Indigo, R.style.Theme_Falai_Navy, R.style.Theme_Falai_Violet, R.style.Theme_Falai_Lavender, R.style.Theme_Falai_Magenta, R.style.Theme_Falai_Rose, R.style.Theme_Falai_Wine, R.style.Theme_Falai_Brown, R.style.Theme_Falai_Slate}[selected(c)]; }
    private Appearance() { }
}
