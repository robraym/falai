package br.com.falai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class ProfilePhotos {
    static Task<String> prepare(Context context, Uri uri) {
        return Tasks.call(ContactsRepository.IO, () -> {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, options); }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IllegalArgumentException("image");
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap source;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) { source = BitmapFactory.decodeStream(input, null, options); }
            if (source == null) throw new IllegalArgumentException("image");
            int side = Math.min(source.getWidth(), source.getHeight());
            Bitmap crop = Bitmap.createBitmap(source, (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
            Bitmap small = Bitmap.createScaledBitmap(crop, 192, 192, true);
            if (crop != source && crop != small) crop.recycle(); if (source != small) source.recycle();
            try {
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    int orientation = new androidx.exifinterface.media.ExifInterface(input).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1);
                    Matrix matrix = new Matrix();
                    switch (orientation) {
                        case 2: matrix.setScale(-1, 1); break;
                        case 3: matrix.setRotate(180); break;
                        case 4: matrix.setScale(1, -1); break;
                        case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                        case 6: matrix.setRotate(90); break;
                        case 7: matrix.setRotate(270); matrix.postScale(-1, 1); break;
                        case 8: matrix.setRotate(270); break;
                        default: break;
                    }
                    if (!matrix.isIdentity()) { Bitmap rotated = Bitmap.createBitmap(small, 0, 0, 192, 192, matrix, true); if (rotated != small) { small.recycle(); small = rotated; } }
                } catch (java.io.IOException ignored) { }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                for (int quality = 85; quality >= 25; quality -= 15) {
                    output.reset(); small.compress(Bitmap.CompressFormat.JPEG, quality, output);
                    if (output.size() <= 16384) return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                }
                throw new IllegalArgumentException("image-size");
            } finally { small.recycle(); }
        });
    }
    static Bitmap decode(String encoded) {
        if (encoded == null || encoded.length() > 22000) return null;
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 256 || bounds.outHeight > 256) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException error) { return null; }
    }
    private ProfilePhotos() { }
}
