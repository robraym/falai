package br.com.falai;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Full-screen chat photo viewer with a WhatsApp-like back affordance and pinch zoom. */
public final class PhotoViewerActivity extends FalaiActivity {
    private static final String EXTRA_MEDIA_ID = "mediaId";
    private ZoomImageView photo;

    static void open(Context context, String mediaId) {
        context.startActivity(new Intent(context, PhotoViewerActivity.class).putExtra(EXTRA_MEDIA_ID, mediaId));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.BLACK);

        String mediaId = getIntent().getStringExtra(EXTRA_MEDIA_ID);
        java.io.File file = ChatMedia.file(this, mediaId);
        if (file == null || !file.isFile()) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        photo = new ZoomImageView(this);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));

        ImageButton back = Ui.icon(this, R.drawable.ic_back, R.string.back, view -> finish());
        back.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        back.setBackground(Ui.shape(this, android.R.color.transparent, 24));
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.TOP | Gravity.START);
        root.addView(back, backParams);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            backParams.leftMargin = bars.left + Ui.dp(this, 8);
            backParams.topMargin = bars.top + Ui.dp(this, 8);
            back.setLayoutParams(backParams);
            return insets;
        });
        setContentView(root);
        androidx.core.view.WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        ViewCompat.requestApplyInsets(root);

        com.google.android.gms.tasks.Tasks.call(ContactsRepository.IO, () -> ChatMedia.preview(getApplicationContext(), mediaId, 1600))
                .addOnCompleteListener(this, task -> {
                    Bitmap bitmap = task.isSuccessful() ? task.getResult() : null;
                    if (isFinishing() || isDestroyed()) {
                        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                        return;
                    }
                    if (bitmap == null) {
                        Toast.makeText(this, R.string.photo_expired, Toast.LENGTH_SHORT).show();
                        finish();
                    } else photo.setImageBitmap(bitmap);
                });
    }

    @Override protected void onDestroy() {
        if (photo != null) photo.releaseBitmap();
        super.onDestroy();
    }
}
