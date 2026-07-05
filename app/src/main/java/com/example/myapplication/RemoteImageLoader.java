package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class RemoteImageLoader {
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 8)
    ) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private RemoteImageLoader() {
    }

    public static boolean isRemoteUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    public static void load(ImageView imageView, String imageUrl, int placeholderResId) {
        if (!isRemoteUrl(imageUrl)) {
            imageView.setImageResource(placeholderResId);
            return;
        }

        Bitmap cached = CACHE.get(imageUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setTag(imageUrl);
        imageView.setImageResource(placeholderResId);

        Request request = new Request.Builder()
                .url(imageUrl)
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                MAIN_HANDLER.post(() -> {
                    if (imageUrl.equals(imageView.getTag())) {
                        imageView.setImageResource(placeholderResId);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    response.close();
                    onFailure(call, new IOException("Unexpected image response"));
                    return;
                }

                try (Response closeableResponse = response;
                     InputStream stream = closeableResponse.body().byteStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) {
                        CACHE.put(imageUrl, bitmap);
                        MAIN_HANDLER.post(() -> {
                            if (imageUrl.equals(imageView.getTag())) {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (IOException e) {
                    onFailure(call, e);
                }
            }
        });
    }
}
