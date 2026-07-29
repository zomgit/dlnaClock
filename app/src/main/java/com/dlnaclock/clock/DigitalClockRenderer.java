package com.dlnaclock.clock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DigitalClockRenderer implements ClockRenderer {

    private Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat timeFormatNoSec = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd EEEE", Locale.getDefault());

    @Override
    public void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config) {
        int color = config.getFontColor();
        int fontSize = config.getFontSize();

        // Time text
        String timeStr = config.isShowSeconds() ?
                timeFormat.format(time.getTime()) : timeFormatNoSec.format(time.getTime());

        timePaint.setColor(color);
        timePaint.setTextSize(fontSize);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setFakeBoldText(true);

        // Set typeface if custom font
        if (config.getFontFamily() != null && !config.getFontFamily().equals("default")) {
            try {
                timePaint.setTypeface(Typeface.create(config.getFontFamily(), Typeface.NORMAL));
            } catch (Exception e) {
                timePaint.setTypeface(Typeface.DEFAULT);
            }
        } else {
            timePaint.setTypeface(Typeface.DEFAULT);
        }

        float centerX = width * config.getPositionX();
        float centerY = height * config.getPositionY();

        // Draw time
        canvas.drawText(timeStr, centerX, centerY, timePaint);

        // Draw date
        if (config.isShowDate()) {
            String dateStr = dateFormat.format(time.getTime());
            datePaint.setColor(color);
            datePaint.setTextSize(fontSize * 0.35f);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setAlpha(180);
            canvas.drawText(dateStr, centerX, centerY + fontSize * 0.6f, datePaint);
        }
    }
}
