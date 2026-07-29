package com.dlnaclock.dlna;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.dlnaclock.App;
import com.dlnaclock.util.NotificationChannelHelper;
import com.dlnaclock.util.PreferenceHelper;

/**
 * DlnaService - DLNA 前台服务
 * 以 Foreground Service 形式运行，保证 DLNA 服务在后台持续可用
 * Android 8.0+ 使用 startForegroundService，低版本使用 startService
 * 包含：网络变化监听（WiFi 切换自动重启）、PARTIAL_WAKE_LOCK
 */
public class DlnaService extends Service {

    private static final String TAG = "DlnaService";
    private static final String CHANNEL_ID = "dlna_service_channel"; // 通知渠道 ID
    private static final int NOTIFICATION_ID = 1;                    // 通知 ID
    private static final long NETWORK_DEBOUNCE_MS = 2000;            // 网络变化防抖延迟
    private static final String ACTION_STOP_DLNA = "com.dlnaclock.ACTION_STOP_DLNA"; // 停止 DLNA 广播动作

    private BroadcastReceiver networkReceiver;                       // 网络变化广播接收器
    private Handler debounceHandler = new Handler(Looper.getMainLooper()); // 防抖 Handler
    private Runnable pendingRestart;                                 // 待执行的重启任务
    private PowerManager.WakeLock partialWakeLock;                   // CPU 唤醒锁

    /** onCreate - 服务创建时调用 */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "DLNA Service created");
        acquirePartialWakeLock();
        registerNetworkReceiver();
        registerStopReceiver();
    }

    /**
     * onStartCommand - 服务启动时调用
     * 创建通知渠道（Android 8+）、启动前台服务、启动 DLNA 管理器
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "DLNA Service started");

        // 仅在 DLNA 启用时才创建通知并启动服务
        if (!PreferenceHelper.isDlnaEnabled()) {
            Log.i(TAG, "DLNA is disabled, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Create notification channel for Android 8+ (isolated in helper class to avoid Dalvik verify errors)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelHelper.createChannel(this, CHANNEL_ID, "DLNA Service", "DLNA投屏服务运行中");
        }

        // Start as foreground service
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("DLNA Clock ScreenSaver")
                .setContentText("DLNA投屏服务运行中")
                .setSmallIcon(android.R.drawable.ic_media_play);

        // 添加停止按钮（通过 PendingIntent 发送广播）
        Intent stopIntent = new Intent(ACTION_STOP_DLNA);
        int stopFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            stopFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, stopFlags);
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent);

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);

        // Start DLNA manager
        App.getInstance().getDlnaManager().start();

        return START_STICKY;
    }

    /** onDestroy - 服务销毁时调用 */
    @Override
    public void onDestroy() {
        Log.i(TAG, "DLNA Service destroyed");
        unregisterNetworkReceiver();
        unregisterStopReceiver();
        releasePartialWakeLock();
        super.onDestroy();
    }

    /** onBind - 绑定服务（本项目不使用） */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ========== 网络变化监听（Fix #1） ==========

    /**
     * registerNetworkReceiver - 动态注册网络变化广播接收器
     * 不使用 manifest 静态注册（API 24+ 不支持 CONNECTIVITY_CHANGE 静态注册）
     */
    private void registerNetworkReceiver() {
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) return;

                Log.d(TAG, "Network connectivity changed");

                // 检查是否有可用网络连接
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm == null) return;

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnected();

                Log.d(TAG, "Network connected: " + isConnected);

                if (isConnected && PreferenceHelper.isDlnaEnabled()) {
                    // 防抖：取消之前的重启任务，延迟 2 秒后执行
                    if (pendingRestart != null) {
                        debounceHandler.removeCallbacks(pendingRestart);
                    }
                    pendingRestart = new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Network recovered, restarting DLNA services");
                            App.getInstance().getDlnaManager().restart();
                            pendingRestart = null;
                        }
                    };
                    debounceHandler.postDelayed(pendingRestart, NETWORK_DEBOUNCE_MS);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
        Log.i(TAG, "Network change receiver registered");
    }

    /** unregisterNetworkReceiver - 注销网络变化广播接收器 */
    private void unregisterNetworkReceiver() {
        if (pendingRestart != null) {
            debounceHandler.removeCallbacks(pendingRestart);
            pendingRestart = null;
        }
        if (networkReceiver != null) {
            try {
                unregisterReceiver(networkReceiver);
                Log.i(TAG, "Network change receiver unregistered");
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering network receiver", e);
            }
            networkReceiver = null;
        }
    }

    // ========== PARTIAL_WAKE_LOCK（Fix #3） ==========

    /** acquirePartialWakeLock - 获取 CPU 唤醒锁，防止 Doze 模式下 CPU 休眠 */
    private void acquirePartialWakeLock() {
        releasePartialWakeLock();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DlnaClock:DlnaService");
            partialWakeLock.acquire();
            Log.i(TAG, "Partial WakeLock acquired");
        }
    }

    /** releasePartialWakeLock - 释放 CPU 唤醒锁 */
    private void releasePartialWakeLock() {
        if (partialWakeLock != null && partialWakeLock.isHeld()) {
            partialWakeLock.release();
            partialWakeLock = null;
            Log.i(TAG, "Partial WakeLock released");
        }
    }

    // ========== 通知栏停止按钮 ==========

    private BroadcastReceiver stopReceiver; // 停止 DLNA 服务的广播接收器

    /** registerStopReceiver - 注册通知栏停止按钮的广播接收器 */
    private void registerStopReceiver() {
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_STOP_DLNA.equals(intent.getAction())) {
                    Log.i(TAG, "Stop DLNA requested from notification");
                    // 关闭 DLNA 设置中的启用状态
                    PreferenceHelper.setDlnaEnabled(false);
                    // 停止服务
                    stopSelf();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_STOP_DLNA);
        registerReceiver(stopReceiver, filter);
    }

    /** unregisterStopReceiver - 注销停止广播接收器 */
    private void unregisterStopReceiver() {
        if (stopReceiver != null) {
            try {
                unregisterReceiver(stopReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering stop receiver", e);
            }
            stopReceiver = null;
        }
    }
}
