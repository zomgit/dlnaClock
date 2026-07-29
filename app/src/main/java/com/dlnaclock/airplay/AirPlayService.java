package com.dlnaclock.airplay;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.dlnaclock.util.NotificationChannelHelper;
import com.dlnaclock.util.PreferenceHelper;

/**
 * AirPlayService - AirPlay 前台服务
 * 以 Foreground Service 形式运行，保证 AirPlay 服务在后台持续可用
 * Android 8.0+ 使用 startForegroundService，低版本使用 startService
 */
public class AirPlayService extends Service {

    private static final String TAG = "AirPlayService";
    private static final String CHANNEL_ID = "airplay_service_channel"; // 通知渠道 ID
    private static final int NOTIFICATION_ID = 2;                       // 通知 ID（避免与 DLNA 的 1 冲突）

    /** onCreate - 服务创建时调用 */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "AirPlay Service created");
    }

    /**
     * onStartCommand - 服务启动时调用
     * 创建通知渠道（Android 8+）、启动前台服务、启动 AirPlay 管理器
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "AirPlay Service started");

        // 仅在 AirPlay 启用时才创建通知并启动服务
        if (!PreferenceHelper.isAirPlayEnabled()) {
            Log.i(TAG, "AirPlay is disabled, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Create notification channel for Android 8+ (isolated in helper class to avoid Dalvik verify errors)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelHelper.createChannel(this, CHANNEL_ID, "AirPlay Service", "AirPlay投屏服务运行中");
        }

        // Start as foreground service
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("AirPlay Service")
                .setContentText("AirPlay投屏服务运行中")
                .setSmallIcon(android.R.drawable.ic_media_play);

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);

        // Start AirPlay manager（已在 App.onCreate 中 init 过）
        AirPlayManager.getInstance().start();

        return START_STICKY;
    }

    /** onDestroy - 服务销毁时调用 */
    @Override
    public void onDestroy() {
        Log.i(TAG, "AirPlay Service destroyed");
        AirPlayManager.getInstance().shutdown();
        super.onDestroy();
    }

    /** onBind - 绑定服务（本项目不使用） */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
