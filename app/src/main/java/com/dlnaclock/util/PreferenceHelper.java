package com.dlnaclock.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.dlnaclock.clock.MinimalRowConfig;

/**
 * PreferenceHelper - SharedPreferences 封装工具类
 * 提供所有设置项的读写方法：时钟配置、防烧屏、背景、DLNA 设备名、视频播放器等
 * 在 App.onCreate 中初始化
 */
public class PreferenceHelper {

    private static SharedPreferences prefs; // 全局 SharedPreferences 实例

    /** init - 初始化 PreferenceHelper，在 App.onCreate 中调用 */
    public static void init(Context context) {
        prefs = context.getSharedPreferences("dlna_clock_prefs", Context.MODE_PRIVATE);
    }

    private static SharedPreferences getPrefs() {
        if (prefs == null) {
            throw new IllegalStateException("PreferenceHelper not initialized. Call init() first.");
        }
        return prefs;
    }

    // === 时钟样式 ===
    public static int getClockStyle() {
        return getPrefs().getInt("clock_style", 0); // 0=Digital, 1=Analog, 2=Minimal
    }

    public static void setClockStyle(int style) {
        getPrefs().edit().putInt("clock_style", style).apply();
    }

    /** getClockFontScale - 获取字号比例（兼容旧代码，默认读取横屏值） */
    public static float getClockFontScale() {
        return getPrefs().getFloat("clock_font_scale", 0.5f);
    }

    /** setClockFontScale - 设置字号比例（兼容旧代码） */
    public static void setClockFontScale(float scale) {
        getPrefs().edit().putFloat("clock_font_scale", scale).apply();
    }

    /** getClockFontScaleLandscape - 获取横屏字号比例 */
    public static float getClockFontScaleLandscape() {
        return getPrefs().getFloat("clock_font_scale_landscape", 0.5f);
    }

    /** setClockFontScaleLandscape - 设置横屏字号比例 */
    public static void setClockFontScaleLandscape(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_landscape", scale).apply();
    }

    /** getClockFontScalePortrait - 获取竖屏字号比例 */
    public static float getClockFontScalePortrait() {
        return getPrefs().getFloat("clock_font_scale_portrait", 0.5f);
    }

    /** setClockFontScalePortrait - 设置竖屏字号比例 */
    public static void setClockFontScalePortrait(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_portrait", scale).apply();
    }

    // === 数字时钟字号比例（横竖屏分别存储） ===
    public static float getClockFontScaleDigitalLandscape() {
        return getPrefs().getFloat("clock_font_scale_digital_landscape", 0.5f);
    }
    public static void setClockFontScaleDigitalLandscape(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_digital_landscape", scale).apply();
    }
    public static float getClockFontScaleDigitalPortrait() {
        return getPrefs().getFloat("clock_font_scale_digital_portrait", 0.5f);
    }
    public static void setClockFontScaleDigitalPortrait(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_digital_portrait", scale).apply();
    }

    // === 模拟时钟字号比例（横竖屏分别存储） ===
    public static float getClockFontScaleAnalogLandscape() {
        return getPrefs().getFloat("clock_font_scale_analog_landscape", 0.5f);
    }
    public static void setClockFontScaleAnalogLandscape(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_analog_landscape", scale).apply();
    }
    public static float getClockFontScaleAnalogPortrait() {
        return getPrefs().getFloat("clock_font_scale_analog_portrait", 0.5f);
    }
    public static void setClockFontScaleAnalogPortrait(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_analog_portrait", scale).apply();
    }

    // === 自定义时钟字号比例（横竖屏分别存储） ===
    public static float getClockFontScaleMinimalLandscape() {
        return getPrefs().getFloat("clock_font_scale_minimal_landscape", 0.5f);
    }
    public static void setClockFontScaleMinimalLandscape(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_minimal_landscape", scale).apply();
    }
    public static float getClockFontScaleMinimalPortrait() {
        return getPrefs().getFloat("clock_font_scale_minimal_portrait", 0.5f);
    }
    public static void setClockFontScaleMinimalPortrait(float scale) {
        getPrefs().edit().putFloat("clock_font_scale_minimal_portrait", scale).apply();
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

    // === 数字时钟字体（字体族：最多3项逗号分隔，按顺序优先渲染） ===
    public static String getNumberFont() {
        return getPrefs().getString("number_font", "Rajdhani Medium,Microsoft YaHei");
    }

    public static void setNumberFont(String font) {
        getPrefs().edit().putString("number_font", font).apply();
    }

    public static String getEnglishFont() {
        return getPrefs().getString("english_font", "Rajdhani Medium,Microsoft YaHei");
    }

    public static void setEnglishFont(String font) {
        getPrefs().edit().putString("english_font", font).apply();
    }

    public static String getChineseFont() {
        return getPrefs().getString("chinese_font", "Rajdhani Medium,Microsoft YaHei");
    }

    public static void setChineseFont(String font) {
        getPrefs().edit().putString("chinese_font", font).apply();
    }

    // === 数字时钟格式 ===
    /** getClockFormat - 获取自定义时钟格式字符串（如 "HH:mm:ss"） */
    public static String getClockFormat() {
        return getPrefs().getString("clock_format", "HH:mm:ss");
    }

    /** setClockFormat - 设置自定义时钟格式字符串 */
    public static void setClockFormat(String format) {
        getPrefs().edit().putString("clock_format", format).apply();
    }

    // === 自定义时钟：5档案 × 3行配置 ===
    public static final int MINIMAL_PROFILE_COUNT = 5;
    public static final int MINIMAL_ROW_COUNT = 3;

    /** getMinimalActiveProfile - 获取当前激活的档案索引 (0-4) */
    public static int getMinimalActiveProfile() {
        return getPrefs().getInt("minimal_active_profile", 2);
    }

    /** setMinimalActiveProfile - 设置当前激活的档案索引 */
    public static void setMinimalActiveProfile(int profile) {
        getPrefs().edit().putInt("minimal_active_profile", Math.max(0, Math.min(profile, MINIMAL_PROFILE_COUNT - 1))).apply();
    }

    /** getMinimalRowConfig - 读取指定档案的行配置（自动迁移旧配置） */
    public static MinimalRowConfig getMinimalRowConfig(int profile, int row) {
        String key = "minimal_profile_" + profile + "_row_" + row;
        String json = getPrefs().getString(key, null);
        if (json != null) {
            return MinimalRowConfig.fromJson(json);
        }
        // 档案0首次读取时，尝试从旧配置迁移
        if (profile == 0 && !getPrefs().getBoolean("minimal_migrated", false)) {
            migrateOldMinimalConfig();
            // 迁移后重新读取
            json = getPrefs().getString(key, null);
            if (json != null) {
                return MinimalRowConfig.fromJson(json);
            }
        }
        // 无旧配置可迁移，返回默认
        if (row == 0) return MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.TIME);
        if (row == 1) return MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.DATE);
        return MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.STATUS);
    }

    /** setMinimalRowConfig - 保存指定档案的行配置 */
    public static void setMinimalRowConfig(int profile, int row, MinimalRowConfig config) {
        String key = "minimal_profile_" + profile + "_row_" + row;
        getPrefs().edit().putString(key, config.toJson()).apply();
    }

    /** migrateOldMinimalConfig - 将旧版扁平配置迁移到档案0 */
    private static void migrateOldMinimalConfig() {
        SharedPreferences p = getPrefs();
        // 行0：时间
        MinimalRowConfig row0 = MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.TIME);
        row0.setFormat(p.getString("minimal_time_format", "HH:mm:ss"));
        row0.setUse12Hour(p.getBoolean("minimal_use_12hour", false));
        row0.setFontName(p.getString("minimal_time_font", MinimalRowConfig.DEFAULT_ROW_FONT));
        row0.setColor(p.getInt("clock_font_color", 0xFFFFFFFF));
        setMinimalRowConfig(0, 0, row0);

        // 行1：日期
        MinimalRowConfig row1 = MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.DATE);
        row1.setFormat(p.getString("minimal_date_format", "yyyy-MM-dd"));
        row1.setFontName(p.getString("minimal_date_font", MinimalRowConfig.DEFAULT_ROW_FONT));
        row1.setColor(0xB4FFFFFF);
        setMinimalRowConfig(0, 1, row1);

        // 行2：状态
        MinimalRowConfig row2 = MinimalRowConfig.createDefault(MinimalRowConfig.ContentType.STATUS);
        row2.setStatusItems(p.getInt("minimal_status_items", 0));
        row2.setRotateInterval(p.getInt("minimal_rotate_interval", 5));
        row2.setFontName(p.getString("minimal_status_font", MinimalRowConfig.DEFAULT_ROW_FONT));
        row2.setCustomText(p.getString("minimal_custom_text", ""));
        row2.setColor(0x8CFFFFFF);
        setMinimalRowConfig(0, 2, row2);

        p.edit().putBoolean("minimal_migrated", true).apply();
    }

    // === 防烧屏设置 ===
    /** 防烧屏总开关（合并位置偏移+像素微偏移） */
    public static boolean isAntiBurnInEnabled() {
        return getPrefs().getBoolean("anti_burn_in", false);
    }

    public static void setAntiBurnInEnabled(boolean enabled) {
        getPrefs().edit().putBoolean("anti_burn_in", enabled).apply();
    }

    /** @deprecated 已合并到 isAntiBurnInEnabled，保留兼容 */
    @Deprecated
    public static boolean isPixelShiftEnabled() {
        return isAntiBurnInEnabled();
    }

    /** @deprecated 已合并到 setAntiBurnInEnabled */
    @Deprecated
    public static void setPixelShiftEnabled(boolean enabled) {
        setAntiBurnInEnabled(enabled);
    }

    public static int getBurnInInterval() {
        return getPrefs().getInt("burn_in_interval", 30); // seconds
    }

    public static void setBurnInInterval(int seconds) {
        getPrefs().edit().putInt("burn_in_interval", seconds).apply();
    }

    /** 偏移幅度（屏幕百分比），默认 15% */
    public static int getBurnInOffsetRange() {
        return getPrefs().getInt("burn_in_offset_range", 15); // percent
    }

    public static void setBurnInOffsetRange(int percent) {
        getPrefs().edit().putInt("burn_in_offset_range", percent).apply();
    }

    /**
     * @deprecated 像素微移已合并进偏移幅度，由偏移幅度推导（15% → 8px）
     */
    @Deprecated
    public static int getBurnInPixelRange() {
        return Math.max(1, Math.round(getBurnInOffsetRange() * 0.5f)); // percent * 0.5 -> px
    }

    /** @deprecated 像素微移已合并进偏移幅度，无需单独设置 */
    @Deprecated
    public static void setBurnInPixelRange(int pixels) {
        getPrefs().edit().putInt("burn_in_pixel_range", pixels).apply();
    }

    /** 防烧屏方式：0=随机偏移 1=弹射运动，默认随机偏移 */
    public static int getAntiBurnInMode() {
        return getPrefs().getInt("anti_burn_in_mode", 0);
    }

    public static void setAntiBurnInMode(int mode) {
        getPrefs().edit().putInt("anti_burn_in_mode", mode).apply();
    }

    /** 弹射每次撞边随机偏移角度（0~90°），默认 30° */
    public static int getBounceAngleRange() {
        return getPrefs().getInt("bounce_angle_range", 30); // degrees
    }

    public static void setBounceAngleRange(int degrees) {
        getPrefs().edit().putInt("bounce_angle_range", degrees).apply();
    }

    /** 弹射移动速度（%屏幕宽/秒），默认 8 */
    public static int getBounceSpeed() {
        return getPrefs().getInt("bounce_speed", 8); // percent of screen width per second
    }

    public static void setBounceSpeed(int speed) {
        getPrefs().edit().putInt("bounce_speed", speed).apply();
    }

    // === 背景设置 ===
    public static int getBackgroundMode() {
        return getPrefs().getInt("bg_mode", 3); // 0=Color, 1=Image, 2=Video, 3=Wallpaper
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

    /** isBackgroundVideoMuted - 背景视频是否静音（默认 false，播放声音） */
    public static boolean isBackgroundVideoMuted() {
        return getPrefs().getBoolean("bg_video_muted", false);
    }

    /** setBackgroundVideoMuted - 设置背景视频静音 */
    public static void setBackgroundVideoMuted(boolean muted) {
        getPrefs().edit().putBoolean("bg_video_muted", muted).apply();
    }

    /** getBackgroundImageFit - 获取图片适应模式 (0=CenterCrop, 1=Stretch, 2=FitWidth, 3=FitHeight, 4=Center) */
    public static int getBackgroundImageFit() {
        return getPrefs().getInt("bg_image_fit", 0);
    }

    public static void setBackgroundImageFit(int fitMode) {
        getPrefs().edit().putInt("bg_image_fit", fitMode).apply();
    }

    // === 动态壁纸设置 ===
    public static int getWallpaperType() {
        return getPrefs().getInt("bg_wallpaper_type", 10);
    }

    public static void setWallpaperType(int type) {
        getPrefs().edit().putInt("bg_wallpaper_type", type).apply();
    }

    // === Lua 自定义壁纸设置 ===
    /** 获取当前选中的 Lua 壁纸脚本路径（builtin:xxx 或外部文件路径） */
    public static String getSelectedLuaScript() {
        return getPrefs().getString("selected_lua_script", "builtin:particles");
    }

    /** 设置当前选中的 Lua 壁纸脚本路径 */
    public static void setSelectedLuaScript(String scriptPath) {
        getPrefs().edit().putString("selected_lua_script", scriptPath).apply();
    }

    /** 获取壁纸参数值（键名: wp_param_{type}_{key}） */
    public static float getWallpaperParam(int wallpaperType, String key, float defaultVal) {
        return getPrefs().getFloat("wp_param_" + wallpaperType + "_" + key, defaultVal);
    }

    /** 设置壁纸参数值 */
    public static void setWallpaperParam(int wallpaperType, String key, float value) {
        getPrefs().edit().putFloat("wp_param_" + wallpaperType + "_" + key, value).apply();
    }

    // === DLNA 设置 ===
    public static boolean isDlnaEnabled() {
        return getPrefs().getBoolean("dlna_enabled", true); // 默认开启
    }

    public static void setDlnaEnabled(boolean enabled) {
        getPrefs().edit().putBoolean("dlna_enabled", enabled).apply();
    }

    public static String getDeviceName() {
        return getPrefs().getString("device_name", "DLNA Clock ScreenSaver");
    }

    public static void setDeviceName(String name) {
        getPrefs().edit().putString("device_name", name).apply();
    }

    /** getUdn - 获取持久化的 UDN（首次调用时基于设备信息生成并保存） */
    public static String getUdn() {
        String udn = getPrefs().getString("device_udn", null);
        if (udn == null) {
            String info = android.os.Build.MANUFACTURER + "_" +
                    android.os.Build.MODEL + "_" +
                    android.os.Build.SERIAL;
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(info.getBytes());
            udn = "uuid:" + uuid.toString();
            getPrefs().edit().putString("device_udn", udn).apply();
        }
        return udn;
    }

    public static String getVideoPlayerPackage() {
        return getPrefs().getString("video_player_package", "");
    }

    public static void setVideoPlayerPackage(String packageName) {
        getPrefs().edit().putString("video_player_package", packageName).apply();
    }

    // === 调试日志 ===
    public static boolean isDebugLogEnabled() {
        return getPrefs().getBoolean("debug_log_enabled", false);
    }

    public static void setDebugLogEnabled(boolean enabled) {
        getPrefs().edit().putBoolean("debug_log_enabled", enabled).apply();
    }

    // === AirPlay 设置 ===
    public static boolean isAirPlayEnabled() {
        return getPrefs().getBoolean("airplay_enabled", false);
    }

    public static void setAirPlayEnabled(boolean enabled) {
        getPrefs().edit().putBoolean("airplay_enabled", enabled).apply();
    }

    public static String getAirPlayDeviceName() {
        return getPrefs().getString("airplay_device_name", "DlnaClock AirPlay");
    }

    public static void setAirPlayDeviceName(String name) {
        getPrefs().edit().putString("airplay_device_name", name).apply();
    }

    // === OSD 设置 ===
    public static int getOsdFontSize() {
        return getPrefs().getInt("osd_font_size", 170);
    }

    public static void setOsdFontSize(int size) {
        getPrefs().edit().putInt("osd_font_size", size).apply();
    }

    public static boolean getOsdTimeEnabled() {
        return getPrefs().getBoolean("osd_time_enabled", false);
    }

    public static void setOsdTimeEnabled(boolean enabled) {
        getPrefs().edit().putBoolean("osd_time_enabled", enabled).apply();
    }

    public static int getOsdFontColor() {
        return getPrefs().getInt("osd_font_color", 0xFFFFFFFF);
    }

    public static void setOsdFontColor(int color) {
        getPrefs().edit().putInt("osd_font_color", color).apply();
    }

    public static float getOsdPositionX() {
        return getPrefs().getFloat("osd_position_x", 0.097f);
    }

    public static void setOsdPositionX(float x) {
        getPrefs().edit().putFloat("osd_position_x", x).apply();
    }

    public static float getOsdPositionY() {
        return getPrefs().getFloat("osd_position_y", 0.497f);
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
