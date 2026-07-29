package com.dlnaclock.screensaver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.dlnaclock.util.PreferenceHelper;

import java.io.File;

public class BackgroundManager {

    private static final String TAG = "BackgroundManager";
    private static final int MODE_COLOR = 0;
    private static final int MODE_IMAGE = 1;
    private static final int MODE_VIDEO = 2;

    private Context context;
    private int mode;
    private int backgroundColor;
    private Bitmap backgroundImage;
    private MediaPlayer videoPlayer;
    private SurfaceView videoSurface;
    private Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public BackgroundManager(Context context) {
        this.context = context;
        loadConfig();
    }

    private void loadConfig() {
        mode = PreferenceHelper.getBackgroundMode();
        backgroundColor = PreferenceHelper.getBackgroundColor();

        if (mode == MODE_IMAGE) {
            String path = PreferenceHelper.getBackgroundImagePath();
            if (path != null && !path.isEmpty() && new File(path).exists()) {
                try {
                    backgroundImage = BitmapFactory.decodeFile(path);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load background image", e);
                    backgroundImage = null;
                }
            }
        }
    }

    public void reloadConfig() {
        release();
        loadConfig();
    }

    public void draw(Canvas canvas, int width, int height) {
        switch (mode) {
            case MODE_COLOR:
                canvas.drawColor(backgroundColor);
                break;

            case MODE_IMAGE:
                canvas.drawColor(Color.BLACK);
                if (backgroundImage != null) {
                    drawBitmapCenterCrop(canvas, backgroundImage, width, height);
                }
                break;

            case MODE_VIDEO:
                // Video is drawn on SurfaceView, canvas just draws black
                canvas.drawColor(Color.BLACK);
                break;
        }
    }

    private void drawBitmapCenterCrop(Canvas canvas, Bitmap bitmap, int canvasWidth, int canvasHeight) {
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scaleX = canvasWidth / bitmapWidth;
        float scaleY = canvasHeight / bitmapHeight;
        float scale = Math.max(scaleX, scaleY);

        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;

        float left = (canvasWidth - scaledWidth) / 2f;
        float top = (canvasHeight - scaledHeight) / 2f;

        RectF destRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
        canvas.drawBitmap(bitmap, null, destRect, bitmapPaint);
    }

    public void setupVideoBackground(SurfaceView surfaceView) {
        if (mode != MODE_VIDEO) return;

        this.videoSurface = surfaceView;
        String videoPath = PreferenceHelper.getBackgroundVideoPath();
        if (videoPath == null || videoPath.isEmpty() || !new File(videoPath).exists()) {
            return;
        }

        try {
            videoPlayer = new MediaPlayer();
            videoPlayer.setDataSource(videoPath);
            videoPlayer.setSurface(surfaceView.getHolder().getSurface());
            videoPlayer.setLooping(true);
            videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            videoPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup video background", e);
        }
    }

    public void release() {
        if (videoPlayer != null) {
            try {
                videoPlayer.stop();
                videoPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            videoPlayer = null;
        }
        if (backgroundImage != null && !backgroundImage.isRecycled()) {
            backgroundImage.recycle();
            backgroundImage = null;
        }
    }

    public int getMode() { return mode; }
    public boolean isVideoMode() { return mode == MODE_VIDEO; }
}
