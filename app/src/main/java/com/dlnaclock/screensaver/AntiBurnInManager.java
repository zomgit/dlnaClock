package com.dlnaclock.screensaver;

import android.os.Handler;
import android.os.Looper;

import com.dlnaclock.util.PreferenceHelper;

import java.util.Random;

public class AntiBurnInManager {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private boolean enabled = false;
    private boolean pixelShiftEnabled = false;
    private int intervalSeconds = 30;

    private float offsetX = 0;
    private float offsetY = 0;
    private float pixelOffsetX = 0;
    private float pixelOffsetY = 0;

    private int screenWidth;
    private int screenHeight;

    private OffsetChangeListener listener;

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
        }
        if (pixelShiftEnabled) {
            schedulePixelShift();
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

    public boolean isEnabled() {
        return enabled || pixelShiftEnabled;
    }
}
