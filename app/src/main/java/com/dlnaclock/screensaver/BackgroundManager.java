package com.dlnaclock.screensaver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.dlnaclock.util.PreferenceHelper;
import com.dlnaclock.screensaver.wallpaper.ParamStore;
import com.dlnaclock.screensaver.wallpaper.WallpaperRenderer;
import com.dlnaclock.screensaver.wallpaper.WallpaperFactory;

import java.io.File;

/**
 * BackgroundManager - 屏保背景管理器
 * 支持背景模式：纯色、静态图片（多种适应模式）、视频循环播放、动态壁纸
 */
public class BackgroundManager {

    private static final String TAG = "BackgroundManager";
    private static final int MODE_COLOR = 0;  // 纯色背景
    private static final int MODE_IMAGE = 1;  // 图片背景
    private static final int MODE_VIDEO = 2;  // 视频背景
    private static final int MODE_WALLPAPER = 3; // 动态壁纸

    // 图片适应模式
    public static final int FIT_CENTER_CROP = 0;   // 居中裁剪
    public static final int FIT_STRETCH = 1;       // 拉伸
    public static final int FIT_WIDTH = 2;         // 自适应宽度
    public static final int FIT_HEIGHT = 3;        // 自适应高度
    public static final int FIT_CENTER = 4;        // 居中（原始尺寸）

    private static final int MAX_DECODE_SIZE = 1920; // 最大解码尺寸限制

    private Context context;
    private int mode;
    private int backgroundColor;
    private int imageFitMode;
    private Bitmap backgroundImage;
    private MediaPlayer videoPlayer;
    private SurfaceView videoSurface;
    private Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    // 动态壁纸
    private WallpaperRenderer wallpaperRenderer;
    private long wallpaperStartTime;
    private Bundle savedWallpaperParams;  // 加载时保存的参数，init后应用
    private boolean wallpaperCrashed = false;  // 崩溃标记，触发重建

    // 壁纸渲染失败时的 ERROR 提示
    private Paint errorPaint;
    private Camera errorCamera;
    private Matrix errorMatrix;

    public BackgroundManager(Context context) {
        this.context = context;
        loadConfig();
    }

    private void loadConfig() {
        mode = PreferenceHelper.getBackgroundMode();
        backgroundColor = PreferenceHelper.getBackgroundColor();
        imageFitMode = PreferenceHelper.getBackgroundImageFit();

        if (mode == MODE_IMAGE) {
            String path = PreferenceHelper.getBackgroundImagePath();
            if (path != null && !path.isEmpty() && new File(path).exists()) {
                backgroundImage = decodeImageFile(path);
            }
        }

        if (mode == MODE_WALLPAPER) {
            int type = PreferenceHelper.getWallpaperType();
            try {
                wallpaperRenderer = WallpaperFactory.create(type);
                // 立即加载已保存的参数，供 initWallpaper 后应用
                if (wallpaperRenderer != null) {
                    savedWallpaperParams = ParamStore.loadParams(type, wallpaperRenderer.getParamDefs());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create wallpaper", e);
                wallpaperRenderer = null;
                savedWallpaperParams = null;
            }
            wallpaperStartTime = System.currentTimeMillis();
        }
    }

    /**
     * decodeImageFile - 健壮的图片解码，支持 JPG/PNG/WEBP/BMP/GIF
     * 1. 先获取尺寸计算 inSampleSize 避免 OOM
     * 2. 解码后处理 EXIF 旋转
     */
    private Bitmap decodeImageFile(String path) {
        try {
            // Step 1: 获取图片尺寸（不解码像素）
            BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
            boundsOpts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, boundsOpts);

            int srcWidth = boundsOpts.outWidth;
            int srcHeight = boundsOpts.outHeight;
            if (srcWidth <= 0 || srcHeight <= 0) {
                Log.e(TAG, "Invalid image bounds: " + path);
                return null;
            }

            // Step 2: 计算 inSampleSize
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = calculateInSampleSize(srcWidth, srcHeight, MAX_DECODE_SIZE, MAX_DECODE_SIZE);
            decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap = BitmapFactory.decodeFile(path, decodeOpts);
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode image: " + path);
                return null;
            }

            // Step 3: 处理 EXIF 旋转（主要针对 JPG）
            int rotation = getExifRotation(path);
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                    bitmap = rotated;
                }
            }

            return bitmap;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM decoding image: " + path, e);
            // 尝试更小的采样率
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 8;
                return BitmapFactory.decodeFile(path, opts);
            } catch (Exception e2) {
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load background image", e);
            return null;
        }
    }

    /** calculateInSampleSize - 计算采样率使解码后尺寸不超过 maxW×maxH */
    private int calculateInSampleSize(int srcWidth, int srcHeight, int maxW, int maxH) {
        int inSampleSize = 1;
        if (srcWidth > maxW || srcHeight > maxH) {
            int halfWidth = srcWidth / 2;
            int halfHeight = srcHeight / 2;
            while ((halfWidth / inSampleSize) >= maxW
                    && (halfHeight / inSampleSize) >= maxH) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /** getExifRotation - 读取 EXIF 旋转角度 */
    private int getExifRotation(String path) {
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
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
                    drawBitmapWithFit(canvas, backgroundImage, width, height, imageFitMode);
                }
                break;

            case MODE_VIDEO:
                // Video is drawn on SurfaceView, canvas just draws black
                canvas.drawColor(Color.BLACK);
                break;

            case MODE_WALLPAPER:
                if (wallpaperCrashed) {
                    // 上次崩溃后自动重建壁纸
                    wallpaperCrashed = false;
                    if (wallpaperRenderer != null) {
                        try {
                            wallpaperRenderer.release();
                        } catch (Exception ignored) {}
                        wallpaperRenderer = null;
                    }
                    int type = PreferenceHelper.getWallpaperType();
                    try {
                        wallpaperRenderer = WallpaperFactory.create(type);
                        if (wallpaperRenderer != null) {
                            savedWallpaperParams = ParamStore.loadParams(type, wallpaperRenderer.getParamDefs());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to recreate wallpaper after crash", e);
                        wallpaperRenderer = null;
                    }
                    wallpaperStartTime = System.currentTimeMillis();
                }
                if (wallpaperRenderer != null) {
                    // 安全保护：save canvas 确保即使壁纸崩溃也能恢复
                    int saveCount = canvas.save();
                    boolean drawSuccess = false;
                    try {
                        long elapsed = System.currentTimeMillis() - wallpaperStartTime;
                        wallpaperRenderer.draw(canvas, width, height, elapsed);
                        drawSuccess = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Wallpaper draw failed, will recreate", e);
                        wallpaperCrashed = true;
                    } catch (OutOfMemoryError e) {
                        Log.e(TAG, "Wallpaper OOM, will recreate", e);
                        wallpaperCrashed = true;
                    }
                    // 确保 canvas 恢复到原始状态（时钟在此之后绘制）
                    canvas.restoreToCount(saveCount);
                    if (!drawSuccess) {
                        drawError(canvas, width, height);
                    }
                } else {
                    drawError(canvas, width, height);
                }
                break;
        }
    }

    /**
     * drawError - 壁纸渲染失败时显示绕 Y 轴旋转的粗体 ERROR 文字
     * 占屏宽约 60%，红色粗体，持续旋转便于识别
     */
    private void drawError(Canvas canvas, int width, int height) {
        canvas.drawColor(0xFF0A0A0A);

        // 懒初始化
        if (errorPaint == null) {
            errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            errorPaint.setTypeface(Typeface.DEFAULT_BOLD);
            errorPaint.setTextAlign(Paint.Align.CENTER);
            errorPaint.setColor(0xFFCC2222);
        }
        if (errorCamera == null) {
            errorCamera = new Camera();
            errorMatrix = new Matrix();
        }

        // 字号：占屏宽约 60%（ERROR 5字符，粗体每字约 0.62em 宽）
        float fontSize = width * 0.6f / (5f * 0.62f);
        errorPaint.setTextSize(fontSize);

        // 绕 Y 轴旋转角度（持续旋转，约 3 秒一圈）
        float rotDeg = (System.currentTimeMillis() / 30f) % 360f;

        float cx = width / 2f;
        float cy = height / 2f;

        canvas.save();
        canvas.translate(cx, cy);

        // 应用 3D Y 轴旋转透视
        errorCamera.save();
        errorCamera.rotateY(rotDeg);
        errorCamera.getMatrix(errorMatrix);
        errorCamera.restore();
        canvas.concat(errorMatrix);

        // 绘制文字（基线居中）
        Paint.FontMetrics fm = errorPaint.getFontMetrics();
        float baseline = -(fm.ascent + fm.descent) / 2f;
        canvas.drawText("ERROR", 0, baseline, errorPaint);

        canvas.restore();
    }

    /** drawBitmapWithFit - 根据适应模式绘制图片 */
    private void drawBitmapWithFit(Canvas canvas, Bitmap bitmap, int canvasWidth, int canvasHeight, int fitMode) {
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();

        switch (fitMode) {
            case FIT_STRETCH: {
                // 非等比拉伸至画布尺寸
                RectF dest = new RectF(0, 0, canvasWidth, canvasHeight);
                canvas.drawBitmap(bitmap, null, dest, bitmapPaint);
                break;
            }
            case FIT_WIDTH: {
                // 宽度撑满，高度等比，垂直居中
                float scale = canvasWidth / bw;
                float scaledH = bh * scale;
                float top = (canvasHeight - scaledH) / 2f;
                RectF dest = new RectF(0, top, canvasWidth, top + scaledH);
                canvas.drawBitmap(bitmap, null, dest, bitmapPaint);
                break;
            }
            case FIT_HEIGHT: {
                // 高度撑满，宽度等比，水平居中
                float scale = canvasHeight / bh;
                float scaledW = bw * scale;
                float left = (canvasWidth - scaledW) / 2f;
                RectF dest = new RectF(left, 0, left + scaledW, canvasHeight);
                canvas.drawBitmap(bitmap, null, dest, bitmapPaint);
                break;
            }
            case FIT_CENTER: {
                // 原始尺寸居中，不缩放
                float left = (canvasWidth - bw) / 2f;
                float top = (canvasHeight - bh) / 2f;
                canvas.drawBitmap(bitmap, left, top, bitmapPaint);
                break;
            }
            case FIT_CENTER_CROP:
            default: {
                // 等比放大填满，裁切溢出
                float scaleX = canvasWidth / bw;
                float scaleY = canvasHeight / bh;
                float scale = Math.max(scaleX, scaleY);
                float scaledW = bw * scale;
                float scaledH = bh * scale;
                float left = (canvasWidth - scaledW) / 2f;
                float top = (canvasHeight - scaledH) / 2f;
                RectF dest = new RectF(left, top, left + scaledW, top + scaledH);
                canvas.drawBitmap(bitmap, null, dest, bitmapPaint);
                break;
            }
        }
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
        if (wallpaperRenderer != null) {
            try {
                wallpaperRenderer.release();
            } catch (Exception e) {
                // ignore
            }
            wallpaperRenderer = null;
        }
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
    public boolean isWallpaperMode() { return mode == MODE_WALLPAPER; }

    /** getWallpaperRenderer - 获取当前壁纸渲染器（用于参数控制面板） */
    public WallpaperRenderer getWallpaperRenderer() { return wallpaperRenderer; }

    /** initWallpaper - 初始化/重新初始化壁纸尺寸 */
    public void initWallpaper(int width, int height) {
        if (mode == MODE_WALLPAPER && wallpaperRenderer != null) {
            try {
                wallpaperRenderer.init(width, height);
                // init 后立即应用已保存的参数（覆盖默认值）
                if (savedWallpaperParams != null) {
                    wallpaperRenderer.applyParams(savedWallpaperParams);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to init wallpaper", e);
            }
        }
    }
}
