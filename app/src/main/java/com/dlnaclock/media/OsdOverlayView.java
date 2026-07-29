package com.dlnaclock.media;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.dlnaclock.util.PreferenceHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OsdOverlayView extends View {

    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bgPaint = new Paint();
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private float posX;
    private float posY;
    private float touchStartX;
    private float touchStartY;
    private float viewStartX;
    private float viewStartY;
    private boolean isDragging = false;

    private int fontSize;
    private int fontColor;
    private int opacity;
    private boolean isVisible = true;
    private boolean isDimmed = false;

    private long dimTimeout = 5000; // 5 seconds
    private long lastInteractionTime = 0;

    public OsdOverlayView(Context context) {
        super(context);
        init();
    }

    public OsdOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OsdOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        fontSize = PreferenceHelper.getOsdFontSize();
        fontColor = PreferenceHelper.getOsdFontColor();
        opacity = PreferenceHelper.getOsdOpacity();

        textPaint.setTextSize(fontSize);
        textPaint.setColor(fontColor);
        textPaint.setAlpha(opacity);
        textPaint.setShadowLayer(4, 2, 2, Color.BLACK);

        bgPaint.setColor(Color.argb(80, 0, 0, 0));

        // Position from preferences (percentage)
        // Will be adjusted in onSizeChanged
        lastInteractionTime = System.currentTimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0) {
            // Initial position
            posX = w * PreferenceHelper.getOsdPositionX();
            posY = h * PreferenceHelper.getOsdPositionY();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isVisible) return;

        String timeStr = timeFormat.format(new Date());

        // Check if should dim
        long elapsed = System.currentTimeMillis() - lastInteractionTime;
        int currentAlpha = opacity;
        if (elapsed > dimTimeout && !isDragging) {
            currentAlpha = opacity / 3; // Dim to 1/3 opacity
            isDimmed = true;
        } else {
            isDimmed = false;
        }

        textPaint.setAlpha(currentAlpha);

        // Draw background
        float textWidth = textPaint.measureText(timeStr);
        float padding = fontSize * 0.3f;
        float bgAlpha = isDimmed ? 30 : 80;
        bgPaint.setAlpha(bgAlpha);
        canvas.drawRoundRect(
                posX - padding,
                posY - fontSize - padding * 0.5f,
                posX + textWidth + padding,
                posY + padding * 0.5f,
                padding * 0.5f, padding * 0.5f,
                bgPaint);

        // Draw time text
        canvas.drawText(timeStr, posX, posY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        lastInteractionTime = System.currentTimeMillis();
        isDimmed = false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                viewStartX = posX;
                viewStartY = posY;
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true;
                }
                if (isDragging) {
                    posX = viewStartX + dx;
                    posY = viewStartY + dy;

                    // Clamp to view bounds
                    posX = Math.max(0, Math.min(posX, getWidth() - textPaint.measureText("00:00:00")));
                    posY = Math.max(fontSize, Math.min(posY, getHeight()));

                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!isDragging) {
                    // Toggle visibility
                    toggleVisibility();
                } else {
                    // Save position
                    savePosition();
                }
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    public void toggleVisibility() {
        isVisible = !isVisible;
        lastInteractionTime = System.currentTimeMillis();
        invalidate();
    }

    private void savePosition() {
        if (getWidth() > 0 && getHeight() > 0) {
            PreferenceHelper.setOsdPositionX(posX / getWidth());
            PreferenceHelper.setOsdPositionY(posY / getHeight());
        }
    }

    public void setFontSize(int size) {
        this.fontSize = size;
        textPaint.setTextSize(size);
        invalidate();
    }

    public void setFontColor(int color) {
        this.fontColor = color;
        textPaint.setColor(color);
        invalidate();
    }

    public void setOpacity(int opacity) {
        this.opacity = opacity;
        invalidate();
    }

    public void reloadConfig() {
        fontSize = PreferenceHelper.getOsdFontSize();
        fontColor = PreferenceHelper.getOsdFontColor();
        opacity = PreferenceHelper.getOsdOpacity();
        textPaint.setTextSize(fontSize);
        textPaint.setColor(fontColor);
        invalidate();
    }
}
