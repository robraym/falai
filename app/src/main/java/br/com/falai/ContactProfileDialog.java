package br.com.falai;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

/** Public contact details, without opening or creating a conversation. */
final class ContactProfileDialog {
    static void show(Context context, String uid, String name) {
        show(context, uid, name, null);
    }
    static void show(Context context, String uid, String name, Runnable timerOptions) {
        LinearLayout content = Ui.column(context); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(context, 24), Ui.dp(context, 24), Ui.dp(context, 24), Ui.dp(context, 16));
        AvatarView photo = new AvatarView(context); photo.load(uid, name);
        content.addView(photo, new LinearLayout.LayoutParams(Ui.dp(context, 96), Ui.dp(context, 96)));
        ProfileNameView title = new ProfileNameView(context); title.setTextSize(22); title.setTextColor(context.getColor(R.color.text_primary)); title.bind(uid, name, resolved -> photo.load(uid, resolved)); title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 8)); content.addView(title);
        AboutTextView phrase = new AboutTextView(context); phrase.setTextSize(16);
        phrase.setTextColor(context.getColor(R.color.text_secondary)); phrase.setGravity(Gravity.CENTER);
        phrase.bind(uid, ""); content.addView(phrase, new LinearLayout.LayoutParams(-1, -2));
        AppVersionView version = new AppVersionView(context, uid); version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, -2);
        versionParams.topMargin = Ui.dp(context, 16); content.addView(version, versionParams);
        AlertDialog.Builder dialog = new AlertDialog.Builder(context).setView(content).setPositiveButton(android.R.string.ok, null);
        if (timerOptions != null) dialog.setNeutralButton(R.string.chat_timer_title, (ignored, button) -> timerOptions.run());
        dialog.show();
    }
    private ContactProfileDialog() { }
}
