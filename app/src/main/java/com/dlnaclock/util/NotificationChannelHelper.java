package com.dlnaclock.util;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * NotificationChannelHelper - 通知渠道兼容辅助类
 * 仅在 API 26+ 设备上被加载，隔离 NotificationChannel 类引用
 * 避免 Dalvik VM 加载 DlnaService 时因 NotificationChannel 类不存在而报 VerifyError
 */
@TargetApi(26)
public class NotificationChannelHelper {

    /**
     * createChannel - 创建通知渠道（仅 API 26+ 生效）
     * @param context 上下文
     * @param channelId 渠道 ID
     * @param name 渠道名称
     * @param description 渠道描述
     */
    public static void createChannel(Context context, String channelId, String name, String description) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
