package br.com.falai;

import android.content.Context;
import android.graphics.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.Source;
import java.util.concurrent.TimeUnit;

/** Small bounded avatars; local disk lookup is bounded and never requests the network. */
final class NotificationAvatar {
    static boolean valid(String uid) { return uid != null && uid.matches("[A-Za-z0-9_-]{1,128}"); }
    static boolean refresh(String uid) {
        if (!valid(uid) || AvatarView.photoFresh(uid)) return false;
        try {
            com.google.firebase.firestore.DocumentSnapshot doc = Tasks.await(
                Backend.db().collection("profilePhotos").document(uid).get(Source.SERVER), 2, TimeUnit.SECONDS);
            AvatarView.cachePhoto(uid, ProfilePhotos.decode(doc.getString("photo"))); return true;
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); return false; }
        catch (Exception error) { return false; }
    }
    static Bitmap bitmap(Context context, String uid, String name) {
        int size = 128; float radius = size / 2f;
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap photo = valid(uid) ? AvatarView.cachedPhoto(uid) : null;
        if (photo == null && valid(uid)) {
            try {
                com.google.firebase.firestore.DocumentSnapshot saved = Tasks.await(
                        Backend.db().collection("profilePhotos").document(uid).get(Source.CACHE), 250, TimeUnit.MILLISECONDS);
                photo = ProfilePhotos.decode(saved.getString("photo"));
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (Exception ignored) { }
        }
        if (photo != null) {
            BitmapShader shader = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(size / (float) photo.getWidth(), size / (float) photo.getHeight());
            Matrix matrix = new Matrix(); matrix.setScale(scale, scale);
            matrix.postTranslate((size - photo.getWidth() * scale) / 2f, (size - photo.getHeight() * scale) / 2f);
            shader.setLocalMatrix(matrix); paint.setShader(shader); canvas.drawCircle(radius, radius, radius, paint);
        } else {
            paint.setColor(Appearance.soft(context)); canvas.drawCircle(radius, radius, radius, paint);
            paint.setColor(Appearance.primary(context)); paint.setTextSize(52); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
            String initial = name == null || name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
            canvas.drawText(initial, radius, radius - (paint.ascent() + paint.descent()) / 2, paint);
        }
        return result;
    }
    private NotificationAvatar() { }
}
