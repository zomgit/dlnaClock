package com.dlnaclock.screensaver;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import com.dlnaclock.clock.AnalogClockRenderer;
import com.dlnaclock.clock.ClockConfig;
import com.dlnaclock.clock.ClockRenderer;
import com.dlnaclock.clock.DigitalClockRenderer;
import com.dlnaclock.clock.MinimalClockRenderer;

import java.util.Calendar;

/**
 * ScreenSaverView - 屏保自定义 View
 * 在 onDraw 中调用 ClockRenderer 绘制时钟，集成 BackgroundManager 绘制背景，
 * 集成 AntiBurnInManager 应用防烧屏偏移
 */
public class ScreenSaverView extends View {

    private ClockRenderer clockRenderer;           // 当前时钟渲染器
    private ClockConfig clockConfig;               // 时钟配置
    private BackgroundManager backgroundManager;   // 背景管理器
    private AntiBurnInManager antiBurnInManager;   // 防烧屏管理器

    private float burnInOffsetX = 0;
    private float burnInOffsetY = 0;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    public ScreenSaverView(Context context) {
        super(context);
        init();
    }

    public ScreenSaverView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScreenSaverView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        clockConfig = ClockConfig.fromPreferences();
        clockRenderer = createRenderer(clockConfig.getStyle());
        backgroundManager = new BackgroundManager(getContext());
        antiBurnInManager = new AntiBurnInManager();
        antiBurnInManager.setListener(new AntiBurnInManager.OffsetChangeListener() {
            @Override
            public void onOffsetChanged(float offsetX, float offsetY, float pixelOffsetX, float pixelOffsetY) {
                burnInOffsetX = offsetX + pixelOffsetX;
                burnInOffsetY = offsetY + pixelOffsetY;
                invalidate();
            }
        });
    }

    private ClockRenderer createRenderer(ClockConfig.ClockStyle style) {
        switch (style) {
            case ANALOG: return new AnalogClockRenderer();
            case MINIMAL: return new MinimalClockRenderer();
            case DIGITAL:
            default: return new DigitalClockRenderer();
        }
    }

    public void setClockConfig(ClockConfig config) {
        this.clockConfig = config;
        this.clockRenderer = createRenderer(config.getStyle());
        invalidate();
    }

    public ClockConfig getClockConfig() {
        return clockConfig;
    }

    public BackgroundManager getBackgroundManager() {
        return backgroundManager;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        backgroundManager.initWallpaper(w, h);
        if (isRunning) {
            antiBurnInManager.start(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw background
        backgroundManager.draw(canvas, width, height);

        // 确保 canvas 状态干净（防止背景绘制后残留变换）
        canvas.save();
        // Apply burn-in offset
        canvas.translate(burnInOffsetX, burnInOffsetY);

        // Draw clock
        try {
            if (clockRenderer != null && clockConfig != null) {
                clockRenderer.draw(canvas, width, height, Calendar.getInstance(), clockConfig);
            }
        } catch (Exception e) {
            // 时钟绘制失败时不影响整体渲染
        }

        canvas.restore();
    }

    public void start() {
        isRunning = true;
        antiBurnInManager.start(getWidth(), getHeight());
        startClockUpdate();
    }

    public void stop() {
        isRunning = false;
        antiBurnInManager.stop();
        handler.removeCallbacks(clockUpdateRunnable);
    }

    private Runnable clockUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                invalidate();
                long interval = backgroundManager.isWallpaperMode() ? 33 : 1000;
                handler.postDelayed(this, interval);
            }
        }
    };

    private void startClockUpdate() {
        handler.removeCallbacks(clockUpdateRunnable);
        invalidate();
        long interval = backgroundManager.isWallpaperMode() ? 33 : 1000;
        handler.postDelayed(clockUpdateRunnable, interval);
    }

    public void reloadConfig() {
        clockConfig = ClockConfig.fromPreferences();
        clockRenderer = createRenderer(clockConfig.getStyle());
        backgroundManager.reloadConfig();
        backgroundManager.initWallpaper(getWidth(), getHeight());
        if (isRunning) {
            startClockUpdate();
        }
        invalidate();
    }
}
