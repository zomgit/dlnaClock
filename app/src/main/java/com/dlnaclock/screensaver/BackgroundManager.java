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
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

import com.dlnaclock.media.IjkPlayerWrapper;
import com.dlnaclock.util.PreferenceHelper;
import com.dlnaclock.screensaver.wallpaper.GestureAwareWallpaper;
import com.dlnaclock.screensaver.wallpaper.ParamStore;
import com.dlnaclock.screensaver.wallpaper.ScriptManager;
import com.dlnaclock.screensaver.wallpaper.WallpaperRenderer;
import com.dlnaclock.screensaver.wallpaper.WallpaperFactory;

import java.io.File;

/**
 * BackgroundManager - 屏保背景管理器
 * 支持背景模式：纯色、静态图片（多种适应模式）、视频循环播放、动态壁纸、Lua 自定义壁纸
 * 视频背景使用 IJK (FFmpeg) 引擎渲染到 TextureView，支持 mkv/avi 等全格式
 */
public class BackgroundManager {

    private static final String TAG = "BackgroundManager";
    private static final int MODE_COLOR = 0;  // 纯色背景
    private static final int MODE_IMAGE = 1;  // 图片背景
    private static final int MODE_VIDEO = 2;  // 视频背景
    private static final int MODE_WALLPAPER = 3; // 动态壁纸
    private static final int MODE_LUA = 4;    // Lua 自定义壁纸

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
    private IjkPlayerWrapper videoPlayer;      // 背景视频播放器（FFmpeg 全格式）
    private TextureView videoTexture;          // 背景视频渲染层（由 ScreenSaverActivity 注入）
    private boolean videoSurfaceReady = false; // TextureView surface 是否已就绪
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

    // 手势变换（一指旋转 / 双指平移缩放），仅非视频模式应用
    private GestureTransform gestureTransform;

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

        if (mode == MODE_WALLPAPER || mode == MODE_LUA) {
            try {
                if (mode == MODE_LUA) {
                    // Lua 自定义壁纸：按所选脚本创建渲染器（参数在 init 时自行加载）
                    wallpaperRenderer = createLuaWallpaper();
                } else {
                    int type = PreferenceHelper.getWallpaperType();
                    wallpaperRenderer = WallpaperFactory.create(type);
                    // 立即加载已保存的参数，供 initWallpaper 后应用
                    if (wallpaperRenderer != null) {
                        savedWallpaperParams = ParamStore.loadParams(type, wallpaperRenderer.getParamDefs());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create wallpaper", e);
                wallpaperRenderer = null;
                savedWallpaperParams = null;
            }
            wallpaperStartTime = System.currentTimeMillis();
        }
    }

    /** createLuaWallpaper - 按所选脚本路径创建 Lua 壁纸渲染器（内置脚本与外部文件均支持） */
    private WallpaperRenderer createLuaWallpaper() {
        String path = PreferenceHelper.getSelectedLuaScript();
        for (ScriptManager.ScriptInfo info : ScriptManager.getAvailableScripts()) {
            if (info.path.equals(path)) {
                return WallpaperFactory.create(info);
            }
        }
        return WallpaperFactory.create(path);
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
            // 任一边超限就采样：分别按宽/高计算所需采样率，取较大者（2 的幂）
            int sampleW = srcWidth / maxW;
            int sampleH = srcHeight / maxH;
            int target = Math.max(sampleW, sampleH);
            while (inSampleSize * 2 <= target) {
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
        // 视频背景：重新加载后尝试重建播放器并刷新 TextureView 可见性
        updateVideoTexture();
    }

    /** setGestureTransform - 注入手势变换（由 ScreenSaverView 创建并注入） */
    public void setGestureTransform(GestureTransform transform) {
        this.gestureTransform = transform;
    }

    public void draw(Canvas canvas, int width, int height) {
        // 手势变换：视频背景（SurfaceView 独立图层）不适用，其余模式先铺黑底再变换
        boolean gestureActive = gestureTransform != null && !gestureTransform.isIdentity()
                && mode != MODE_VIDEO;
        int gestureSaveCount = -1;
        if (gestureActive) {
            gestureSaveCount = canvas.save();
            canvas.drawColor(Color.BLACK); // 旋转后露出的边缘显示黑色
            boolean awareWallpaper = mode == MODE_WALLPAPER
                    && wallpaperRenderer instanceof GestureAwareWallpaper;
            if (!awareWallpaper) {
                gestureTransform.applyTo(canvas, width, height);
            }
        }

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
                // 视频由 TextureView 独立渲染层播放，Canvas 保持透明避免遮挡视频
                break;

            case MODE_WALLPAPER:
            case MODE_LUA:
                if (wallpaperCrashed) {
                    // 上次崩溃后自动重建壁纸
                    wallpaperCrashed = false;
                    if (wallpaperRenderer != null) {
                        try {
                            wallpaperRenderer.release();
                        } catch (Exception ignored) {}
                        wallpaperRenderer = null;
                    }
                    try {
                        if (mode == MODE_LUA) {
                            wallpaperRenderer = createLuaWallpaper();
                            savedWallpaperParams = null;
                        } else {
                            int type = PreferenceHelper.getWallpaperType();
                            wallpaperRenderer = WallpaperFactory.create(type);
                            if (wallpaperRenderer != null) {
                                savedWallpaperParams = ParamStore.loadParams(type, wallpaperRenderer.getParamDefs());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to recreate wallpaper after crash", e);
                        wallpaperRenderer = null;
                    }
                    wallpaperStartTime = System.currentTimeMillis();
                }
                if (wallpaperRenderer != null) {
                    // 手势感知壁纸：在绘制前注入手势参数（由壁纸自行应用变换）
                    if (gestureActive && wallpaperRenderer instanceof GestureAwareWallpaper) {
                        GestureAwareWallpaper aware = (GestureAwareWallpaper) wallpaperRenderer;
                        aware.applyGesture(gestureTransform.getRotX(), gestureTransform.getRotY(),
                                gestureTransform.getOffsetX(), gestureTransform.getOffsetY(),
                                gestureTransform.getScale());
                    }
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

        // 恢复手势变换前的画布状态
        if (gestureSaveCount >= 0) {
            canvas.restoreToCount(gestureSaveCount);
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

    /**
     * attachVideoTexture - 注入视频背景渲染层（由 ScreenSaverActivity 在布局加载后调用）
     * 监听 TextureView 的 surface 生命周期，就绪后自动创建播放器
     */
    public void attachVideoTexture(TextureView textureView) {
        this.videoTexture = textureView;
        if (textureView == null) return;
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        // 若 surface 已就绪（如 Activity 重建复用），立即尝试播放
        if (textureView.isAvailable()) {
            videoSurfaceReady = true;
            tryStartVideo();
        }
        updateVideoTexture();
    }

    /** SurfaceTexture 生命周期监听 - surface 就绪后创建播放器，销毁时释放 */
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            videoSurfaceReady = true;
            tryStartVideo();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // 尺寸变化无需处理
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            videoSurfaceReady = false;
            releaseVideoPlayer();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // 帧更新无需处理
        }
    };

    /**
     * updateVideoTexture - 按当前模式刷新视频层：仅视频模式且文件存在时显示并播放
     */
    private void updateVideoTexture() {
        if (videoTexture == null) return;
        boolean shouldShow = mode == MODE_VIDEO && isBackgroundVideoAvailable();
        videoTexture.setVisibility(shouldShow ? android.view.View.VISIBLE : android.view.View.GONE);
        if (shouldShow) {
            if (videoTexture.isAvailable()) {
                videoSurfaceReady = true;
                tryStartVideo();
            }
        } else {
            releaseVideoPlayer();
        }
    }

    /** isBackgroundVideoAvailable - 背景视频文件是否已配置且存在 */
    private boolean isBackgroundVideoAvailable() {
        String path = PreferenceHelper.getBackgroundVideoPath();
        return path != null && !path.isEmpty() && new File(path).exists();
    }

    /**
     * tryStartVideo - 使用 IJK (FFmpeg) 播放器创建背景视频（支持 mkv/avi 等全格式）
     * 仅在 surface 就绪、视频模式、文件存在且播放器未创建时启动
     */
    private void tryStartVideo() {
        if (!videoSurfaceReady || videoTexture == null) return;
        if (mode != MODE_VIDEO) return;
        if (!isBackgroundVideoAvailable()) return;
        if (videoPlayer != null) return;

        try {
            videoPlayer = new IjkPlayerWrapper(context);
            videoPlayer.setSurface(new android.view.Surface(videoTexture.getSurfaceTexture()));
            videoPlayer.setListener(new IjkPlayerWrapper.MediaPlayerListener() {
                @Override
                public void onPrepared() {
                    // 双保险：再次确认循环标志后开始播放
                    try {
                        videoPlayer.getIjkMediaPlayer().setLooping(true);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to set looping", e);
                    }
                    videoPlayer.start();
                }

                @Override
                public void onCompletion() {
                    // 循环已由 setLooping 处理
                }

                @Override
                public void onError(int what, int extra) {
                    Log.e(TAG, "Background video error: " + what + ", " + extra);
                }

                @Override
                public void onBufferingUpdate(int percent) {}

                @Override
                public void onVideoSizeChanged(int width, int height) {}
            });
            videoPlayer.setDataSource(PreferenceHelper.getBackgroundVideoPath());
            // 循环播放：prepare 完成前设置循环标志（IjkPlayerWrapper 重试重建后由 onPrepared 兜底）
            try {
                videoPlayer.getIjkMediaPlayer().setLooping(true);
            } catch (Exception e) {
                Log.w(TAG, "Failed to set looping", e);
            }
            // 静音设置：根据偏好设置播放器音量（0=静音 1=有声）
            try {
                tv.danmaku.ijk.media.player.IjkMediaPlayer ijk = videoPlayer.getIjkMediaPlayer();
                if (ijk != null) {
                    float vol = PreferenceHelper.isBackgroundVideoMuted() ? 0f : 1f;
                    ijk.setVolume(vol, vol);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to apply initial volume", e);
            }
            Log.i(TAG, "Background video started: " + PreferenceHelper.getBackgroundVideoPath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup video background", e);
            releaseVideoPlayer();
        }
    }

    /** releaseVideoPlayer - 释放背景视频播放器 */
    private void releaseVideoPlayer() {
        if (videoPlayer != null) {
            try {
                videoPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            videoPlayer = null;
        }
    }

    /** pauseVideo - 暂停背景视频（Activity onPause 时调用，避免后台播放） */
    public void pauseVideo() {
        if (videoPlayer != null) {
            try {
                videoPlayer.pause();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * resumeVideo - 恢复背景视频播放（DLNA 投屏结束后调用）
     * 仅在视频模式且播放器已就绪时恢复
     */
    public void resumeVideo() {
        if (mode != MODE_VIDEO) return;
        if (videoPlayer != null) {
            try {
                if (!videoPlayer.isPlaying()) {
                    videoPlayer.start();
                    Log.i(TAG, "Background video resumed");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to resume video", e);
            }
        }
    }

    /**
     * setVideoMuted - 动态设置背景视频是否静音
     * 通过 IjkMediaPlayer.setVolume 控制，不重启播放器
     */
    public void setVideoMuted(boolean muted) {
        PreferenceHelper.setBackgroundVideoMuted(muted);
        if (videoPlayer != null) {
            try {
                tv.danmaku.ijk.media.player.IjkMediaPlayer ijk = videoPlayer.getIjkMediaPlayer();
                if (ijk != null) {
                    float vol = muted ? 0f : 1f;
                    ijk.setVolume(vol, vol);
                    Log.i(TAG, "Background video volume set to " + vol);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to set video volume", e);
            }
        }
    }

    /** isVideoMuted - 查询背景视频是否静音 */
    public boolean isVideoMuted() {
        return PreferenceHelper.isBackgroundVideoMuted();
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
    public boolean isWallpaperMode() { return mode == MODE_WALLPAPER || mode == MODE_LUA; }

    /** getWallpaperRenderer - 获取当前壁纸渲染器（用于参数控制面板） */
    public WallpaperRenderer getWallpaperRenderer() { return wallpaperRenderer; }

    /** initWallpaper - 初始化/重新初始化壁纸尺寸 */
    public void initWallpaper(int width, int height) {
        if ((mode == MODE_WALLPAPER || mode == MODE_LUA) && wallpaperRenderer != null) {
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
