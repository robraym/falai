package br.com.falai;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

/** Public contact details, without opening or creating a conversation. */
final class ContactProfileDialog {
    static void show(Context context, String uid, String name) {
        LinearLayout content = Ui.column(context); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(context, 24), Ui.dp(context, 24), Ui.dp(context, 24), Ui.dp(context, 16));
        AvatarView photo = new AvatarView(context); photo.load(uid, name);
        content.addView(photo, new LinearLayout.LayoutParams(Ui.dp(context, 96), Ui.dp(context, 96)));
        TextView title = Ui.text(context, name, 22, false); title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 8)); content.addView(title);
        AboutTextView phrase = new AboutTextView(context); phrase.setTextSize(16);
        phrase.setTextColor(context.getColor(R.color.text_secondary)); phrase.setGravity(Gravity.CENTER);
        phrase.bind(uid, ""); content.addView(phrase, new LinearLayout.LayoutParams(-1, -2));
        AppVersionView version = new AppVersionView(context, uid); version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, -2);
        versionParams.topMargin = Ui.dp(context, 16); content.addView(version, versionParams);
        new AlertDialog.Builder(context).setView(content).setPositiveButton(android.R.string.ok, null).show();
    }
    private ContactProfileDialog() { }
}
