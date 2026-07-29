package com.dlnaclock.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;

/**
 * IconGenerator - 生成 DMR 设备图标
 * 为 UPnP 设备描述中的 iconList 提供 PNG 图标数据
 */
public class IconGenerator {

    /**
     * generateIcon - 生成一个简洁的设备图标 PNG
     * 深色背景 + 白色 "D" 字母，48x48 像素
     * @return PNG 格式的字节数组
     */
    public static byte[] generateIcon() {
        int size = 48;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 深色背景（深蓝灰色）
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF2C3E50);
        RectF bgRect = new RectF(0, 0, size, size);
        canvas.drawRoundRect(bgRect, 8, 8, bgPaint);

        // 白色 "D" 字母
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(32);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("D", size / 2f, y, textPaint);

        // 压缩为 PNG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        bitmap.recycle();

        return baos.toByteArray();
    }
}
