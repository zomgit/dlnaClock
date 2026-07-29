package com.dlnaclock.media;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.dlnaclock.util.PreferenceHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * OsdOverlayView - 可拖拽时间叠加层（OSD）
 * 在视频播放时显示当前时间，支持触摸拖拽移动位置
 * 播放 5 秒后自动半透明，触摸时恢复
 * 另支持音量 OSD：控制端 SetVolume/SetMute 后显示音量条+百分比，3 秒后自动隐藏
 */
public class OsdOverlayView extends View {

    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 时间文字画笔
    private Paint bgPaint = new Paint();                        // 背景矩形画笔
    private Paint volumeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 音量文字画笔
    private Paint volumeBarBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 音量条背景画笔
    private Paint volumeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // 音量条前景画笔
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

    // 音量 OSD 状态
    private boolean timeOsdEnabled = true;   // 是否绘制时间 OSD（音乐界面仅用音量 OSD）
    private boolean touchable = true;        // 是否拦截触摸（音乐界面需让触摸穿透到下层控件）
    private boolean volumeOsdShowing = false;
    private int volumePercent = 0;
    private boolean volumeMuted = false;
    private static final long VOLUME_OSD_TIMEOUT = 3000; // 音量 OSD 自动隐藏时长

    // 点击监听器：用于通知外部用户点击了 OSD（非拖拽）
    private OnTapListener tapListener;

    public interface OnTapListener {
        void onTap();
    }

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }
    private final Runnable hideVolumeRunnable = new Runnable() {
        @Override
        public void run() {
            volumeOsdShowing = false;
            invalidate();
        }
    };

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
        timeOsdEnabled = PreferenceHelper.getOsdTimeEnabled();

        textPaint.setTextSize(fontSize);
        textPaint.setColor(fontColor);
        textPaint.setAlpha(opacity);
        textPaint.setShadowLayer(4, 2, 2, Color.BLACK);

        bgPaint.setColor(Color.argb(80, 0, 0, 0));

        volumeTextPaint.setColor(Color.WHITE);
        volumeTextPaint.setShadowLayer(4, 2, 2, Color.BLACK);
        volumeBarBgPaint.setColor(Color.argb(120, 255, 255, 255));
        volumeBarPaint.setColor(Color.WHITE);

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

        if (volumeOsdShowing) {
            drawVolumeOsd(canvas);
        }

        if (!timeOsdEnabled || !isVisible) return;

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

        // Draw background - text vertically centered in rect
        float textWidth = textPaint.measureText(timeStr);
        float padding = fontSize * 0.3f;
        int bgAlpha = isDimmed ? 30 : 80;
        bgPaint.setAlpha(bgAlpha);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textAscent = Math.abs(fm.ascent);  // positive value
        float textDescent = fm.descent;            // positive value
        float textHeight = textAscent + textDescent;
        float rectHeight = textHeight + padding;

        // Rect centered vertically around text at posY
        RectF rect = new RectF(
                posX - padding,
                posY - textAscent - padding * 0.5f,
                posX + textWidth + padding,
                posY + textDescent + padding * 0.5f
        );

        // 调用兼容 API 19 的 4 参数方法
        canvas.drawRoundRect(rect, padding * 0.5f, padding * 0.5f, bgPaint);

        // Draw time text (baseline at posY)
        canvas.drawText(timeStr, posX, posY, textPaint);
    }

    /** drawVolumeOsd - 屏幕顶部居中绘制音量条 + 百分比文字 */
    private void drawVolumeOsd(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float textSize = Math.max(24f, Math.min(w, h) * 0.045f);
        volumeTextPaint.setTextSize(textSize);

        String label = volumeMuted ? "静音" : "音量 " + volumePercent + "%";
        float boxWidth = Math.min(w * 0.5f, textSize * 14);
        float padding = textSize * 0.6f;
        float barHeight = textSize * 0.35f;
        float boxHeight = padding * 2 + textSize + barHeight + textSize * 0.5f;
        float left = (w - boxWidth) / 2f;
        float top = h * 0.08f;

        // 背景圆角矩形
        bgPaint.setAlpha(160);
        RectF boxRect = new RectF(left, top, left + boxWidth, top + boxHeight);
        canvas.drawRoundRect(boxRect, padding * 0.5f, padding * 0.5f, bgPaint);

        // 百分比文字（居中）
        float textWidth = volumeTextPaint.measureText(label);
        float textX = left + (boxWidth - textWidth) / 2f;
        float textY = top + padding + textSize * 0.85f;
        canvas.drawText(label, textX, textY, volumeTextPaint);

        // 音量条
        float barLeft = left + padding;
        float barRight = left + boxWidth - padding;
        float barTop = textY + textSize * 0.5f;
        float barRadius = barHeight / 2f;
        RectF barBgRect = new RectF(barLeft, barTop, barRight, barTop + barHeight);
        canvas.drawRoundRect(barBgRect, barRadius, barRadius, volumeBarBgPaint);
        int fillPercent = volumeMuted ? 0 : volumePercent;
        if (fillPercent > 0) {
            float fillRight = barLeft + (barRight - barLeft) * fillPercent / 100f;
            RectF barFillRect = new RectF(barLeft, barTop, fillRight, barTop + barHeight);
            canvas.drawRoundRect(barFillRect, barRadius, barRadius, volumeBarPaint);
        }
    }

    /** showVolume - 显示音量 OSD，3 秒后自动隐藏（需在主线程调用） */
    public void showVolume(int percent, boolean mute) {
        volumePercent = Math.max(0, Math.min(percent, 100));
        volumeMuted = mute;
        volumeOsdShowing = true;
        removeCallbacks(hideVolumeRunnable);
        postDelayed(hideVolumeRunnable, VOLUME_OSD_TIMEOUT);
        invalidate();
    }

    /** setTimeOsdEnabled - 是否启用时间 OSD 绘制（音乐界面已有时钟，关闭时间 OSD） */
    public void setTimeOsdEnabled(boolean enabled) {
        this.timeOsdEnabled = enabled;
        invalidate();
    }

    /** setTouchable - 是否拦截触摸事件；关闭后触摸穿透到下层控件 */
    public void setTouchable(boolean touchable) {
        this.touchable = touchable;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 移除延时回调，避免泄漏
        removeCallbacks(hideVolumeRunnable);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!touchable) {
            // 不拦截，让事件传递给下层控件（按钮/进度条）
            return false;
        }
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
                    // 通知外部点击事件
                    if (tapListener != null) {
                        tapListener.onTap();
                    }
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
