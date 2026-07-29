package com.dlnaclock.screensaver;

import android.os.Handler;
import android.os.Looper;

import com.dlnaclock.util.PreferenceHelper;

import java.util.Random;

/**
 * AntiBurnInManager - 防烧屏管理器
 * 提供两种防烧屏机制：
 * 1. 位置随机偏移：每 N 秒偏移 ±10% 屏幕宽高
 * 2. 像素微偏移：每 5 秒偏移 1-5px
 * 偏移值应用到 Canvas.translate()
 */
public class AntiBurnInManager {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private boolean enabled = false;          // 位置偏移开关
    private boolean pixelShiftEnabled = false; // 像素微偏移开关
    private int intervalSeconds = 30;          // 位置偏移间隔（秒）

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
        this.pixelShiftEnabled = PreferenceHelper.isPixelShiftEnabled();
        this.intervalSeconds = PreferenceHelper.getBurnInInterval();

        if (enabled) {
            schedulePositionMove();
        } else {
            // 关闭时重置位置偏移量，回到用户设定位置
            handler.removeCallbacks(positionMoveRunnable);
            resetOffsets();
        }
        if (pixelShiftEnabled) {
            schedulePixelShift();
        } else {
            // 关闭时重置像素偏移量
            handler.removeCallbacks(pixelShiftRunnable);
            resetPixelOffsets();
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
                // Random offset within 10% of screen size
                float maxOffsetX = screenWidth * 0.1f;
                float maxOffsetY = screenHeight * 0.1f;
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
            if (pixelShiftEnabled) {
                // Random pixel offset 1-5 pixels
                pixelOffsetX = (random.nextInt(5) + 1) * (random.nextBoolean() ? 1 : -1);
                pixelOffsetY = (random.nextInt(5) + 1) * (random.nextBoolean() ? 1 : -1);

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

    /** resetOffsets - 重置位置偏移量为零，回到用户设定位置 */
    private void resetOffsets() {
        offsetX = 0;
        offsetY = 0;
        notifyOffsetChanged();
    }

    /** resetPixelOffsets - 重置像素微偏移量为零 */
    private void resetPixelOffsets() {
        pixelOffsetX = 0;
        pixelOffsetY = 0;
        notifyOffsetChanged();
    }

    public boolean isEnabled() {
        return enabled || pixelShiftEnabled;
    }
}
