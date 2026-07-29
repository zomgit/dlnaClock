package com.dlnaclock.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

/**
 * FullScreenHelper - 全屏显示工具类
 * 处理刘海屏、挖孔屏、导航栏等区域的全屏延伸显示
 * 确保内容覆盖整个屏幕，包括状态栏、导航栏和刘海区域
 */
public class FullScreenHelper {

    /**
     * setupFullScreen - 设置 Activity 为真正全屏显示
     * 1. 隐藏状态栏和导航栏
     * 2. 让内容延伸到系统栏后面
     * 3. API 28+ 处理刘海/挖孔区域
     * 4. 保持屏幕常亮
     *
     * @param activity 目标 Activity
     */
    public static void setupFullScreen(Activity activity) {
        if (activity == null) return;

        // 保持屏幕常亮
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19+: 使用沉浸式模式，让内容延伸到系统栏后面
            View decorView = activity.getWindow().getDecorView();

            // LAYOUT 标志：让内容布局延伸到系统栏（状态栏/导航栏）后面
            // SYSTEM_UI 标志：隐藏系统栏并启用沉浸式模式
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      // 内容延伸到状态栏后面
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION  // 内容延伸到导航栏后面
                    | View.SYSTEM_UI_FLAG_FULLSCREEN              // 隐藏状态栏
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION         // 隐藏导航栏
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY        // 沉浸式模式（滑动时临时显示）
            );
        }

        // API 28+ (Android 9+): 处理刘海屏/挖孔屏
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                WindowManager.LayoutParams layoutParams = activity.getWindow().getAttributes();
                // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1
                // 允许内容延伸到短边（顶部/底部）的刘海区域
                layoutParams.layoutInDisplayCutoutMode = 1; // SHORT_EDGES
                activity.getWindow().setAttributes(layoutParams);
            } catch (Exception e) {
                // 某些设备可能不支持此属性，忽略异常
            }
        }
    }

    /**
     * onResumeFullScreen - 在 onResume 中重新设置全屏
     * 沉浸式模式在 Activity 切换时可能丢失，需要重新设置
     *
     * @param activity 目标 Activity
     */
    public static void onResumeFullScreen(Activity activity) {
        setupFullScreen(activity);
    }
}
