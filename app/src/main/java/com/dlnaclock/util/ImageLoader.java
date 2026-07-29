package com.dlnaclock.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {

    private static ImageLoader instance;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private ImageLoader() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        executor = Executors.newFixedThreadPool(3);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ImageLoader getInstance() {
        if (instance == null) {
            instance = new ImageLoader();
        }
        return instance;
    }

    public void load(final String url, final ImageView imageView) {
        if (url == null || url.isEmpty()) {
            imageView.setImageBitmap(null);
            return;
        }

        Bitmap cached = memoryCache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = downloadImage(url);
                    if (bitmap != null) {
                        memoryCache.put(url, bitmap);
                        final Bitmap finalBitmap = bitmap;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(finalBitmap);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void loadAsync(final String url, final ImageLoadCallback callback) {
        if (url == null || url.isEmpty()) {
            if (callback != null) callback.onLoaded(null);
            return;
        }

        Bitmap cached = memoryCache.get(url);
        if (cached != null) {
            if (callback != null) callback.onLoaded(cached);
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = downloadImage(url);
                    if (bitmap != null) {
                        memoryCache.put(url, bitmap);
                    }
                    final Bitmap finalBitmap = bitmap;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onLoaded(finalBitmap);
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onLoaded(null);
                        }
                    });
                }
            }
        });
    }

    private Bitmap downloadImage(String urlStr) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setDoInput(true);
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public interface ImageLoadCallback {
        void onLoaded(Bitmap bitmap);
    }
}
