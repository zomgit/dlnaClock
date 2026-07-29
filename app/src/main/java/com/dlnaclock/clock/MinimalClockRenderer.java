package com.dlnaclock.clock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MinimalClockRenderer implements ClockRenderer {

    private Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat timeFormatNoSec = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd EEEE", Locale.getDefault());

    @Override
    public void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config) {
        int color = config.getFontColor();
        int fontSize = config.getFontSize();
        float centerX = width * config.getPositionX();
        float centerY = height * config.getPositionY();

        String timeStr = config.isShowSeconds() ?
                timeFormat.format(time.getTime()) : timeFormatNoSec.format(time.getTime());

        // Thin, light time text
        timePaint.setColor(color);
        timePaint.setTextSize(fontSize);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setFakeBoldText(false);
        timePaint.setStrokeWidth(1);
        timePaint.setStyle(Paint.Style.FILL);

        canvas.drawText(timeStr, centerX, centerY, timePaint);

        // Thin separator line
        float lineWidth = fontSize * 2;
        separatorPaint.setColor(Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)));
        separatorPaint.setStrokeWidth(1);
        canvas.drawLine(centerX - lineWidth / 2, centerY + fontSize * 0.15f,
                centerX + lineWidth / 2, centerY + fontSize * 0.15f,
                separatorPaint);

        // Small date text
        if (config.isShowDate()) {
            String dateStr = dateFormat.format(time.getTime());
            datePaint.setColor(Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)));
            datePaint.setTextSize(fontSize * 0.25f);
            datePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(dateStr, centerX, centerY + fontSize * 0.5f, datePaint);
        }
    }
}
