package com.dlnaclock.clock;

import android.graphics.Paint;

import com.dlnaclock.util.PreferenceHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * ClockConfig - 时钟配置模型
 * 存储时钟样式、字体、字号比例（占屏百分比）、颜色、位置、自定义格式等
 * 数字时钟支持：数字字体、英文字体、中文字体
 * 自定义时钟支持：三行独立配置（时间/日期/状态轮播）
 */
public class ClockConfig {

    /** ClockStyle - 时钟样式枚举 */
    public enum ClockStyle {
        DIGITAL(0),   // 数字时钟
        ANALOG(1),    // 模拟时钟
        MINIMAL(2);   // 自定义时钟（三行）

        private final int value;
        ClockStyle(int value) { this.value = value; }
        public int getValue() { return value; }

        public static ClockStyle fromValue(int value) {
            for (ClockStyle style : values()) {
                if (style.value == value) return style;
            }
            return DIGITAL;
        }
    }

    // === 通用配置 ===
    private ClockStyle style;      // 时钟样式
    private float fontScale;       // 字号比例（0.05-1.00，各时钟类型×横竖屏分别存储）
    private int fontColor;         // 字体颜色
    private float positionX;       // 水平位置（0.0-1.0）
    private float positionY;       // 垂直位置（0.0-1.0）

    // === 数字时钟字体配置 ===
    private String numberFont;     // 数字字体（默认 Rajdhani Medium）
    private String englishFont;    // 英文字体（默认微软雅黑）
    private String chineseFont;    // 中文字体（默认微软雅黑）

    // === 数字时钟格式 ===
    private String clockFormat;    // 自定义时间格式（如 "HH:mm:ss"）

    // === 自定义时钟配置（3行独立配置） ===
    private MinimalRowConfig[] minimalRows = new MinimalRowConfig[3];

    // === 状态行位掩码常量 ===
    public static final int STATUS_CPU      = 0x01;  // CPU 利用率
    public static final int STATUS_BATTERY  = 0x02;  // 电量
    public static final int STATUS_APP_TIME = 0x04;  // 本 APP 已运行时间
    public static final int STATUS_DEV_TIME = 0x08;  // 手机已开机时间
    public static final int STATUS_CUSTOM   = 0x10;  // 自定义内容

    /** 默认时间格式 */
    public static final String DEFAULT_FORMAT = "HH:mm:ss";

    /** 数字时钟默认字体 */
    public static final String DEFAULT_NUMBER_FONT = "Rajdhani Medium";
    public static final String DEFAULT_ENGLISH_FONT = "default";
    public static final String DEFAULT_CHINESE_FONT = "default";

    public ClockConfig() {
        this.style = ClockStyle.DIGITAL;
        this.fontScale = 0.12f;
        this.fontColor = 0xFFFFFFFF;
        this.positionX = 0.5f;
        this.positionY = 0.5f;

        this.numberFont = DEFAULT_NUMBER_FONT;
        this.englishFont = DEFAULT_ENGLISH_FONT;
        this.chineseFont = DEFAULT_CHINESE_FONT;
        this.clockFormat = DEFAULT_FORMAT;

        // 自定义时钟默认行配置
        this.minimalRows[0] = MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.TIME);
        this.minimalRows[1] = MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.DATE);
        this.minimalRows[2] = MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.STATUS);
    }

    /** fromPreferences - 从 SharedPreferences 加载时钟配置 */
    public static ClockConfig fromPreferences() {
        ClockConfig config = new ClockConfig();
        config.style = ClockStyle.fromValue(PreferenceHelper.getClockStyle());
        // 根据当前屏幕方向加载对应的字号比例（延迟到 getFontSizePx 时处理）
        config.fontScale = PreferenceHelper.getClockFontScale();
        config.fontColor = PreferenceHelper.getClockFontColor();
        config.positionX = PreferenceHelper.getClockPositionX();
        config.positionY = PreferenceHelper.getClockPositionY();

        // 数字时钟
        config.numberFont = PreferenceHelper.getNumberFont();
        config.englishFont = PreferenceHelper.getEnglishFont();
        config.chineseFont = PreferenceHelper.getChineseFont();
        config.clockFormat = PreferenceHelper.getClockFormat();

        // 自定义时钟：从当前档案加载3行配置
        int profile = PreferenceHelper.getMinimalActiveProfile();
        for (int i = 0; i < 3; i++) {
            config.minimalRows[i] = PreferenceHelper.getMinimalRowConfig(profile, i);
        }

        return config;
    }

    /**
     * getFontSizePx - 基于屏幕宽度计算字号，使时钟最宽占屏幕宽度的 fontScale×80%
     * 例如 fontScale=1.0 时占80%，fontScale=0.1 时占8%
     * @param screenWidth 屏幕宽度（像素）
     * @param timeString  当前时间字符串（用于测量宽度比）
     * @param paint       已设置字体的画笔（用于测量）
     * @return 实际字号像素
     */
    public int getFontSizePx(int screenWidth, String timeString, Paint paint) {
        if (timeString == null || timeString.isEmpty() || paint == null) {
            return Math.max(12, (int) (screenWidth * fontScale * 0.8f / 8f));
        }
        // 目标文本宽度 = 屏幕宽度 × 用户比例 × 0.8（最大80%）
        float targetWidth = screenWidth * Math.min(fontScale, 1.0f) * 0.8f;

        // 在参考字号下测量时间字符串宽度
        float refSize = 100f;
        paint.setTextSize(refSize);
        float measuredWidth = paint.measureText(timeString);
        if (measuredWidth <= 0) {
            return Math.max(12, (int) (screenWidth * fontScale * 0.8f / 8f));
        }

        // 按比例计算目标字号
        float fontSize = refSize * targetWidth / measuredWidth;
        return Math.max(12, Math.round(fontSize));
    }

    /**
     * getFontScaleForOrientation - 根据屏幕方向和时钟样式读取对应的字号比例
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param style 时钟样式
     * @return 当前方向和样式的字号比例
     */
    public static float getFontScaleForOrientation(int screenWidth, int screenHeight, ClockStyle style) {
        boolean isLandscape = screenWidth > screenHeight;
        switch (style) {
            case DIGITAL:
                return isLandscape ?
                    PreferenceHelper.getClockFontScaleDigitalLandscape() :
                    PreferenceHelper.getClockFontScaleDigitalPortrait();
            case ANALOG:
                return isLandscape ?
                    PreferenceHelper.getClockFontScaleAnalogLandscape() :
                    PreferenceHelper.getClockFontScaleAnalogPortrait();
            case MINIMAL:
                return isLandscape ?
                    PreferenceHelper.getClockFontScaleMinimalLandscape() :
                    PreferenceHelper.getClockFontScaleMinimalPortrait();
            default:
                return isLandscape ?
                    PreferenceHelper.getClockFontScaleDigitalLandscape() :
                    PreferenceHelper.getClockFontScaleDigitalPortrait();
        }
    }

    /**
     * generateReferenceString - 根据时间格式生成固定宽度的参考字符串
     * 将格式中所有连续格式字母（H/m/s/h等）归一化为2个字符，
     * 确保 "H:m:s" → "HH:mm:ss"，"h:mm a" → "hh:mm aa" 等。
     * 然后用午夜时间格式化，得到如 "00:00:00" 的固定宽度字符串。
     * 用于字号计算，避免每秒时间变化导致字号和位置跳动。
     * @param format SimpleDateFormat 格式字符串
     * @return 固定宽度的参考时间字符串
     */
    public static String generateReferenceString(String format) {
        if (format == null || format.isEmpty()) return "00:00:00";
        try {
            // 将所有连续格式字母归一化为恰好 2 个
            // H→HH, h→hh, m→mm, s→ss, E→EE, a→aa 等
            String normalized = format.replaceAll("([HhmsaASeEkKwWzZGMdDFYL])\\1*", "$1$1");
            SimpleDateFormat sdf = new SimpleDateFormat(normalized, Locale.getDefault());
            Calendar ref = Calendar.getInstance();
            ref.set(Calendar.HOUR_OF_DAY, 0);
            ref.set(Calendar.MINUTE, 0);
            ref.set(Calendar.SECOND, 0);
            ref.set(Calendar.MILLISECOND, 0);
            return sdf.format(ref.getTime());
        } catch (Exception e) {
            return "00:00:00";
        }
    }

    // === Getters and setters ===
    public ClockStyle getStyle() { return style; }
    public void setStyle(ClockStyle style) { this.style = style; }

    public float getFontScale() { return fontScale; }
    public void setFontScale(float fontScale) { this.fontScale = fontScale; }

    public int getFontColor() { return fontColor; }
    public void setFontColor(int fontColor) { this.fontColor = fontColor; }

    public float getPositionX() { return positionX; }
    public void setPositionX(float positionX) { this.positionX = positionX; }

    public float getPositionY() { return positionY; }
    public void setPositionY(float positionY) { this.positionY = positionY; }

    public String getNumberFont() { return numberFont; }
    public void setNumberFont(String numberFont) { this.numberFont = numberFont; }

    public String getEnglishFont() { return englishFont; }
    public void setEnglishFont(String englishFont) { this.englishFont = englishFont; }

    public String getChineseFont() { return chineseFont; }
    public void setChineseFont(String chineseFont) { this.chineseFont = chineseFont; }

    public String getClockFormat() { return clockFormat; }
    public void setClockFormat(String clockFormat) { this.clockFormat = clockFormat; }

    // === 自定义时钟行配置 ===
    public MinimalRowConfig[] getMinimalRows() { return minimalRows; }
    public void setMinimalRows(MinimalRowConfig[] rows) { this.minimalRows = rows; }

    public MinimalRowConfig getMinimalRow(int index) {
        if (index >= 0 && index < 3 && minimalRows[index] != null) return minimalRows[index];
        return new MinimalRowConfig();
    }

    public void setMinimalRow(int index, MinimalRowConfig row) {
        if (index >= 0 && index < 3) minimalRows[index] = row;
    }

    // === Minimal 便捷方法（委托到 minimalRows[]，简化外部调用） ===

    /** 时间行字体（第1行） */
    public String getMinimalTimeFont() {
        return minimalRows[0] != null ? minimalRows[0].getFontName() : DEFAULT_NUMBER_FONT;
    }

    /** 日期行字体（第2行） */
    public String getMinimalDateFont() {
        return minimalRows[1] != null ? minimalRows[1].getFontName() : DEFAULT_CHINESE_FONT;
    }

    /** 状态行字体（第3行） */
    public String getMinimalStatusFont() {
        return minimalRows[2] != null ? minimalRows[2].getFontName() : DEFAULT_CHINESE_FONT;
    }

    /** 时间行格式 */
    public String getMinimalTimeFormat() {
        return minimalRows[0] != null ? minimalRows[0].getFormat() : DEFAULT_FORMAT;
    }

    /** 日期行格式 */
    public String getMinimalDateFormat() {
        return minimalRows[1] != null ? minimalRows[1].getFormat() : "yyyy-MM-dd";
    }

    /** 时间行是否12小时制 */
    public boolean isMinimalUse12Hour() {
        return minimalRows[0] != null && minimalRows[0].isUse12Hour();
    }

    /** 状态行位掩码 */
    public int getMinimalStatusItems() {
        return minimalRows[2] != null ? minimalRows[2].getStatusItems() : 0;
    }

    /** 状态行轮播间隔 */
    public int getMinimalRotateInterval() {
        return minimalRows[2] != null ? minimalRows[2].getRotateInterval() : 5;
    }

    /** 状态行自定义文本 */
    public String getMinimalCustomText() {
        return minimalRows[2] != null ? minimalRows[2].getCustomText() : "";
    }
}
