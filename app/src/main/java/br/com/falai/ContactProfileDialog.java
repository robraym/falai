package br.com.falai;

import android.content.Context;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;

/** Public contact details, without opening or creating a conversation. */
final class ContactProfileDialog {
    static void show(Context context, String uid, String name) {
        LinearLayout content = Ui.column(context); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(context, 24), Ui.dp(context, 28), Ui.dp(context, 24), Ui.dp(context, 12));
        AvatarView photo = new AvatarView(context); photo.load(uid, name);
        photo.setContentDescription(context.getString(R.string.view_profile_photo));
        photo.setOnClickListener(view -> showPhoto(context, uid));
        content.addView(photo, new LinearLayout.LayoutParams(Ui.dp(context, 108), Ui.dp(context, 108)));
        ProfileNameView title = new ProfileNameView(context); title.setTextSize(22); title.setTextColor(context.getColor(R.color.text_primary)); title.bind(uid, name, resolved -> photo.load(uid, resolved)); title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 8)); content.addView(title);
        AboutTextView phrase = new AboutTextView(context); phrase.setTextSize(15);
        phrase.setLineSpacing(Ui.dp(context, 3), 1);
        phrase.setTextColor(context.getColor(R.color.text_secondary)); phrase.setGravity(Gravity.CENTER);
        phrase.bind(uid, ""); content.addView(phrase, new LinearLayout.LayoutParams(-1, -2));
        AppVersionView version = new AppVersionView(context, uid); version.setGravity(Gravity.CENTER); version.setTextSize(12);
        version.setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12));
        version.setBackground(Ui.shape(context, R.color.input_background, 14));
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, -2);
        versionParams.topMargin = Ui.dp(context, 22); content.addView(version, versionParams);
        ScrollView scroll = new ScrollView(context); scroll.setVerticalScrollBarEnabled(false); scroll.addView(content);
        AlertDialog.Builder dialog = new AlertDialog.Builder(context).setView(scroll).setPositiveButton(android.R.string.ok, null);
        dialog.show();
    }
    private static void showPhoto(Context context, String uid) {
        Bitmap bitmap = AvatarView.cachedPhoto(uid);
        if (bitmap == null) return;
        Dialog viewer = new Dialog(context, Appearance.theme(context));
        viewer.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout surface = new FrameLayout(context); surface.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(context); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap); image.setContentDescription(context.getString(R.string.profile_photo));
        surface.addView(image, new FrameLayout.LayoutParams(-1, -1));
        ImageButton back = new ImageButton(context); back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        back.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), new ColorDrawable(Color.TRANSPARENT), null));
        back.setContentDescription(context.getString(R.string.back)); back.setOnClickListener(view -> viewer.dismiss());
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48), Gravity.TOP | Gravity.START);
        position.setMargins(Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8));
        surface.addView(back, position); viewer.setContentView(surface);
        Window window = viewer.getWindow();
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(surface, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        viewer.show(); window.setBackgroundDrawable(new ColorDrawable(Color.BLACK)); window.setLayout(-1, -1);
        androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat.getInsetsController(window, surface);
        bars.setAppearanceLightStatusBars(false); bars.setAppearanceLightNavigationBars(false);
        androidx.core.view.ViewCompat.requestApplyInsets(surface);
    }
    private ContactProfileDialog() { }
}
