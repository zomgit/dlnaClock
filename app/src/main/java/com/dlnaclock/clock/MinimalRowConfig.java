package com.dlnaclock.clock;

import org.json.JSONObject;

/**
 * MinimalRowConfig - 自定义时钟单行配置
 * 每行独立配置：内容类型、格式、字体、颜色、自定义文本、状态项等
 * 支持 JSON 序列化/反序列化，用于 SharedPreferences 存储
 */
public class MinimalRowConfig {

    /** ContentType - 行内容类型枚举 */
    public enum ContentType {
        NONE(0),    // 不显示
        TIME(1),    // 时间
        DATE(2),    // 日期
        STATUS(3),  // 状态信息轮播
        CUSTOM(4);  // 自定义文本

        private final int value;
        ContentType(int value) { this.value = value; }
        public int getValue() { return value; }

        public static ContentType fromValue(int value) {
            for (ContentType t : values()) {
                if (t.value == value) return t;
            }
            return NONE;
        }
    }

    private ContentType contentType;  // 内容类型
    private String format;            // 时间/日期格式字符串（如 "HH:mm:ss"）
    private boolean use12Hour;        // 是否12小时制（仅TIME类型有效）
    private String fontName;          // 字体名称
    private int color;                // ARGB 颜色值
    private String customText;        // 自定义显示文本（仅CUSTOM类型有效）
    private int statusItems;          // 状态位掩码（仅STATUS类型有效）
    private int rotateInterval;       // 轮播间隔秒数（仅STATUS类型有效）
    private float sizeRatio;          // 字号比例（相对于主字号，默认1.0）

    /** 默认字体族（最多3项，按顺序优先渲染，逗号分隔） */
    public static final String DEFAULT_ROW_FONT = "Rajdhani Medium,Microsoft YaHei";

    /** 创建默认配置 */
    public MinimalRowConfig() {
        this.contentType = ContentType.NONE;
        this.format = "HH:mm:ss";
        this.use12Hour = false;
        this.fontName = DEFAULT_ROW_FONT;
        this.color = 0xFFFFFFFF;
        this.customText = "";
        this.statusItems = 0;
        this.rotateInterval = 5;
        this.sizeRatio = 1.0f;
    }

    /** 创建指定内容类型的默认配置 */
    public static MinimalRowConfig createDefault(ContentType type) {
        MinimalRowConfig config = new MinimalRowConfig();
        config.contentType = type;
        switch (type) {
            case TIME:
                config.format = "HH:mm:ss";
                config.fontName = DEFAULT_ROW_FONT;
                config.color = 0xFFFFFFFF;
                break;
            case DATE:
                config.format = "yyyy-MM-dd";
                config.fontName = DEFAULT_ROW_FONT;
                config.color = 0xFFFFFFFF;
                break;
            case STATUS:
                config.fontName = DEFAULT_ROW_FONT;
                config.color = 0xFFFFFFFF;
                config.statusItems = 3;
                config.rotateInterval = 5;
                break;
            case CUSTOM:
                config.fontName = DEFAULT_ROW_FONT;
                config.color = 0xFFFFFFFF;
                config.customText = "";
                break;
            case NONE:
            default:
                break;
        }
        return config;
    }

    /** 深拷贝 */
    public MinimalRowConfig copy() {
        MinimalRowConfig c = new MinimalRowConfig();
        c.contentType = this.contentType;
        c.format = this.format;
        c.use12Hour = this.use12Hour;
        c.fontName = this.fontName;
        c.color = this.color;
        c.customText = this.customText;
        c.statusItems = this.statusItems;
        c.rotateInterval = this.rotateInterval;
        c.sizeRatio = this.sizeRatio;
        return c;
    }

    // === JSON 序列化 ===

    /** toJson - 序列化为 JSON 字符串 */
    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("contentType", contentType.getValue());
            obj.put("format", format != null ? format : "");
            obj.put("use12Hour", use12Hour);
            obj.put("fontName", fontName != null ? fontName : "default");
            obj.put("color", color);
            obj.put("customText", customText != null ? customText : "");
            obj.put("statusItems", statusItems);
            obj.put("rotateInterval", rotateInterval);
            obj.put("sizeRatio", sizeRatio);
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** fromJson - 从 JSON 字符串反序列化 */
    public static MinimalRowConfig fromJson(String json) {
        MinimalRowConfig config = new MinimalRowConfig();
        if (json == null || json.isEmpty()) return config;
        try {
            JSONObject obj = new JSONObject(json);
            config.contentType = ContentType.fromValue(obj.optInt("contentType", 0));
            config.format = obj.optString("format", "HH:mm:ss");
            config.use12Hour = obj.optBoolean("use12Hour", false);
            config.fontName = obj.optString("fontName", DEFAULT_ROW_FONT);
            config.color = obj.optInt("color", 0xFFFFFFFF);
            config.customText = obj.optString("customText", "");
            config.statusItems = obj.optInt("statusItems", 0);
            config.rotateInterval = obj.optInt("rotateInterval", 5);
            config.sizeRatio = (float) obj.optDouble("sizeRatio", 1.0);
        } catch (Exception e) {
            // 解析失败返回默认配置
        }
        return config;
    }

    // === Getters and Setters ===

    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public boolean isUse12Hour() { return use12Hour; }
    public void setUse12Hour(boolean use12Hour) { this.use12Hour = use12Hour; }

    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public String getCustomText() { return customText; }
    public void setCustomText(String customText) { this.customText = customText; }

    public int getStatusItems() { return statusItems; }
    public void setStatusItems(int statusItems) { this.statusItems = statusItems; }

    public int getRotateInterval() { return rotateInterval; }
    public void setRotateInterval(int rotateInterval) { this.rotateInterval = rotateInterval; }

    public float getSizeRatio() { return sizeRatio; }
    public void setSizeRatio(float sizeRatio) { this.sizeRatio = sizeRatio; }
}
