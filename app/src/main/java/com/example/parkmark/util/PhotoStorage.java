package com.example.parkmark.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

// Copy images into private storage so gallery deletion or revoked URI access cannot lose new photos.
public final class PhotoStorage {
    private PhotoStorage() { }

    private static InputStream open(Context context, String source) throws IOException {
        if (source.startsWith("photos/")) return new java.io.FileInputStream(new File(context.getFilesDir(), source));
        InputStream stream = context.getContentResolver().openInputStream(Uri.parse(source));
        if (stream == null) throw new IOException("Image is unavailable");
        return stream;
    }

    // Decode a bounded image and honor camera orientation instead of loading full-size camera bitmaps.
    public static Bitmap decode(Context context, String source, int maximum) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = open(context, source)) { BitmapFactory.decodeStream(input, null, options); }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Unsupported image");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > maximum) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap;
        try (InputStream input = open(context, source)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
        if (bitmap == null) throw new IOException("Cannot decode image");
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream input = open(context, source)) {
            orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) { /* Some image formats have no EXIF metadata. */ }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            default: return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    public static String importPhoto(Context context, Uri uri) throws IOException {
        Bitmap bitmap = decode(context, uri.toString(), 1600);
        File directory = new File(context.getFilesDir(), "photos");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create photo folder");
        String path = "photos/" + UUID.randomUUID() + ".jpg";
        File file = new File(context.getFilesDir(), path);
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) throw new IOException("Cannot save photo");
        } catch (IOException error) { file.delete(); throw error; }
        finally { bitmap.recycle(); }
        return path;
    }

    // Remove only app-owned files; never delete an image from the user's gallery.
    public static void delete(Context context, String path) {
        if (path.startsWith("photos/") && !path.contains("..")) new File(context.getFilesDir(), path).delete();
    }
}
