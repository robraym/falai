package br.com.falai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Compresses photos and keeps downloaded copies in private app storage. */
final class ChatMedia {
    static final int MAX_BYTES = 512 * 1024;
    private ChatMedia() { }
    static File file(Context context, String messageId) {
        if (messageId == null || !messageId.matches("[a-f0-9-]{36}")) return null;
        return new File(new File(context.getFilesDir(), "chat_media"), messageId + ".jpg");
    }
    static byte[] prepare(Context context, Uri uri, String messageId) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, bounds); }
        if (bounds.outWidth < 1 || bounds.outHeight < 1) throw new IllegalArgumentException("invalid-image");
        int sample = 1; while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
        if (bitmap == null) throw new IllegalArgumentException("invalid-image");
        try {
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input != null) orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (Exception ignored) { }
            int degrees = orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (degrees != 0) { Matrix matrix = new Matrix(); matrix.postRotate(degrees); Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true); if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated; } }
            for (int attempt = 0; attempt < 5; attempt++) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int quality = Math.max(48, 88 - attempt * 10);
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
                byte[] bytes = output.toByteArray();
                if (bytes.length <= MAX_BYTES) { save(context, messageId, bytes); return bytes; }
                if (attempt >= 2 && Math.max(bitmap.getWidth(), bitmap.getHeight()) > 600) {
                    int width = Math.max(1, (int) (bitmap.getWidth() * .78f)), height = Math.max(1, (int) (bitmap.getHeight() * .78f));
                    Bitmap smaller = Bitmap.createScaledBitmap(bitmap, width, height, true); if (smaller != bitmap) { bitmap.recycle(); bitmap = smaller; }
                }
            }
            throw new IllegalArgumentException("image-too-large");
        } finally { if (!bitmap.isRecycled()) bitmap.recycle(); }
    }
    static synchronized void save(Context context, String messageId, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length < 4 || bytes.length > MAX_BYTES) throw new java.io.IOException("invalid-media");
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 2400 || bounds.outHeight > 2400
                || (long) bounds.outWidth * bounds.outHeight > 4_000_000L) throw new java.io.IOException("invalid-media");
        BitmapFactory.Options sample = new BitmapFactory.Options(); sample.inSampleSize = 4; sample.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap checked = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, sample);
        if (checked == null) throw new java.io.IOException("invalid-media"); checked.recycle();
        File file = file(context, messageId); if (file == null) throw new IllegalArgumentException("invalid-id");
        File parent = file.getParentFile(); if (!parent.exists() && !parent.mkdirs()) throw new java.io.IOException("storage-unavailable");
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); output.getFD().sync(); }
        trim(context, file);
    }
    static void delete(Context context, String messageId) { File file = file(context, messageId); if (file != null) file.delete(); }
    static byte[] read(Context context, String messageId) throws Exception {
        File file = file(context, messageId); if (file == null || !file.isFile() || file.length() > MAX_BYTES) throw new java.io.IOException("media-missing");
        try (java.io.FileInputStream input = new java.io.FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    static android.graphics.Bitmap preview(Context context, String messageId, int maxPixels) {
        File file = file(context, messageId); if (file == null || !file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1; while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxPixels) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }
    private static void trim(Context context, File newest) {
        File[] files = newest.getParentFile().listFiles((dir, name) -> name.endsWith(".jpg")); if (files == null) return;
        long size = 0; for (File item : files) size += item.length();
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (File item : files) { if (size <= 50L * 1024 * 1024) break; long length = item.length(); if (!item.equals(newest) && item.delete()) size -= length; }
    }
}
