package com.dlnaclock.dlna;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.dlnaclock.App;

public class DlnaService extends Service {

    private static final String TAG = "DlnaService";
    private static final String CHANNEL_ID = "dlna_service_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "DLNA Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "DLNA Service started");

        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DLNA Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("DLNA投屏服务运行中");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
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

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);

        // Start DLNA manager
        App.getInstance().getDlnaManager().start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "DLNA Service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
