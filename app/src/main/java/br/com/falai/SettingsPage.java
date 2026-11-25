package br.com.falai;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.LinearLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** Full-screen settings surface sharing the Profile header and back navigation. */
class SettingsPage extends Dialog {
    final LinearLayout body;
    private final FalaiActivity activity;
    private final LinearLayout root;
    private final DefaultLifecycleObserver lifecycle = new DefaultLifecycleObserver() {
        @Override public void onDestroy(LifecycleOwner owner) { dismiss(); }
    };
    SettingsPage(FalaiActivity activity, int title) {
        super(activity, Appearance.theme(activity));
        this.activity = activity;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = Ui.column(activity); root.setBackgroundColor(activity.getColor(R.color.background));
        root.addView(Ui.backHeading(activity, activity.getString(title), view -> dismiss()));
        body = Ui.column(activity); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); setCanceledOnTouchOutside(false);
        getWindow().setWindowAnimations(R.style.SettingsPageAnimations);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int side = activity.getResources().getDimensionPixelSize(R.dimen.navigation_side_padding);
            view.setPadding(side + bars.left, bars.top, side + bars.right,
                    Math.max(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom));
            return insets;
        });
    }
    @Override protected void onStart() {
        super.onStart();
        activity.getLifecycle().addObserver(lifecycle);
        getWindow().setBackgroundDrawable(new ColorDrawable(activity.getColor(R.color.background)));
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setLayout(-1, -1);
        androidx.core.view.WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), root);
        boolean light = activity.getResources().getBoolean(R.bool.light_system_bars);
        bars.setAppearanceLightStatusBars(light); bars.setAppearanceLightNavigationBars(light);
        ViewCompat.requestApplyInsets(root);
    }
    @Override public void dismiss() {
        activity.getLifecycle().removeObserver(lifecycle);
        super.dismiss();
    }
    static int optionColumns(android.content.Context context) {
        int available = context.getResources().getConfiguration().screenWidthDp - 64;
        return available >= 540 ? 3 : available >= 240 ? 2 : 1;
    }
    static void recreateAfterExit(FalaiActivity activity) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) activity.recreate();
        }, 260);
    }
}
