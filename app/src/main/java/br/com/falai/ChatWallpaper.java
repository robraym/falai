package br.com.falai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.core.graphics.PathParser;

/** A quiet, repeating doodle wallpaper drawn as vectors, without a large bitmap. */
final class ChatWallpaper extends Drawable {
    private final Paint background = new Paint(), ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tile = new Path();
    private final float density;

    ChatWallpaper(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        background.setColor(context.getColor(R.color.chat_wallpaper));
        ink.setColor(context.getColor(R.color.chat_wallpaper_ink));
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1.05f);
        ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
        add("M4,3h16a2,2 0,0 1,2 2v12a2,2 0,0 1,-2 2H9l-6,4V5a2,2 0,0 1,1 -2 M7,8h10 M7,12h7", 16, 15, 1.15f, -12);
        add("M12,21C9,18 2,14 2,8C2,2 9,1 12,6C15,1 22,2 22,8C22,14 15,18 12,21Z", 88, 10, 1.1f, 14);
        add("M12,2l3,6l7,1l-5,5l1,7l-6,-3l-6,3l1,-7l-5,-5l7,-1Z", 159, 25, 0.8f, -18);
        add("M22,12a10,10 0,1 1,-20 0a10,10 0,1 1,20 0 M7,9h0.5 M16,9h0.5 M7,14q5,6 10,0", 65, 70, 1.05f, -10);
        add("M2,10L22,2L15,22L10,14Z M10,14L22,2 M10,14L8,21L13,18", 128, 73, 1.1f, 14);
        add("M4,8h14v10a4,4 0,0 1,-4 4H8a4,4 0,0 1,-4 -4Z M18,9h2a3,3 0,0 1,0 6h-2 M7,4q-2,-2 0,-4 M12,4q-2,-2 0,-4", 14, 116, 1.05f, -14);
        add("M8,18V5l12,-3v13 M8,5l12,-3 M8,9l12,-3 M8,18a3,2 0,1 1,-6 0a3,2 0,1 1,6 0 M20,15a3,2 0,1 1,-6 0a3,2 0,1 1,6 0", 92, 137, 1, 12);
        add("M17,2a10,10 0,1 0,5 16A10,10 0,0 1,17 2Z", 159, 144, 1, -15);
        add("M2,6h8 M6,2v8 M16,16h6 M19,13v6", 48, 172, 0.8f, 10);
        add("M2,6h8 M6,2v8 M16,16h6 M19,13v6", 157, 111, 0.65f, -10);
        add("M2,6h8 M6,2v8", 39, 69, 0.65f, 0);
        add("M2,6h8 M6,2v8", 125, 185, 0.6f, 15);
    }

    private void add(String data, float x, float y, float scale, float angle) {
        Path path = PathParser.createPathFromPathData(data);
        Matrix transform = new Matrix(); transform.setScale(scale, scale); transform.postRotate(angle); transform.postTranslate(x, y);
        tile.addPath(path, transform);
    }
    @Override public void draw(@NonNull Canvas canvas) {
        canvas.save(); canvas.clipRect(getBounds()); canvas.drawRect(getBounds(), background);
        canvas.translate(getBounds().left, getBounds().top); canvas.scale(density, density);
        float width = getBounds().width() / density, height = getBounds().height() / density;
        for (int y = -200; y < height; y += 200) {
            for (int x = -200; x < width; x += 200) {
                canvas.save(); canvas.translate(x, y); canvas.drawPath(tile, ink); canvas.restore();
            }
        }
        canvas.restore();
    }
    @Override public void setAlpha(int alpha) { background.setAlpha(alpha); ink.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { background.setColorFilter(filter); ink.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
