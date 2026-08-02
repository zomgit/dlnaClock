package com.dlnaclock.clock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.dlnaclock.App;

import java.util.Calendar;

/**
 * AnalogClockRenderer - 模拟时钟渲染器
 * Canvas 绘制表盘、刻度、数字、时/分/秒指针
 */
public class AnalogClockRenderer implements ClockRenderer {

    private Paint dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);    // 表盘画笔
    private Paint hourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);    // 时针画笔
    private Paint minutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 分针画笔
    private Paint secondPaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 秒针画笔
    private Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);    // 刻度画笔
    private Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 中心点画笔
    private Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 数字画笔

    @Override
    public void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config) {
        int color = config.getFontColor();

        float[] layout = computeLayout(width, height, config);
        float centerX = layout[4];
        float centerY = layout[5];
        float radius = layout[6];

        // Draw dial circle
        dialPaint.setColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
        dialPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius, dialPaint);

        // Draw dial border
        dialPaint.setColor(color);
        dialPaint.setStyle(Paint.Style.STROKE);
        dialPaint.setStrokeWidth(radius * 0.03f);
        canvas.drawCircle(centerX, centerY, radius, dialPaint);

        // Draw hour ticks and numbers
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 - 90);
            float tickStart = radius * 0.85f;
            float tickEnd = radius * 0.95f;

            tickPaint.setColor(color);
            tickPaint.setStrokeWidth(radius * 0.03f);
            tickPaint.setStyle(Paint.Style.STROKE);

            float x1 = centerX + (float) (tickStart * Math.cos(angle));
            float y1 = centerY + (float) (tickStart * Math.sin(angle));
            float x2 = centerX + (float) (tickEnd * Math.cos(angle));
            float y2 = centerY + (float) (tickEnd * Math.sin(angle));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);

            // Draw numbers
            numberPaint.setColor(color);
            numberPaint.setTextSize(radius * 0.18f);
            numberPaint.setTextAlign(Paint.Align.CENTER);
            float numX = centerX + (float) (radius * 0.72f * Math.cos(angle));
            float numY = centerY + (float) (radius * 0.72f * Math.sin(angle)) + radius * 0.06f;
            canvas.drawText(String.valueOf(i == 0 ? 12 : i), numX, numY, numberPaint);
        }

        // Draw minute ticks
        for (int i = 0; i < 60; i++) {
            if (i % 5 == 0) continue;
            double angle = Math.toRadians(i * 6 - 90);
            float tickStart = radius * 0.90f;
            float tickEnd = radius * 0.95f;

            tickPaint.setColor(Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)));
            tickPaint.setStrokeWidth(radius * 0.015f);

            float x1 = centerX + (float) (tickStart * Math.cos(angle));
            float y1 = centerY + (float) (tickStart * Math.sin(angle));
            float x2 = centerX + (float) (tickEnd * Math.cos(angle));
            float y2 = centerY + (float) (tickEnd * Math.sin(angle));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        int hour = time.get(Calendar.HOUR_OF_DAY) % 12;
        int minute = time.get(Calendar.MINUTE);
        int second = time.get(Calendar.SECOND);
        int millis = time.get(Calendar.MILLISECOND);

        // Draw hour hand
        double hourAngle = Math.toRadians((hour + minute / 60.0) * 30 - 90);
        hourPaint.setColor(color);
        hourPaint.setStrokeWidth(radius * 0.06f);
        hourPaint.setStyle(Paint.Style.STROKE);
        hourPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(centerX, centerY,
                centerX + (float) (radius * 0.5f * Math.cos(hourAngle)),
                centerY + (float) (radius * 0.5f * Math.sin(hourAngle)),
                hourPaint);

        // Draw minute hand
        double minuteAngle = Math.toRadians((minute + second / 60.0) * 6 - 90);
        minutePaint.setColor(color);
        minutePaint.setStrokeWidth(radius * 0.04f);
        minutePaint.setStyle(Paint.Style.STROKE);
        minutePaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(centerX, centerY,
                centerX + (float) (radius * 0.7f * Math.cos(minuteAngle)),
                centerY + (float) (radius * 0.7f * Math.sin(minuteAngle)),
                minutePaint);

        // Draw second hand (always show for analog clock)
        {
            double secondAngle = Math.toRadians((second + millis / 1000.0) * 6 - 90);
            secondPaint.setColor(Color.argb(200, 255, 80, 80));
            secondPaint.setStrokeWidth(radius * 0.02f);
            secondPaint.setStyle(Paint.Style.STROKE);
            secondPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(centerX, centerY,
                    centerX + (float) (radius * 0.8f * Math.cos(secondAngle)),
                    centerY + (float) (radius * 0.8f * Math.sin(secondAngle)),
                    secondPaint);
        }

        // Draw center dot
        centerPaint.setColor(color);
        centerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius * 0.05f, centerPaint);
    }

    @Override
    public float[] getContentBounds(int width, int height, Calendar time, ClockConfig config) {
        float[] layout = computeLayout(width, height, config);
        return new float[]{layout[0], layout[1], layout[2], layout[3]};
    }

    /**
     * computeLayout - 计算表盘尺寸与位置（绘制与弹射边界共用）
     * @return [left, top, right, bottom, centerX, centerY, radius]
     */
    private float[] computeLayout(int width, int height, ClockConfig config) {
        // 基于屏幕宽度计算字号（各时钟类型独立存储，模拟时钟用 "12:00:00" 作为参考文本）
        float orientScale = ClockConfig.getFontScaleForOrientation(width, height, ClockConfig.ClockStyle.ANALOG);
        config.setFontScale(orientScale);
        numberPaint.setTextSize(100);
        int fontSize = config.getFontSizePx(width, "12:00:00", numberPaint);
        int size = Math.min(width, height) / 3;
        if (size > fontSize * 4) {
            size = fontSize * 4;
        }

        float radius = size / 2f;
        // 基于内容尺寸和用户设置的位置百分比计算坐标
        // posX=0% 左边缘贴屏幕左边, posX=50% 水平居中, posX=100% 右边缘贴屏幕右边
        float centerX = radius + config.getPositionX() * (width - size);
        // posY=0% 顶部贴屏幕顶端, posY=50% 垂直居中, posY=100% 底部贴屏幕底端
        float centerY = radius + config.getPositionY() * (height - size);
        // 安全边界钳制
        centerX = Math.max(radius, Math.min(width - radius, centerX));
        centerY = Math.max(radius, Math.min(height - radius, centerY));

        return new float[]{
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                centerX, centerY, radius
        };
    }
}
