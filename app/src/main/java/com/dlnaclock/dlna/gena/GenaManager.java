package com.dlnaclock.dlna.gena;

import android.os.Handler;
import android.os.Looper;
import com.dlnaclock.util.LogUtil;

import com.dlnaclock.dlna.avt.AVTransportService;
import com.dlnaclock.dlna.avt.MediaInfo;
import com.dlnaclock.dlna.avt.TransportState;
import com.dlnaclock.dlna.rc.RenderingControlService;
import com.dlnaclock.dlna.soap.SoapConstants;
import com.dlnaclock.util.XmlUtil;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GenaManager - GENA 事件订阅与通知管理器
 * 参考 DLNA-Cast 的 LastChange 事件推送设计
 * 管理 SUBSCRIBE/UNSUBSCRIBE，状态变化时发送 HTTP NOTIFY 到订阅者的回调 URL
 */
public class GenaManager {

    private static final String TAG = "GenaManager";

    private Map<String, Subscription> subscriptions = new ConcurrentHashMap<>(); // SID → Subscription
    private ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(); // 异步发送 NOTIFY
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private AVTransportService avtService;
    private RenderingControlService rcService;

    /** Subscription - GENA 订阅信息 */
    private static class Subscription {
        String sid;           // 订阅 ID
        String callbackUrl;   // 回调 URL（控制点接收 NOTIFY 的地址）
        String serviceType;   // 服务类型（AVTransport/RenderingControl/ConnectionManager）
        long expiresAt;       // 过期时间戳（毫秒）
        int seq = 0;          // NOTIFY 序列号（每次发送递增）

        Subscription(String sid, String callbackUrl, String serviceType, int timeoutSeconds) {
            this.sid = sid;
            this.callbackUrl = callbackUrl;
            this.serviceType = serviceType;
            this.expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /** GenaManager - 构造函数 */
    public GenaManager(AVTransportService avtService, RenderingControlService rcService) {
        this.avtService = avtService;
        this.rcService = rcService;
    }

    /**
     * addSubscription - 添加 GENA 订阅
     * @param callbackUrl 控制点回调 URL
     * @param serviceType 服务类型路径（如 /event/AVTransport）
     * @param timeoutSeconds 超时秒数
     * @return 分配的 SID
     */
    public String addSubscription(String callbackUrl, String serviceType, int timeoutSeconds) {
        // Parse callback URL - format: <http://ip:port/path>
        String url = callbackUrl;
        if (url.startsWith("<") && url.endsWith(">")) {
            url = url.substring(1, url.length() - 1);
        }

        String sid = "uuid:" + UUID.randomUUID().toString();
        Subscription sub = new Subscription(sid, url, serviceType, timeoutSeconds);
        subscriptions.put(sid, sub);

        LogUtil.i(TAG, "New subscription: SID=" + sid + ", callback=" + url + ", service=" + serviceType
                + ", active=" + getActiveSubscriptionCount());

        // Clean expired subscriptions
        cleanExpired();

        // Send initial event with current state
        sendInitialEvent(sub);

        return sid;
    }

    /**
     * renewSubscription - 续订
     * @param sid 订阅 ID
     * @param timeoutSeconds 新的超时秒数
     * @return true 如果续订成功
     */
    public boolean renewSubscription(String sid, int timeoutSeconds) {
        Subscription sub = subscriptions.get(sid);
        if (sub != null && !sub.isExpired()) {
            sub.expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
            LogUtil.d(TAG, "Subscription renewed: SID=" + sid);
            return true;
        }
        return false;
    }

    /**
     * removeSubscription - 取消订阅
     * @param sid 订阅 ID
     */
    public void removeSubscription(String sid) {
        subscriptions.remove(sid);
        LogUtil.d(TAG, "Subscription removed: SID=" + sid);
    }

    /**
     * getSubscriptionTimeout - 获取订阅超时时间字符串
     * @param sid 订阅 ID
     * @return 超时字符串（如 "Second-1800"）
     */
    public String getSubscriptionTimeout(String sid) {
        Subscription sub = subscriptions.get(sid);
        if (sub != null) {
            long remainingMs = sub.expiresAt - System.currentTimeMillis();
            int remainingSec = (int) (remainingMs / 1000);
            if (remainingSec > 0) {
                return "Second-" + remainingSec;
            }
        }
        return "Second-1800";
    }

    /**
     * notifyTransportStateChanged - AVTransport 状态变化时通知所有相关订阅者
     */
    public void notifyTransportStateChanged() {
        cleanExpired();
        for (Subscription sub : subscriptions.values()) {
            if (sub.serviceType.contains("AVTransport")) {
                sendNotify(sub, buildAVTransportLastChange());
            }
        }
    }

    /**
     * notifyRenderingControlChanged - RenderingControl 状态变化时通知所有相关订阅者
     */
    public void notifyRenderingControlChanged() {
        cleanExpired();
        for (Subscription sub : subscriptions.values()) {
            if (sub.serviceType.contains("RenderingControl")) {
                sendNotify(sub, buildRenderingControlLastChange());
            }
        }
    }

    /**
     * logSubscriptionStatus - 输出所有活跃订阅的诊断信息
     * 可通过 adb logcat -s GenaManager 查看
     */
    public void logSubscriptionStatus() {
        LogUtil.i(TAG, "=== GENA Subscription Status ===");
        LogUtil.i(TAG, "Total subscriptions: " + subscriptions.size());
        for (Subscription sub : subscriptions.values()) {
            long remainingMs = sub.expiresAt - System.currentTimeMillis();
            int remainingSec = (int) (remainingMs / 1000);
            LogUtil.i(TAG, "  SID: " + sub.sid
                    + " | service: " + sub.serviceType
                    + " | callback: " + sub.callbackUrl
                    + " | expires: " + remainingSec + "s"
                    + " | seq: " + sub.seq
                    + (sub.isExpired() ? " [EXPIRED]" : " [ACTIVE]"));
        }
        LogUtil.i(TAG, "================================");
    }

    /**
     * getActiveSubscriptionCount - 获取活跃订阅数量（不含已过期）
     */
    public int getActiveSubscriptionCount() {
        int count = 0;
        for (Subscription sub : subscriptions.values()) {
            if (!sub.isExpired()) count++;
        }
        return count;
    }

    /** buildAVTransportLastChange - 构建 AVTransport LastChange XML */
    private String buildAVTransportLastChange() {
        TransportState state = avtService.getCurrentState();
        MediaInfo mediaInfo = avtService.getMediaInfoObject();
        long posMs = avtService.getCurrentPositionMs();
        long durMs = avtService.getDurationMs();

        String relTime = formatTime(posMs);
        String duration = formatTime(durMs);
        String uri = mediaInfo != null ? mediaInfo.getCurrentUri() : "";
        String trackDuration = mediaInfo != null ? mediaInfo.getDuration() : "00:00:00";

        // 根据状态动态设置 CurrentTransportActions
        String transportActions;
        switch (state) {
            case STOPPED:
                transportActions = "Play";
                break;
            case PLAYING:
                transportActions = "Stop,Pause,Seek";
                break;
            case PAUSED_PLAYBACK:
                transportActions = "Play,Stop,Seek";
                break;
            case TRANSITIONING:
                transportActions = "Stop";
                break;
            case NO_MEDIA_PRESENT:
            default:
                transportActions = "";
                break;
        }

        // 播放结束时 RelativeTimeCounter 归零
        String counterTime;
        if (state == TransportState.STOPPED || state == TransportState.NO_MEDIA_PRESENT) {
            counterTime = "00:00:00";
        } else {
            counterTime = relTime;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/AVT/\">");
        sb.append("<InstanceID val=\"0\">");
        sb.append("<TransportState val=\"").append(state.getValue()).append("\"/>");
        sb.append("<TransportStatus val=\"OK\"/>");
        sb.append("<PlaybackStorageMedium val=\"NETWORK\"/>");
        sb.append("<RecordStorageMedium val=\"NOT_IMPLEMENTED\"/>");
        sb.append("<PossiblePlaybackStorageMedia val=\"NETWORK\"/>");
        sb.append("<PossibleRecordStorageMedia val=\"NOT_IMPLEMENTED\"/>");
        sb.append("<CurrentPlayMode val=\"NORMAL\"/>");
        sb.append("<TransportPlaySpeed val=\"1\"/>");
        sb.append("<NumberOfTracks val=\"1\"/>");
        sb.append("<CurrentTrack val=\"1\"/>");
        sb.append("<CurrentTrackDuration val=\"").append(trackDuration).append("\"/>");
        sb.append("<CurrentMediaDuration val=\"").append(trackDuration).append("\"/>");
        sb.append("<CurrentTrackURI val=\"").append(XmlUtil.escapeXml(uri)).append("\"/>");
        sb.append("<AVTransportURI val=\"").append(XmlUtil.escapeXml(uri)).append("\"/>");
        sb.append("<NextAVTransportURI val=\"\"/>");
        sb.append("<CurrentTransportActions val=\"").append(transportActions).append("\"/>");
        sb.append("<RelativeTimeCounter val=\"").append(counterTime).append("\"/>");
        sb.append("</InstanceID>");
        sb.append("</Event>");
        return sb.toString();
    }

    /** buildRenderingControlLastChange - 构建 RenderingControl LastChange XML */
    private String buildRenderingControlLastChange() {
        int volume = rcService.getVolume();
        boolean mute = rcService.getMute();

        StringBuilder sb = new StringBuilder();
        sb.append("<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/RCS/\">");
        sb.append("<InstanceID val=\"0\">");
        sb.append("<Volume channel=\"Master\" val=\"").append(volume).append("\"/>");
        sb.append("<Mute channel=\"Master\" val=\"").append(mute ? "1" : "0").append("\"/>");
        sb.append("</InstanceID>");
        sb.append("</Event>");
        return sb.toString();
    }

    /**
     * sendConnectionManagerNotify - 发送 ConnectionManager 事件通知
     * ConnectionManager 事件不使用 LastChange，直接将状态变量作为独立 property 发送
     */
    private void sendConnectionManagerNotify(final Subscription sub) {
        notifyExecutor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(sub.callbackUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("NOTIFY");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    conn.setRequestProperty("CONTENT-TYPE", "text/xml; charset=\"utf-8\"");
                    conn.setRequestProperty("NT", "upnp:event");
                    conn.setRequestProperty("NTS", "upnp:propchange");
                    conn.setRequestProperty("SID", sub.sid);
                    conn.setRequestProperty("SEQ", String.valueOf(sub.seq++));
                    conn.setRequestProperty("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");

                    // ConnectionManager: 每个状态变量作为独立的 e:property
                    String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">" +
                            "<e:property><SourceProtocolInfo>" +
                            SoapConstants.PROTOCOL_INFO_SOURCE +
                            "</SourceProtocolInfo></e:property>" +
                            "<e:property><SinkProtocolInfo>" +
                            XmlUtil.escapeXml(SoapConstants.PROTOCOL_INFO_SINK) +
                            "</SinkProtocolInfo></e:property>" +
                            "<e:property><CurrentConnectionIDs>0</CurrentConnectionIDs></e:property>" +
                            "</e:propertyset>";

                    byte[] data = body.getBytes("UTF-8");
                    conn.setRequestProperty("CONTENT-LENGTH", String.valueOf(data.length));

                    OutputStream os = conn.getOutputStream();
                    os.write(data);
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    LogUtil.d(TAG, "GENA NOTIFY (CM) sent -> " + responseCode);

                } catch (Exception e) {
                    LogUtil.w(TAG, "Failed to send GENA NOTIFY (CM) to " + sub.callbackUrl +
                            ": " + e.getMessage());
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    /** sendInitialEvent - 订阅时发送初始事件通知 */
    private void sendInitialEvent(Subscription sub) {
        String eventBody;
        if (sub.serviceType.contains("AVTransport")) {
            eventBody = buildAVTransportLastChange();
        } else if (sub.serviceType.contains("RenderingControl")) {
            eventBody = buildRenderingControlLastChange();
        } else if (sub.serviceType.contains("ConnectionManager")) {
            // ConnectionManager 事件不使用 LastChange，需要专用发送方法
            sendConnectionManagerNotify(sub);
            return;
        } else {
            return;
        }
        sendNotify(sub, eventBody);
    }

    /**
     * sendNotify - 异步发送 HTTP NOTIFY 到订阅者的回调 URL
     * @param sub 订阅信息
     * @param lastChangeXml LastChange XML 内容
     */
    private void sendNotify(final Subscription sub, final String lastChangeXml) {
        notifyExecutor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(sub.callbackUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("NOTIFY");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    // Set required headers (UPnP GENA spec)
                    conn.setRequestProperty("CONTENT-TYPE", "text/xml; charset=\"utf-8\"");
                    conn.setRequestProperty("NT", "upnp:event");
                    conn.setRequestProperty("NTS", "upnp:propchange");
                    conn.setRequestProperty("SID", sub.sid);
                    conn.setRequestProperty("SEQ", String.valueOf(sub.seq++));
                    conn.setRequestProperty("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");

                    // Build event body
                    String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">" +
                            "<e:property>" +
                            "<LastChange>" +
                            XmlUtil.escapeXml(lastChangeXml) +
                            "</LastChange>" +
                            "</e:property>" +
                            "</e:propertyset>";

                    byte[] data = body.getBytes("UTF-8");
                    conn.setRequestProperty("CONTENT-LENGTH", String.valueOf(data.length));

                    // 诊断日志：打印完整的 GENA NOTIFY 请求（HTTP Header + XML Body）
                    LogUtil.i(TAG, "========== GENA NOTIFY REQUEST ==========");
                    LogUtil.i(TAG, "NOTIFY " + sub.callbackUrl + " HTTP/1.1");
                    LogUtil.i(TAG, "HOST: " + url.getHost() + ":" + (url.getPort() > 0 ? url.getPort() : 80));
                    LogUtil.i(TAG, "CONTENT-TYPE: text/xml; charset=\"utf-8\"");
                    LogUtil.i(TAG, "CONTENT-LENGTH: " + data.length);
                    LogUtil.i(TAG, "NT: upnp:event");
                    LogUtil.i(TAG, "NTS: upnp:propchange");
                    LogUtil.i(TAG, "SID: " + sub.sid);
                    LogUtil.i(TAG, "SEQ: " + (sub.seq - 1)); // seq 已在上面自增，这里打印发送时的值
                    LogUtil.i(TAG, "SERVER: Android/4.4 UPnP/1.1 DlnaClock/1.0");
                    LogUtil.i(TAG, "");
                    LogUtil.i(TAG, body);
                    LogUtil.i(TAG, "==========================================");

                    OutputStream os = conn.getOutputStream();
                    os.write(data);
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    LogUtil.i(TAG, "GENA NOTIFY response: " + responseCode + " from " + sub.callbackUrl);

                } catch (Exception e) {
                    LogUtil.w(TAG, "Failed to send GENA NOTIFY to " + sub.callbackUrl +
                            ": " + e.getMessage());
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    /** cleanExpired - 清理过期的订阅 */
    private void cleanExpired() {
        java.util.Iterator<Map.Entry<String, Subscription>> it = subscriptions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Subscription> entry = it.next();
            if (entry.getValue().isExpired()) {
                LogUtil.d(TAG, "Removing expired subscription: " + entry.getKey().substring(0, Math.min(13, entry.getKey().length())) + "...");
                it.remove();
            }
        }
    }

    /** formatTime - 将毫秒时间格式化为 HH:MM:SS */
    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** release - 释放资源 */
    public void release() {
        subscriptions.clear();
        notifyExecutor.shutdown();
    }
}
