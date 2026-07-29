package com.dlnaclock.clock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.dlnaclock.App;
import com.dlnaclock.util.SystemFontHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * DigitalClockRenderer - 数字时钟渲染器
 * 支持自定义格式、占屏百分比字号、自定义数字/英文/中文字体
 * 主行（时间）使用数字字体，副行（日期）使用中文/英文字体
 */
public class DigitalClockRenderer implements ClockRenderer {

    private Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 时间画笔
    private Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // 副行（日期）画笔
    private String lastFormat = "";                              // 上次使用的格式

    /** setTabularNumbers - 为 Paint 启用等宽数字（tnum OpenType 特性） */
    private void setTabularNumbers(Paint paint) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // "tnum" = tabular figures (monospaced digits), 避免数字宽度变化
            paint.setFontFeatureSettings("\"tnum\"");
        }
    }

    private SimpleDateFormat formatter;                          // 时间格式化器

    @Override
    public void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config) {
        int color = config.getFontColor();

        // 确保格式化器与配置同步
        String format = config.getClockFormat();
        if (formatter == null || !format.equals(lastFormat)) {
            try {
                formatter = new SimpleDateFormat(format, Locale.getDefault());
            } catch (Exception e) {
                formatter = new SimpleDateFormat(ClockConfig.DEFAULT_FORMAT, Locale.getDefault());
            }
            lastFormat = format;
        }

        String displayStr = formatter.format(time.getTime());

        // 判断是否包含空格，拆分为大字体主行 + 小字体副行
        String mainLine = displayStr;
        String subLine = null;
        int spaceIdx = displayStr.indexOf(' ');
        if (spaceIdx > 0 && spaceIdx < displayStr.length() - 1) {
            mainLine = displayStr.substring(0, spaceIdx);
            subLine = displayStr.substring(spaceIdx + 1);
        }

        // 设置数字字体（主行时间）
        Typeface numberTypeface = SystemFontHelper.resolveTypeface(App.getInstance(), config.getNumberFont());
        // 设置中文/英文字体（副行日期）
        Typeface textTypeface = SystemFontHelper.resolveTypeface(App.getInstance(), config.getChineseFont());

        // 基于屏幕宽度计算字号（各时钟类型独立存储）
        timePaint.setTypeface(numberTypeface);
        setTabularNumbers(timePaint); // 启用等宽数字
        float orientScale = ClockConfig.getFontScaleForOrientation(width, height, ClockConfig.ClockStyle.DIGITAL);
        config.setFontScale(orientScale);
        // 用主行格式（空格前部分）生成参考字符串计算字号，避免含日期的长格式压缩字号
        // 这与 MinimalClockRenderer 的逻辑一致：首行=主字号，副行=比例缩放
        String mainFormat = format;
        int fmtSpaceIdx = format.indexOf(' ');
        if (fmtSpaceIdx > 0) {
            mainFormat = format.substring(0, fmtSpaceIdx);
        }
        String refTimeStr = ClockConfig.generateReferenceString(mainFormat);
        int fontSize = config.getFontSizePx(width, refTimeStr, timePaint);

        // 计算布局尺寸
        float subSize = (subLine != null) ? fontSize * 0.35f : 0;
        subPaint.setTypeface(textTypeface);
        // 副行不用 tnum（日期部分不需要等宽数字），仅在时间主行使用
        if (subLine != null) {
            subPaint.setTextSize(subSize);
        }

        // 设置主行画笔属性（用于测量和绘制）
        timePaint.setTextSize(fontSize);

        // 计算文本块尺寸（用参考串测量宽度，确保布局框架稳定不随文本内容抖动）
        float refMainWidth = timePaint.measureText(refTimeStr);
        // 副行参考串：用副行格式部分的归一化字符串
        float refSubWidth = 0;
        if (subLine != null && fmtSpaceIdx > 0 && fmtSpaceIdx < format.length() - 1) {
            String subFormat = format.substring(fmtSpaceIdx + 1);
            String refSubStr = ClockConfig.generateReferenceString(subFormat);
            subPaint.setTextSize(subSize);
            refSubWidth = subPaint.measureText(refSubStr);
        }
        float refBlockWidth = Math.max(refMainWidth, refSubWidth);
        float blockHeight = (subLine != null) ? fontSize + subSize : fontSize;

        // 基于参考串尺寸和用户设置的位置百分比计算坐标（稳定布局框架）
        float blockLeft = config.getPositionX() * (width - refBlockWidth);
        float blockTop = config.getPositionY() * (height - blockHeight);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 防抖动策略：含时分秒（两个冒号）时以分钟段为锚点居中，
        // 即让 mm 段的中心对准区域中心。HH:mm 宽度在一分钟内恒定，
        // 锚点稳定不随秒跳动；其它情况直接居中。
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        float regionCenterX = blockLeft + refBlockWidth / 2f;
        float drawX;
        int c1 = mainLine.indexOf(':');
        int c2 = mainLine.lastIndexOf(':');
        if (c1 > 0 && c2 > c1) {
            // HH:mm:ss 形式：分钟段位于两个冒号之间
            timePaint.setTextAlign(Paint.Align.LEFT);
            float prefixW = timePaint.measureText(mainLine, 0, c1 + 1);  // "HH:"
            float minuteW = timePaint.measureText(mainLine, c1 + 1, c2); // "mm"
            // 让分钟段中心对准区域中心
            drawX = regionCenterX - prefixW - minuteW / 2f;
        } else {
            // 无秒或非时分秒格式：直接居中
            timePaint.setTextAlign(Paint.Align.CENTER);
            drawX = regionCenterX;
        }
        float centerY = blockTop + fontSize * 0.85f;

        // 绘制主行（时间）——使用已设置的对齐方式（有秒LEFT，无秒CENTER）
        timePaint.setColor(color);
        timePaint.setTextSize(fontSize);
        timePaint.setFakeBoldText(true);
        timePaint.setTypeface(numberTypeface);
        timePaint.setAlpha(255);
        timePaint.setShader(null);
        timePaint.setMaskFilter(null);
        canvas.drawText(mainLine, drawX, centerY, timePaint);

        // 副行（日期部分）—— 日期不包含秒，始终居中
        if (subLine != null) {
            subPaint.setColor(color);
            subPaint.setTextSize(subSize);
            subPaint.setTextAlign(Paint.Align.CENTER);
            subPaint.setAlpha(180);
            subPaint.setMaskFilter(null);
            subPaint.setShader(null);
            subPaint.setTypeface(textTypeface);
            // 副行始终居中（日期不会快速变化）
            float subDrawX = blockLeft + refBlockWidth / 2f;
            canvas.drawText(subLine, subDrawX, centerY + fontSize * 0.65f, subPaint);
        }
    }
}
