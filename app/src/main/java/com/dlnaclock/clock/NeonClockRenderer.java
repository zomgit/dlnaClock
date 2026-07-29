package com.dlnaclock.clock;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class NeonClockRenderer implements ClockRenderer {

    private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat timeFormatNoSec = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    public void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config) {
        int fontSize = config.getFontSize();
        float centerX = width * config.getPositionX();
        float centerY = height * config.getPositionY();

        // Disable hardware acceleration for blur mask filter
        canvas.save();

        String timeStr = config.isShowSeconds() ?
                timeFormat.format(time.getTime()) : timeFormatNoSec.format(time.getTime());

        // Outer glow layer
        glowPaint.setColor(config.getFontColor());
        glowPaint.setTextSize(fontSize);
        glowPaint.setTextAlign(Paint.Align.CENTER);
        glowPaint.setAlpha(60);
        glowPaint.setMaskFilter(new BlurMaskFilter(fontSize * 0.3f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawText(timeStr, centerX, centerY, glowPaint);

        // Middle glow layer
        glowPaint.setAlpha(120);
        glowPaint.setMaskFilter(new BlurMaskFilter(fontSize * 0.15f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawText(timeStr, centerX, centerY, glowPaint);

        // Core text (bright)
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(fontSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setMaskFilter(null);
        canvas.drawText(timeStr, centerX, centerY, textPaint);

        // Draw date with neon effect
        if (config.isShowDate()) {
            String dateStr = dateFormat.format(time.getTime());

            // Date glow
            datePaint.setColor(config.getFontColor());
            datePaint.setTextSize(fontSize * 0.3f);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setAlpha(50);
            datePaint.setMaskFilter(new BlurMaskFilter(fontSize * 0.15f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawText(dateStr, centerX, centerY + fontSize * 0.6f, datePaint);

            // Date core
            datePaint.setColor(Color.WHITE);
            datePaint.setAlpha(200);
            datePaint.setMaskFilter(null);
            canvas.drawText(dateStr, centerX, centerY + fontSize * 0.6f, datePaint);
        }

        canvas.restore();
    }
}
