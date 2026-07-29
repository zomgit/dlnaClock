package com.dlnaclock.dlna;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dlnaclock.util.PreferenceHelper;
import com.dlnaclock.util.ServiceCompat;

/**
 * BootReceiver - 开机自启动接收器
 * 设备重启后检查 DLNA 是否启用，如果启用则自动启动 DlnaService
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        Log.i(TAG, "Boot completed received");

        // 检查 DLNA 是否启用
        if (!PreferenceHelper.isDlnaEnabled()) {
            Log.i(TAG, "DLNA is disabled, not starting service");
            return;
        }

        Log.i(TAG, "DLNA enabled, starting DlnaService");
        Intent serviceIntent = new Intent(context, DlnaService.class);
        try {
            ServiceCompat.startForegroundService(context, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start DlnaService after boot", e);
        }
    }
}
