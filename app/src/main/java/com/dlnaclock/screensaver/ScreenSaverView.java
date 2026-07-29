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
import com.dlnaclock.clock.NeonClockRenderer;

import java.util.Calendar;

public class ScreenSaverView extends View {

    private ClockRenderer clockRenderer;
    private ClockConfig clockConfig;
    private BackgroundManager backgroundManager;
    private AntiBurnInManager antiBurnInManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    private float burnInOffsetX = 0;
    private float burnInOffsetY = 0;

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
        setLayerType(LAYER_TYPE_SOFTWARE, null); // Required for BlurMaskFilter in NeonClock
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
            case NEON: return new NeonClockRenderer();
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

        // Apply burn-in offset
        canvas.save();
        canvas.translate(burnInOffsetX, burnInOffsetY);

        // Draw clock
        if (clockRenderer != null && clockConfig != null) {
            clockRenderer.draw(canvas, width, height, Calendar.getInstance(), clockConfig);
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
                handler.postDelayed(this, 1000);
            }
        }
    };

    private void startClockUpdate() {
        handler.removeCallbacks(clockUpdateRunnable);
        invalidate();
        handler.postDelayed(clockUpdateRunnable, 1000);
    }

    public void reloadConfig() {
        clockConfig = ClockConfig.fromPreferences();
        clockRenderer = createRenderer(clockConfig.getStyle());
        backgroundManager.reloadConfig();
        invalidate();
    }
}
