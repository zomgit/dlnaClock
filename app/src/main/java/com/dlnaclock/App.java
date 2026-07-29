package com.dlnaclock;

import android.app.Application;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.dlnaclock.airplay.AirPlayManager;
import com.dlnaclock.dlna.DlnaManager;
import com.dlnaclock.screensaver.wallpaper.ScriptManager;
import com.dlnaclock.util.PreferenceHelper;
import com.dlnaclock.util.ServiceCompat;

/**
 * App - 应用程序入口类（Application 子类）
 * 负责全局初始化：偏好设置、DLNA 管理器、前台服务、WakeLock 管理
 */
public class App extends Application {

    private static final String TAG = "DlnaClockApp";
    private static App instance;           // 单例引用，供全局获取 Context
    private DlnaManager dlnaManager;       // DLNA 协议栈总管理器
    private AirPlayManager airPlayManager;  // AirPlay 协议栈总管理器
    private PowerManager.WakeLock wakeLock; // 播放时保持屏幕/CPU 唤醒

    /**
     * onCreate - 应用启动时调用
     * 初始化偏好设置、DLNA 管理器，并启动 DLNA 前台服务
     */
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        PreferenceHelper.init(this);
        ScriptManager.init(this);
        dlnaManager = DlnaManager.getInstance();
        dlnaManager.init(this);
        // DLNA 默认关闭，仅在用户手动开启后才启动服务
        if (PreferenceHelper.isDlnaEnabled()) {
            startDlnaService();
        }

        // AirPlay 初始化
        airPlayManager = AirPlayManager.getInstance();
        airPlayManager.init(this);
        if (PreferenceHelper.isAirPlayEnabled()) {
            startAirPlayService();
        }
    }

    /**
     * startDlnaService - 启动 DLNA 前台服务
     * 通过 ServiceCompat 反射调用，避免 Dalvik 类加载验证问题
     */
    private void startDlnaService() {
        Intent intent = new Intent(this, com.dlnaclock.dlna.DlnaService.class);
        try {
            ServiceCompat.startForegroundService(this, intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start DLNA service", e);
        }
    }

    /**
     * startAirPlayService - 启动 AirPlay 前台服务
     * 通过 ServiceCompat 反射调用，避免 Dalvik 类加载验证问题
     */
    private void startAirPlayService() {
        Intent intent = new Intent(this, com.dlnaclock.airplay.AirPlayService.class);
        try {
            ServiceCompat.startForegroundService(this, intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AirPlay service", e);
        }
    }

    /** getInstance - 获取 App 单例实例 */
    public static App getInstance() {
        return instance;
    }

    /** getDlnaManager - 获取 DLNA 管理器实例 */
    public DlnaManager getDlnaManager() {
        return dlnaManager;
    }

    /** getAirPlayManager - 获取 AirPlay 管理器实例 */
    public AirPlayManager getAirPlayManager() {
        return airPlayManager;
    }

    /**
     * acquireWakeLock - 获取 WakeLock，保持屏幕和 CPU 唤醒
     * @param tag 标签，用于调试标识
     */
    public void acquireWakeLock(String tag) {
        releaseWakeLock();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DlnaClock:" + tag);
        wakeLock.acquire();
    }

    /** releaseWakeLock - 释放 WakeLock */
    public void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    /**
     * onTerminate - 应用终止时调用
     * 关闭 DLNA 管理器并释放 WakeLock
     */
    @Override
    public void onTerminate() {
        if (dlnaManager != null) {
            dlnaManager.shutdown();
        }
        if (airPlayManager != null) {
            airPlayManager.shutdown();
        }
        releaseWakeLock();
        super.onTerminate();
    }
}
