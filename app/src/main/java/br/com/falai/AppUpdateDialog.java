package br.com.falai;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

/** Full screen update surface; download and installation remain owned by the controller. */
final class AppUpdateDialog extends Dialog {
    final TextView message, detail;
    final ProgressBar progress;
    private final FalaiActivity activity;
    private final TextView available, percentage, transferSize;
    private final AppVersionArtwork artwork;
    private final LinearLayout progressGroup;
    private final Button primary, close, cancel;

    AppUpdateDialog(FalaiActivity activity) {
        super(activity, Appearance.theme(activity));
        this.activity = activity;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = Ui.column(activity);
        root.setBackgroundColor(activity.getColor(R.color.background));
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout toolbar = Ui.row(activity);
        toolbar.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), 0);
        ImageButton back = new ImageButton(activity);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.text_primary)));
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(Appearance.ripple(activity)), null, Ui.shape(activity, R.color.card, 24)));
        back.setContentDescription(activity.getString(R.string.sound_close));
        back.setOnClickListener(view -> dismiss());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout hero = new LinearLayout(activity) {
            private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void dispatchDraw(Canvas canvas) {
                float radius = Math.min(getWidth() * .65f, Ui.dp(activity, 260));
                int accent = Appearance.primary(activity);
                glow.setShader(new RadialGradient(getWidth() / 2f, getHeight() * .48f, radius,
                        new int[]{androidx.core.graphics.ColorUtils.setAlphaComponent(accent, Appearance.dark(activity) ? 35 : 24), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, getWidth(), getHeight(), glow);
                super.dispatchDraw(canvas);
            }
        };
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(Ui.dp(activity, 28), Ui.dp(activity, 32), Ui.dp(activity, 28), Ui.dp(activity, 32));
        artwork = new AppVersionArtwork(activity);
        hero.addView(artwork, new LinearLayout.LayoutParams(-1, -2));
        message = centered(18, false);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        message.setPadding(0, Ui.dp(activity, 14), 0, 0); hero.addView(message);
        available = centered(13, false); available.setPadding(0, Ui.dp(activity, 10), 0, 0);
        available.setVisibility(View.GONE); hero.addView(available);
        progressGroup = Ui.column(activity); progressGroup.setPadding(0, Ui.dp(activity, 24), 0, 0);
        percentage = centered(18, false); percentage.setTextColor(Appearance.primary(activity)); progressGroup.addView(percentage);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(Appearance.primary(activity)));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Appearance.soft(activity)));
        progress.setIndeterminateTintList(ColorStateList.valueOf(Appearance.primary(activity)));
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 4)); bar.topMargin = Ui.dp(activity, 10);
        progressGroup.addView(progress, bar);
        transferSize = centered(12, false); transferSize.setPadding(0, Ui.dp(activity, 8), 0, 0); progressGroup.addView(transferSize);
        hero.addView(progressGroup, new LinearLayout.LayoutParams(-1, -2)); progressGroup.setVisibility(View.GONE);
        detail = centered(12, false); detail.setPadding(0, Ui.dp(activity, 14), 0, 0); hero.addView(detail);
        scroll.addView(hero, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = Ui.column(activity); actions.setGravity(Gravity.CENTER_HORIZONTAL);
        actions.setPadding(Ui.dp(activity, 32), Ui.dp(activity, 12), Ui.dp(activity, 32), Ui.dp(activity, 28));
        primary = action(R.string.check_apk_update, true);
        close = action(R.string.sound_close, false);
        cancel = action(R.string.cancel_apk_download, false);
        actions.addView(primary, new LinearLayout.LayoutParams(-2, -2));
        actions.addView(close, new LinearLayout.LayoutParams(-2, -2));
        actions.addView(cancel, new LinearLayout.LayoutParams(-2, -2));
        close.setVisibility(View.GONE);
        close.setOnClickListener(view -> dismiss()); cancel.setOnClickListener(view -> dismiss());
        root.addView(actions);
        setContentView(root); setCanceledOnTouchOutside(false);
    }
    private TextView centered(int size, boolean bold) {
        TextView text = Ui.text(activity, "", size, bold); text.setGravity(Gravity.CENTER); return text;
    }
    private Button action(int label, boolean filled) {
        Button button = new androidx.appcompat.widget.AppCompatButton(activity);
        button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setStateListAnimator(null);
        button.setElevation(0); button.setTranslationZ(0);
        button.setMinHeight(Ui.dp(activity, 48)); button.setMinimumHeight(Ui.dp(activity, 48));
        button.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 12), Ui.dp(activity, 24), Ui.dp(activity, 12));
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Appearance.ripple(activity)),
                filled ? Ui.shape(activity, R.color.action_green_soft, 28) : new ColorDrawable(Color.TRANSPARENT), null));
        button.setTextColor(filled ? Appearance.primary(activity) : activity.getColor(R.color.text_secondary));
        return button;
    }
    Button getButton(int which) { return which == AlertDialog.BUTTON_POSITIVE ? primary : which == AlertDialog.BUTTON_NEGATIVE ? cancel : close; }
    void setMessage(CharSequence text) {
        boolean upToDate = activity.getString(R.string.apk_up_to_date).contentEquals(text == null ? "" : text);
        message.setSingleLine(upToDate);
        if (upToDate) {
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(message, 10, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        } else {
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(message, androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
            message.setTextSize(18);
        }
        message.setText(text); message.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
    }
    void setInstalledDetails(String text) { artwork.setVersion(text); }
    void setAvailableDetails(String text) { available.setText(text); available.setVisibility(View.VISIBLE); }
    void setProgressState(boolean indeterminate, int percent, CharSequence size) {
        progressGroup.setVisibility(View.VISIBLE); progress.setIndeterminate(indeterminate); progress.setProgress(percent);
        percentage.setText(indeterminate ? "" : activity.getString(R.string.apk_update_percent, percent));
        percentage.setVisibility(indeterminate ? View.GONE : View.VISIBLE);
        transferSize.setText(size); transferSize.setVisibility(size == null || size.length() == 0 ? View.GONE : View.VISIBLE);
    }
    void hideProgress() { progressGroup.setVisibility(View.GONE); }

    @Override protected void onStart() {
        super.onStart();
        if (getWindow() == null) return;
        getWindow().setBackgroundDrawable(new ColorDrawable(activity.getColor(R.color.background)));
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setLayout(-1, -1);
        androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(!Appearance.dark(activity));
        bars.setAppearanceLightNavigationBars(!Appearance.dark(activity));
        androidx.core.view.ViewCompat.requestApplyInsets(getWindow().getDecorView());
    }
}
