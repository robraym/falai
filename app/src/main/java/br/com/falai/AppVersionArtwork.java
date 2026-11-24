package br.com.falai;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared logo and inline app/version title for startup and updates. */
public final class AppVersionArtwork extends LinearLayout {
    private final TextView title;

    public AppVersionArtwork(Context context) { this(context, null); }
    public AppVersionArtwork(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public AppVersionArtwork(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(logo, new LayoutParams(Ui.dp(context, 124), Ui.dp(context, 124)));
        title = Ui.text(context, "", 28, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(title, 18, 28, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        LayoutParams position = new LayoutParams(-1, -2);
        position.topMargin = Ui.dp(context, 14);
        addView(title, position);
        setVersion(BuildConfig.VERSION_NAME);
    }

    void setVersion(String version) {
        String[] parts = version.split("\\.");
        String shortVersion = parts.length > 1 ? parts[0] + "." + parts[1] : version;
        title.setText(getContext().getString(R.string.app_version_title, getContext().getString(R.string.app_name), shortVersion));
    }
}
