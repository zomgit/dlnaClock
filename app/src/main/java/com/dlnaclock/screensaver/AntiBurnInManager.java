package com.dlnaclock.screensaver;

import android.os.Handler;
import android.os.Looper;

import com.dlnaclock.util.PreferenceHelper;

import java.util.Random;

/**
 * AntiBurnInManager - 防烧屏管理器
 * 合并两种防烧屏机制为一个开关：
 * 1. 位置随机偏移：每 N 秒偏移 ±offsetRange% 屏幕宽高
 * 2. 像素微偏移：每 5 秒偏移 1~pixelRange px，幅度由偏移幅度（百分比）推导
 * 偏移值应用到 Canvas.translate()
 */
public class AntiBurnInManager {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private boolean enabled = false;          // 防烧屏总开关
    private int intervalSeconds = 30;          // 位置偏移间隔（秒）
    private int offsetRangePercent = 15;       // 偏移幅度（屏幕百分比）
    private int pixelRange = 8;                // 像素微偏移幅度（px），由偏移幅度推导

    private float offsetX = 0;
    private float offsetY = 0;
    private float pixelOffsetX = 0;
    private float pixelOffsetY = 0;

    private int screenWidth;
    private int screenHeight;

    private OffsetChangeListener listener;

    /** OffsetChangeListener - 偏移量变化监听接口 */
    public interface OffsetChangeListener {
        void onOffsetChanged(float offsetX, float offsetY, float pixelOffsetX, float pixelOffsetY);
    }

    public AntiBurnInManager() {
    }

    public void setListener(OffsetChangeListener listener) {
        this.listener = listener;
    }

    public void start(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.enabled = PreferenceHelper.isAntiBurnInEnabled();
        this.intervalSeconds = PreferenceHelper.getBurnInInterval();
        this.offsetRangePercent = PreferenceHelper.getBurnInOffsetRange();
        // 像素微移幅度由偏移幅度推导：15% -> 8px，与旧默认值一致
        this.pixelRange = Math.max(1, Math.round(offsetRangePercent * 0.5f));

        if (enabled) {
            schedulePositionMove();
            schedulePixelShift();
        } else {
            handler.removeCallbacks(positionMoveRunnable);
            handler.removeCallbacks(pixelShiftRunnable);
            resetOffsets();
        }
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    private void schedulePositionMove() {
        handler.removeCallbacks(positionMoveRunnable);
        handler.postDelayed(positionMoveRunnable, intervalSeconds * 1000);
    }

    private void schedulePixelShift() {
        handler.removeCallbacks(pixelShiftRunnable);
        handler.postDelayed(pixelShiftRunnable, 5000);
    }

    private Runnable positionMoveRunnable = new Runnable() {
        @Override
        public void run() {
            if (enabled && screenWidth > 0 && screenHeight > 0) {
                // Random offset within offsetRangePercent% of screen size
                float maxOffsetX = screenWidth * (offsetRangePercent / 100f);
                float maxOffsetY = screenHeight * (offsetRangePercent / 100f);
                offsetX = (random.nextFloat() - 0.5f) * 2 * maxOffsetX;
                offsetY = (random.nextFloat() - 0.5f) * 2 * maxOffsetY;

                notifyOffsetChanged();
            }
            schedulePositionMove();
        }
    };

    private Runnable pixelShiftRunnable = new Runnable() {
        @Override
        public void run() {
            if (enabled) {
                // Random pixel offset 1~pixelRange pixels
                pixelOffsetX = (random.nextInt(pixelRange) + 1) * (random.nextBoolean() ? 1 : -1);
                pixelOffsetY = (random.nextInt(pixelRange) + 1) * (random.nextBoolean() ? 1 : -1);

                notifyOffsetChanged();
            }
            schedulePixelShift();
        }
    };

    private void notifyOffsetChanged() {
        if (listener != null) {
            listener.onOffsetChanged(offsetX, offsetY, pixelOffsetX, pixelOffsetY);
        }
    }

    public float getTotalOffsetX() {
        return offsetX + pixelOffsetX;
    }

    public float getTotalOffsetY() {
        return offsetY + pixelOffsetY;
    }

    /** resetOffsets - 重置所有偏移量为零 */
    private void resetOffsets() {
        offsetX = 0;
        offsetY = 0;
        pixelOffsetX = 0;
        pixelOffsetY = 0;
        notifyOffsetChanged();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
