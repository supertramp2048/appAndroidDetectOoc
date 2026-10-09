package com.socialapp.core.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class ImageUtils {

    private static final String TAG = "ImageUtils";

    private ImageUtils() {
    }

    @Nullable
    public static File copyUriToFile(@NonNull Context context, @NonNull Uri uri, @NonNull File outputFile) {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(outputFile)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return outputFile;
        } catch (Exception e) {
            Log.e(TAG, "Error copying URI to File", e);
            return null;
        }
    }

    @Nullable
    public static File compressImageFile(@NonNull File inputFile, @NonNull File outputFile, int maxDimension, int quality) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(inputFile.getAbsolutePath(), options);

            int width = options.outWidth;
            int height = options.outHeight;
            int sampleSize = 1;

            while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
                sampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;

            Bitmap bitmap = BitmapFactory.decodeFile(inputFile.getAbsolutePath(), options);
            if (bitmap == null) return null;

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
                out.flush();
            }
            bitmap.recycle();

            return outputFile;
        } catch (Exception e) {
            Log.e(TAG, "Error compressing image file", e);
            return null;
        }
    }
}
