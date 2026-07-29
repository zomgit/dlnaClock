package com.dlnaclock.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceHelper {

    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences("dlna_clock_prefs", Context.MODE_PRIVATE);
    }

    private static SharedPreferences getPrefs() {
        if (prefs == null) {
            throw new IllegalStateException("PreferenceHelper not initialized. Call init() first.");
        }
        return prefs;
    }

    // Clock settings
    public static int getClockStyle() {
        return getPrefs().getInt("clock_style", 0); // 0=Digital, 1=Analog, 2=Neon, 3=Minimal
    }

    public static void setClockStyle(int style) {
        getPrefs().edit().putInt("clock_style", style).apply();
    }

    public static String getClockFont() {
        return getPrefs().getString("clock_font", "default");
    }

    public static void setClockFont(String font) {
        getPrefs().edit().putString("clock_font", font).apply();
    }

    public static int getClockFontSize() {
        return getPrefs().getInt("clock_font_size", 80);
    }

    public static void setClockFontSize(int size) {
        getPrefs().edit().putInt("clock_font_size", size).apply();
    }

    public static int getClockFontColor() {
        return getPrefs().getInt("clock_font_color", 0xFFFFFFFF);
    }

    public static void setClockFontColor(int color) {
        getPrefs().edit().putInt("clock_font_color", color).apply();
    }

    public static float getClockPositionX() {
        return getPrefs().getFloat("clock_position_x", 0.5f);
    }

    public static void setClockPositionX(float x) {
        getPrefs().edit().putFloat("clock_position_x", x).apply();
    }

    public static float getClockPositionY() {
        return getPrefs().getFloat("clock_position_y", 0.5f);
    }

    public static void setClockPositionY(float y) {
        getPrefs().edit().putFloat("clock_position_y", y).apply();
    }

    public static boolean getShowSeconds() {
        return getPrefs().getBoolean("show_seconds", true);
    }

    public static void setShowSeconds(boolean show) {
        getPrefs().edit().putBoolean("show_seconds", show).apply();
    }

    public static boolean getShowDate() {
        return getPrefs().getBoolean("show_date", true);
    }

    public static void setShowDate(boolean show) {
        getPrefs().edit().putBoolean("show_date", show).apply();
    }

    // Anti burn-in settings
    public static boolean isAntiBurnInEnabled() {
        return getPrefs().getBoolean("anti_burn_in", true);
    }

    public static void setAntiBurnInEnabled(boolean enabled) {
        getPrefs().edit().putBoolean("anti_burn_in", enabled).apply();
    }

    public static boolean isPixelShiftEnabled() {
        return getPrefs().getBoolean("pixel_shift", false);
    }

    public static void setPixelShiftEnabled(boolean enabled) {
        getPrefs().edit().putBoolean("pixel_shift", enabled).apply();
    }

    public static int getBurnInInterval() {
        return getPrefs().getInt("burn_in_interval", 30); // seconds
    }

    public static void setBurnInInterval(int seconds) {
        getPrefs().edit().putInt("burn_in_interval", seconds).apply();
    }

    // Background settings
    public static int getBackgroundMode() {
        return getPrefs().getInt("bg_mode", 0); // 0=Color, 1=Image, 2=Video
    }

    public static void setBackgroundMode(int mode) {
        getPrefs().edit().putInt("bg_mode", mode).apply();
    }

    public static int getBackgroundColor() {
        return getPrefs().getInt("bg_color", 0xFF000000);
    }

    public static void setBackgroundColor(int color) {
        getPrefs().edit().putInt("bg_color", color).apply();
    }

    public static String getBackgroundImagePath() {
        return getPrefs().getString("bg_image_path", "");
    }

    public static void setBackgroundImagePath(String path) {
        getPrefs().edit().putString("bg_image_path", path).apply();
    }

    public static String getBackgroundVideoPath() {
        return getPrefs().getString("bg_video_path", "");
    }

    public static void setBackgroundVideoPath(String path) {
        getPrefs().edit().putString("bg_video_path", path).apply();
    }

    // DLNA settings
    public static String getDeviceName() {
        return getPrefs().getString("device_name", "DLNA Clock ScreenSaver");
    }

    public static void setDeviceName(String name) {
        getPrefs().edit().putString("device_name", name).apply();
    }

    // OSD settings
    public static int getOsdFontSize() {
        return getPrefs().getInt("osd_font_size", 24);
    }

    public static void setOsdFontSize(int size) {
        getPrefs().edit().putInt("osd_font_size", size).apply();
    }

    public static int getOsdFontColor() {
        return getPrefs().getInt("osd_font_color", 0xFFFFFFFF);
    }

    public static void setOsdFontColor(int color) {
        getPrefs().edit().putInt("osd_font_color", color).apply();
    }

    public static float getOsdPositionX() {
        return getPrefs().getFloat("osd_position_x", 0.05f);
    }

    public static void setOsdPositionX(float x) {
        getPrefs().edit().putFloat("osd_position_x", x).apply();
    }

    public static float getOsdPositionY() {
        return getPrefs().getFloat("osd_position_y", 0.05f);
    }

    public static void setOsdPositionY(float y) {
        getPrefs().edit().putFloat("osd_position_y", y).apply();
    }

    public static int getOsdOpacity() {
        return getPrefs().getInt("osd_opacity", 200);
    }

    public static void setOsdOpacity(int opacity) {
        getPrefs().edit().putInt("osd_opacity", opacity).apply();
    }
}
