package com.dlnaclock.screensaver;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.dlnaclock.clock.AnalogClockRenderer;
import com.dlnaclock.clock.ClockConfig;
import com.dlnaclock.clock.ClockRenderer;
import com.dlnaclock.clock.DigitalClockRenderer;
import com.dlnaclock.clock.MinimalClockRenderer;
import com.dlnaclock.util.PreferenceHelper;

import java.util.Calendar;

/**
 * ScreenSaverView - 屏保自定义 View
 * 在 onDraw 中调用 ClockRenderer 绘制时钟，集成 BackgroundManager 绘制背景，
 * 集成 AntiBurnInManager 应用防烧屏偏移；弹射模式下集成 BounceBurnInManager
 * 并支持手指拖动时钟
 */
public class ScreenSaverView extends View {

    /**
     * 壁纸旋转监听 - 手势旋转角变化时通知外部（参数面板实时显示）
     */
    public interface WallpaperRotationListener {
        /** 手势旋转角变化（角度制） */
        void onWallpaperRotationChanged(float rotX, float rotY);
    }

    private ClockRenderer clockRenderer;           // 当前时钟渲染器
    private ClockConfig clockConfig;               // 时钟配置
    private BackgroundManager backgroundManager;   // 背景管理器
    private AntiBurnInManager antiBurnInManager;   // 防烧屏管理器
    private GestureController gestureController;   // 背景手势控制器（随机偏移模式）
    private BounceBurnInManager bounceBurnInManager; // 防烧屏管理器（弹射模式）
    private WallpaperRotationListener wallpaperRotationListener; // 旋转参数联动监听

    private float burnInOffsetX = 0;
    private float burnInOffsetY = 0;

    // 弹射模式状态
    private long lastBounceTick = 0;               // 上次弹射步进时间戳
    private boolean dragging = false;              // 是否正在拖动时钟
    private float downX, downY;                    // 按下位置
    private float prevX, prevY;                    // 上次 MOVE 位置
    private float lastMoveX, lastMoveY;            // 最近一次位移（松手弹射方向）
    private int touchSlop;                         // 拖动判定阈值

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
        // 手势操控：一指旋转 / 双指平移缩放，变换仅作用于背景层
        gestureController = new GestureController(getContext());
        backgroundManager.setGestureTransform(gestureController.getTransform());
        setupGestureRotationBridge();
        antiBurnInManager = new AntiBurnInManager();
        antiBurnInManager.setListener(new AntiBurnInManager.OffsetChangeListener() {
            @Override
            public void onOffsetChanged(float offsetX, float offsetY, float pixelOffsetX, float pixelOffsetY) {
                burnInOffsetX = offsetX + pixelOffsetX;
                burnInOffsetY = offsetY + pixelOffsetY;
                invalidate();
            }
        });
        bounceBurnInManager = new BounceBurnInManager();
        bounceBurnInManager.setListener(new BounceBurnInManager.OffsetChangeListener() {
            @Override
            public void onOffsetChanged(float offsetX, float offsetY) {
                burnInOffsetX = offsetX;
                burnInOffsetY = offsetY;
                invalidate();
            }
        });
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    /**
     * setupGestureRotationBridge - 手势旋转角变化桥接：
     * 转发给参数面板实时显示旋转角度（所有手势壁纸通用）
     */
    private void setupGestureRotationBridge() {
        gestureController.setRotationStateListener(new GestureController.RotationStateListener() {
            @Override
            public void onRotationChanged(float rotX, float rotY) {
                if (wallpaperRotationListener != null) {
                    wallpaperRotationListener.onWallpaperRotationChanged(rotX, rotY);
                }
            }
        });
    }

    /** setWallpaperRotationListener - 设置旋转角联动监听（参数面板） */
    public void setWallpaperRotationListener(WallpaperRotationListener listener) {
        this.wallpaperRotationListener = listener;
    }

    /** resetWallpaperGesture - 一键还原：清零当前壁纸的手势状态（叠加式壁纸） */
    public void resetWallpaperGesture() {
        gestureController.resetCurrentWallpaperTransform();
        invalidate();
    }

    /** resetRotationGesture - 单独还原旋转角（保留平移/缩放） */
    public void resetRotationGesture() {
        gestureController.resetRotation();
        if (wallpaperRotationListener != null) {
            wallpaperRotationListener.onWallpaperRotationChanged(0f, 0f);
        }
        invalidate();
    }

    /** getGestureRotX/getGestureRotY - 当前手势旋转角（面板打开时同步显示） */
    public float getGestureRotX() { return gestureController.getTransform().getRotX(); }
    public float getGestureRotY() { return gestureController.getTransform().getRotY(); }

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
            if (isBounceMode()) {
                startBounceManager(w, h);
            } else {
                antiBurnInManager.start(w, h);
            }
        }
    }

    /** isBounceMode - 防烧屏是否为弹射模式（总开关开启且模式为弹射） */
    private boolean isBounceMode() {
        return PreferenceHelper.isAntiBurnInEnabled() && PreferenceHelper.getAntiBurnInMode() == 1;
    }

    /** startBounceManager - 启动弹射管理器并更新碰撞边界 */
    private void startBounceManager(int w, int h) {
        bounceBurnInManager.start(w, h,
                PreferenceHelper.isAntiBurnInEnabled(),
                PreferenceHelper.getBounceAngleRange(),
                PreferenceHelper.getBounceSpeed());
        updateBounceBounds();
    }

    /** updateBounceBounds - 根据当前时钟渲染内容计算弹射碰撞边界 */
    private void updateBounceBounds() {
        if (clockRenderer != null && getWidth() > 0 && getHeight() > 0) {
            float[] bounds = clockRenderer.getContentBounds(
                    getWidth(), getHeight(), Calendar.getInstance(), clockConfig);
            bounceBurnInManager.setContentBounds(bounds);
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
        if (isBounceMode()) {
            startBounceManager(getWidth(), getHeight());
        } else {
            antiBurnInManager.start(getWidth(), getHeight());
        }
        startClockUpdate();
    }

    public void stop() {
        isRunning = false;
        antiBurnInManager.stop();
        bounceBurnInManager.stop();
        handler.removeCallbacks(clockUpdateRunnable);
    }

    private Runnable clockUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                if (isBounceMode()) {
                    // 弹射模式：按实际时间间隔步进
                    long now = SystemClock.elapsedRealtime();
                    if (lastBounceTick != 0) {
                        bounceBurnInManager.step(now - lastBounceTick);
                    }
                    lastBounceTick = now;
                }
                invalidate();
                long interval = (backgroundManager.isWallpaperMode() || isBounceMode()) ? 50 : 1000;
                handler.postDelayed(this, interval);
            }
        }
    };

    private void startClockUpdate() {
        handler.removeCallbacks(clockUpdateRunnable);
        lastBounceTick = 0;
        invalidate();
        long interval = (backgroundManager.isWallpaperMode() || isBounceMode()) ? 50 : 1000;
        handler.postDelayed(clockUpdateRunnable, interval);
    }

    public void reloadConfig() {
        clockConfig = ClockConfig.fromPreferences();
        clockRenderer = createRenderer(clockConfig.getStyle());
        // 切换壁纸：保存当前手势状态并恢复目标壁纸的独立手势状态
        gestureController.setCurrentWallpaper(PreferenceHelper.getWallpaperType());
        backgroundManager.reloadConfig();
        backgroundManager.initWallpaper(getWidth(), getHeight());
        if (isRunning) {
            if (isBounceMode()) {
                startBounceManager(getWidth(), getHeight());
            } else {
                antiBurnInManager.start(getWidth(), getHeight());
            }
            startClockUpdate();
        }
        invalidate();
    }

    /**
     * onTouchEvent - 触摸事件处理：
     * 1. 弹射模式（开启时）：手指拖动时钟（保持原有行为，背景手势不介入避免冲突）
     * 2. 其他模式：背景手势操控（一指旋转 / 双指平移缩放），轻点触发点击切换控制条
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isRunning && isBounceMode() && bounceBurnInManager.isEnabled()) {
            return handleBounceTouch(event);
        }
        // 背景手势操控：一指旋转 / 双指平移缩放（消费后立即刷新画面）
        if (gestureController.onTouch(event, getWidth(), getHeight())) {
            invalidate();
            return true;
        }
        // 未触发手势的抬起视为点击（切换控件栏可见性）
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    /** handleBounceTouch - 弹射模式拖动时钟：
     * 拖动超过阈值时时钟跟随手指移动，松手后沿手指方向继续弹射；
     * 轻点（无拖动）时触发 performClick（保持原有点击切换控制条行为）
     */
    private boolean handleBounceTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                prevX = downX;
                prevY = downY;
                lastMoveX = 0;
                lastMoveY = 0;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.hypot(dx, dy) > touchSlop) {
                        dragging = true;
                    }
                }
                if (dragging) {
                    bounceBurnInManager.dragTo(event.getX(), event.getY());
                    lastMoveX = event.getX() - prevX;
                    lastMoveY = event.getY() - prevY;
                    prevX = event.getX();
                    prevY = event.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    // 松手：沿手指最后移动方向继续弹射
                    bounceBurnInManager.release(lastMoveX, lastMoveY);
                } else {
                    // 轻点：保持原有点击行为（切换控制条）
                    performClick();
                }
                dragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }
}
