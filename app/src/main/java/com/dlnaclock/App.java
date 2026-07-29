package com.dlnaclock;

import android.app.Application;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.dlnaclock.dlna.DlnaManager;
import com.dlnaclock.util.PreferenceHelper;

public class App extends Application {

    private static final String TAG = "DlnaClockApp";
    private static App instance;
    private DlnaManager dlnaManager;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        PreferenceHelper.init(this);
        dlnaManager = DlnaManager.getInstance();
        dlnaManager.init(this);
        startDlnaService();
    }

    private void startDlnaService() {
        Intent intent = new Intent(this, com.dlnaclock.dlna.DlnaService.class);
        startService(intent);
    }

    public static App getInstance() {
        return instance;
    }

    public DlnaManager getDlnaManager() {
        return dlnaManager;
    }

    public void acquireWakeLock(String tag) {
        releaseWakeLock();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DlnaClock:" + tag);
        wakeLock.acquire();
    }

    public void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    public void onTerminate() {
        if (dlnaManager != null) {
            dlnaManager.shutdown();
        }
        releaseWakeLock();
        super.onTerminate();
    }
}
