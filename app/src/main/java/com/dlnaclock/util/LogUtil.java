package com.dlnaclock.util;

import android.util.Log;

/**
 * LogUtil - 日志包装类
 * VERBOSE 和 DEBUG 级别日志受应用内调试开关控制
 * INFO/WARN/ERROR 始终输出，不受开关影响
 * 
 * 使用方式：
 * - 将 DLNA 等模块中的 Log.d/Log.v 替换为 LogUtil.d/LogUtil.v
 * - Log.i/Log.w/Log.e 保持不变（始终输出）
 * - 在设置界面中开关"调试日志"即可实时控制 VERBOSE/DEBUG 输出
 */
public class LogUtil {

    /**
     * VERBOSE 日志 - 受调试开关控制
     */
    public static int v(String tag, String msg) {
        if (PreferenceHelper.isDebugLogEnabled()) {
            return Log.v(tag, msg);
        }
        return 0;
    }

    public static int v(String tag, String msg, Throwable tr) {
        if (PreferenceHelper.isDebugLogEnabled()) {
            return Log.v(tag, msg, tr);
        }
        return 0;
    }

    /**
     * DEBUG 日志 - 受调试开关控制
     */
    public static int d(String tag, String msg) {
        if (PreferenceHelper.isDebugLogEnabled()) {
            return Log.d(tag, msg);
        }
        return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
        if (PreferenceHelper.isDebugLogEnabled()) {
            return Log.d(tag, msg, tr);
        }
        return 0;
    }

    /**
     * INFO 日志 - 始终输出
     */
    public static int i(String tag, String msg) {
        return Log.i(tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return Log.i(tag, msg, tr);
    }

    /**
     * WARN 日志 - 始终输出
     */
    public static int w(String tag, String msg) {
        return Log.w(tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return Log.w(tag, msg, tr);
    }

    /**
     * ERROR 日志 - 始终输出
     */
    public static int e(String tag, String msg) {
        return Log.e(tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return Log.e(tag, msg, tr);
    }
}
