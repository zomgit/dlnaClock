package com.dlnaclock.screensaver.wallpaper;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.dlnaclock.App;

/**
 * ParamStore - 壁纸参数持久化存储
 * 键名规则：wp_param_{wallpaperType}_{paramKey}
 * 默认值时不在 SharedPrefs 中存储，减少冗余
 */
public class ParamStore {

    private static final String PREFIX = "wp_param_";

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(App.getInstance());
    }

    /** 生成存储键 */
    private static String makeKey(int wallpaperType, String paramKey) {
        return PREFIX + wallpaperType + "_" + paramKey;
    }

    /** 保存参数值 */
    public static void saveParam(int wallpaperType, String key, float value) {
        prefs().edit().putFloat(makeKey(wallpaperType, key), value).apply();
    }

    /** 保存颜色参数（存储为 int） */
    public static void saveColorParam(int wallpaperType, String key, int color) {
        prefs().edit().putInt(makeKey(wallpaperType, key), color).apply();
    }

    /** 获取参数值（若无记录返回默认值，兼容 int/float 混存） */
    public static float getParam(int wallpaperType, String key, float defaultVal) {
        try {
            return prefs().getFloat(makeKey(wallpaperType, key), defaultVal);
        } catch (ClassCastException e) {
            // 兼容：旧版可能以 int 存储
            try {
                return (float) prefs().getInt(makeKey(wallpaperType, key), (int) defaultVal);
            } catch (Exception e2) {
                return defaultVal;
            }
        }
    }

    /** 获取颜色参数值（兼容 float/int 混存） */
    public static int getColorParam(int wallpaperType, String key, int defaultColor) {
        try {
            return prefs().getInt(makeKey(wallpaperType, key), defaultColor);
        } catch (ClassCastException e) {
            // 兼容：控制面板可能以 float 存储了颜色值
            try {
                return (int) prefs().getFloat(makeKey(wallpaperType, key), defaultColor);
            } catch (Exception e2) {
                return defaultColor;
            }
        }
    }

    /** 根据 ParamDef 数组加载所有参数到 Bundle（自动钳制范围，防止危险值导致OOM） */
    public static Bundle loadParams(int wallpaperType, ParamDef[] defs) {
        Bundle bundle = new Bundle();
        if (defs == null) return bundle;
        for (ParamDef def : defs) {
            if (def.type == ParamDef.Type.COLOR) {
                int color = getColorParam(wallpaperType, def.key, (int) def.defaultValue);
                bundle.putFloat(def.key, color);
            } else {
                float val = getParam(wallpaperType, def.key, def.defaultValue);
                // 安全钳制：确保加载的值在合法范围内
                if (val < def.min) val = def.defaultValue;
                if (val > def.max) val = def.max;
                bundle.putFloat(def.key, val);
            }
        }
        return bundle;
    }

    /** 将 Bundle 中的所有参数持久化 */
    public static void saveParams(int wallpaperType, ParamDef[] defs, Bundle bundle) {
        if (defs == null || bundle == null) return;
        SharedPreferences.Editor editor = prefs().edit();
        for (ParamDef def : defs) {
            String fullKey = makeKey(wallpaperType, def.key);
            if (def.type == ParamDef.Type.COLOR) {
                editor.putInt(fullKey, (int) bundle.getFloat(def.key, def.defaultValue));
            } else {
                editor.putFloat(fullKey, bundle.getFloat(def.key, def.defaultValue));
            }
        }
        editor.apply();
    }

    /** 重置某壁纸类型的所有参数为默认值 */
    public static void resetParams(int wallpaperType, ParamDef[] defs) {
        if (defs == null) return;
        SharedPreferences.Editor editor = prefs().edit();
        for (ParamDef def : defs) {
            editor.remove(makeKey(wallpaperType, def.key));
        }
        editor.apply();
    }
}
