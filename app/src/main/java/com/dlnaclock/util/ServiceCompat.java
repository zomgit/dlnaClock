package com.dlnaclock.util;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * ServiceCompat - 服务启动兼容工具
 * 通过反射调用 startForegroundService，避免 Dalvik 验证器在类加载时报错
 * 适用于 minSdk < 26 但需要兼容 API 26+ 前台服务启动的场景
 */
public class ServiceCompat {

    private static final String TAG = "ServiceCompat";

    /**
     * startForegroundService - 兼容方式启动前台服务
     * API 26+ 通过反射调用 Context.startForegroundService，低版本直接 startService
     * 避免 Dalvik VM 在类加载阶段扫描到 API 26 方法引用而报 VerifyError
     */
    public static void startForegroundService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Method m = Context.class.getMethod("startForegroundService", Intent.class);
                m.invoke(context, intent);
                return;
            } catch (Exception e) {
                Log.w(TAG, "startForegroundService via reflection failed, fallback to startService", e);
            }
        }
        context.startService(intent);
    }
}
