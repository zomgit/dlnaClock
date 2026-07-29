package com.dlnaclock.screensaver.wallpaper;

import android.os.Bundle;

/**
 * ParamDef - 壁纸参数定义
 * 描述一个可调节参数的类型、范围和默认值，UI 层据此生成控件
 */
public class ParamDef {

    public enum Type { FLOAT, INT, COLOR, BOOL, SELECT }

    public final String key;
    public final String label;
    public final Type type;
    public final float min;
    public final float max;
    public final float defaultValue;
    public final float step;
    public final String[] options;

    /** FLOAT 参数 */
    public ParamDef(String key, String label, float min, float max, float defaultValue, float step) {
        this.key = key;
        this.label = label;
        this.type = Type.FLOAT;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.step = step;
        this.options = null;
    }

    /** INT 参数 */
    public ParamDef(String key, String label, int min, int max, int defaultValue) {
        this.key = key;
        this.label = label;
        this.type = Type.INT;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.step = 1;
        this.options = null;
    }

    /** COLOR 参数 */
    public ParamDef(String key, String label, int defaultColor) {
        this.key = key;
        this.label = label;
        this.type = Type.COLOR;
        this.min = 0;
        this.max = 0xFFFFFF;
        this.defaultValue = defaultColor;
        this.step = 1;
        this.options = null;
    }

    /** BOOL 参数 (defaultValue: 0=false, 1=true) */
    public ParamDef(String key, String label, boolean defaultVal) {
        this.key = key;
        this.label = label;
        this.type = Type.BOOL;
        this.min = 0;
        this.max = 1;
        this.defaultValue = defaultVal ? 1 : 0;
        this.step = 1;
        this.options = null;
    }

    /** SELECT 参数 (optionIndex 从 0 开始) */
    public ParamDef(String key, String label, String[] options, int defaultIndex) {
        this.key = key;
        this.label = label;
        this.type = Type.SELECT;
        this.min = 0;
        this.max = options.length - 1;
        this.defaultValue = defaultIndex;
        this.step = 1;
        this.options = options;
    }

    /** 从 Bundle 中获取此参数的值，若不存在返回默认值 */
    public float getValue(Bundle bundle) {
        return bundle.getFloat(key, defaultValue);
    }

    /** 将值写入 Bundle */
    public void putValue(Bundle bundle, float value) {
        bundle.putFloat(key, value);
    }
}
