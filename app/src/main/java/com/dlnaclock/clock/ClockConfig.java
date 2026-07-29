package com.dlnaclock.clock;

import com.dlnaclock.util.PreferenceHelper;

public class ClockConfig {

    public enum ClockStyle {
        DIGITAL(0), ANALOG(1), NEON(2), MINIMAL(3);

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

    private ClockStyle style;
    private String fontFamily;
    private int fontSize;
    private int fontColor;
    private float positionX; // 0.0 to 1.0
    private float positionY; // 0.0 to 1.0
    private boolean showSeconds;
    private boolean showDate;

    public ClockConfig() {
        this.style = ClockStyle.DIGITAL;
        this.fontFamily = "default";
        this.fontSize = 80;
        this.fontColor = 0xFFFFFFFF;
        this.positionX = 0.5f;
        this.positionY = 0.5f;
        this.showSeconds = true;
        this.showDate = true;
    }

    public static ClockConfig fromPreferences() {
        ClockConfig config = new ClockConfig();
        config.style = ClockStyle.fromValue(PreferenceHelper.getClockStyle());
        config.fontFamily = PreferenceHelper.getClockFont();
        config.fontSize = PreferenceHelper.getClockFontSize();
        config.fontColor = PreferenceHelper.getClockFontColor();
        config.positionX = PreferenceHelper.getClockPositionX();
        config.positionY = PreferenceHelper.getClockPositionY();
        config.showSeconds = PreferenceHelper.getShowSeconds();
        config.showDate = PreferenceHelper.getShowDate();
        return config;
    }

    // Getters and setters
    public ClockStyle getStyle() { return style; }
    public void setStyle(ClockStyle style) { this.style = style; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = fontSize; }

    public int getFontColor() { return fontColor; }
    public void setFontColor(int fontColor) { this.fontColor = fontColor; }

    public float getPositionX() { return positionX; }
    public void setPositionX(float positionX) { this.positionX = positionX; }

    public float getPositionY() { return positionY; }
    public void setPositionY(float positionY) { this.positionY = positionY; }

    public boolean isShowSeconds() { return showSeconds; }
    public void setShowSeconds(boolean showSeconds) { this.showSeconds = showSeconds; }

    public boolean isShowDate() { return showDate; }
    public void setShowDate(boolean showDate) { this.showDate = showDate; }
}
