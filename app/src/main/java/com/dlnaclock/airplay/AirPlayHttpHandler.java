package com.dlnaclock.airplay;

import android.util.Log;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * AirPlayHttpHandler - AirPlay HTTP 请求处理器
 * 处理所有 AirPlay 端点的请求，将播放控制委托给 AirPlayManager
 * 生成 plist XML 响应（server-info、playback-info）
 */
public class AirPlayHttpHandler {

    private static final String TAG = "AirPlayHttpHandler";

    private static final String MIME_PLIST = "text/x-apple-plist+xml";
    private static final String PLIST_HEADER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
            "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
            "<plist version=\"1.0\">\n<dict>\n";
    private static final String PLIST_FOOTER = "</dict>\n</plist>\n";

    private AirPlayManager airPlayManager; // AirPlay 总协调器引用

    /** AirPlayHttpHandler - 构造函数 */
    public AirPlayHttpHandler(AirPlayManager manager) {
        this.airPlayManager = manager;
    }

    /**
     * handleServerInfo - 处理 GET /server-info 请求
     * 返回设备信息 plist（deviceid、features、model、name、protovers、srcvers 等）
     */
    public NanoHTTPD.Response handleServerInfo(NanoHTTPD.IHTTPSession session) {
        Log.d(TAG, "handleServerInfo");

        String deviceId = airPlayManager.getDeviceId();
        String deviceName = airPlayManager.getDeviceName();

        StringBuilder plist = new StringBuilder(PLIST_HEADER);
        appendPlistString(plist, "deviceid", deviceId);
        appendPlistString(plist, "features", AirPlayConstants.FEATURES);
        appendPlistString(plist, "model", AirPlayConstants.MODEL);
        appendPlistString(plist, "name", deviceName);
        appendPlistString(plist, "protovers", AirPlayConstants.PROTOVERS);
        appendPlistString(plist, "srcvers", AirPlayConstants.SRCVERS);
        appendPlistString(plist, "vv", "2");
        appendPlistString(plist, "pk", "0000000000000000000000000000000000000000000000000000000000000000");
        plist.append(PLIST_FOOTER);

        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_PLIST, plist.toString());
    }

    /**
     * handlePlay - 处理 POST /play 请求
     * 解析请求体中的 Content-Location URL 和 Start-Position，调用 AirPlayManager.onPlay()
     */
    public NanoHTTPD.Response handlePlay(NanoHTTPD.IHTTPSession session, String body) {
        Log.d(TAG, "handlePlay");

        if (body == null || body.isEmpty()) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Missing body");
        }

        AirPlayRequestParser.PlayRequest request = AirPlayRequestParser.parsePlayRequest(body);
        if (request == null || request.contentLocation == null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Missing Content-Location");
        }

        airPlayManager.onPlay(request.contentLocation, request.startPosition);
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
    }

    /**
     * handleStop - 处理 POST /stop 请求
     * 停止当前播放
     */
    public NanoHTTPD.Response handleStop(NanoHTTPD.IHTTPSession session) {
        Log.d(TAG, "handleStop");
        airPlayManager.onStop();
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
    }

    /**
     * handleScrub - 处理 GET /scrub?position=xx 请求
     * 跳转到指定秒数的播放位置
     */
    public NanoHTTPD.Response handleScrub(NanoHTTPD.IHTTPSession session, String uri) {
        Log.d(TAG, "handleScrub: " + uri);

        Map<String, String> params = AirPlayRequestParser.parseQueryString(uri);
        String positionStr = params.get("position");
        if (positionStr == null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Missing position");
        }

        try {
            float positionSeconds = Float.parseFloat(positionStr);
            airPlayManager.onScrub(positionSeconds);
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
        } catch (NumberFormatException e) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Invalid position");
        }
    }

    /**
     * handleRate - 处理 GET /rate?value=xx 请求
     * value=0 表示暂停，value=1 表示播放
     */
    public NanoHTTPD.Response handleRate(NanoHTTPD.IHTTPSession session, String uri) {
        Log.d(TAG, "handleRate: " + uri);

        Map<String, String> params = AirPlayRequestParser.parseQueryString(uri);
        String valueStr = params.get("value");
        if (valueStr == null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Missing value");
        }

        try {
            float value = Float.parseFloat(valueStr);
            airPlayManager.onRate(value);
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
        } catch (NumberFormatException e) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Invalid value");
        }
    }

    /**
     * handlePlaybackInfo - 处理 GET /playback-info 请求
     * 返回当前播放状态 plist（duration、position、playbackState、rate 等）
     */
    public NanoHTTPD.Response handlePlaybackInfo(NanoHTTPD.IHTTPSession session) {
        Log.d(TAG, "handlePlaybackInfo");

        String playbackState = airPlayManager.getPlaybackState();
        double duration = airPlayManager.getDurationSeconds();
        double position = airPlayManager.getPositionSeconds();
        float rate = airPlayManager.getRate();

        StringBuilder plist = new StringBuilder(PLIST_HEADER);
        appendPlistReal(plist, "duration", duration);
        appendPlistReal(plist, "position", position);
        appendPlistString(plist, "playbackState", playbackState);
        appendPlistReal(plist, "rate", rate);
        appendPlistReal(plist, "readyToPlay", 1.0);
        appendPlistReal(plist, "loadedTimeRanges", 1.0);
        appendPlistReal(plist, "seekableTimeRanges", 1.0);
        plist.append(PLIST_FOOTER);

        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_PLIST, plist.toString());
    }

    /**
     * handlePhoto - 处理 POST /photo 请求
     * 图片投屏功能（暂未实现，返回 200 OK）
     */
    public NanoHTTPD.Response handlePhoto(NanoHTTPD.IHTTPSession session, String body) {
        Log.d(TAG, "handlePhoto - not yet implemented");
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
    }

    /**
     * handleReverse - 处理 POST /reverse 请求
     * 反向连接（暂未实现，返回 200 OK）
     */
    public NanoHTTPD.Response handleReverse(NanoHTTPD.IHTTPSession session) {
        Log.d(TAG, "handleReverse - not yet implemented");
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
    }

    // === plist 构造辅助方法 ===

    /** appendPlistString - 向 plist 追加 string 类型键值对 */
    private void appendPlistString(StringBuilder sb, String key, String value) {
        sb.append("  <key>").append(escapeXml(key)).append("</key>\n");
        sb.append("  <string>").append(escapeXml(value)).append("</string>\n");
    }

    /** appendPlistReal - 向 plist 追加 real 类型键值对 */
    private void appendPlistReal(StringBuilder sb, String key, double value) {
        sb.append("  <key>").append(escapeXml(key)).append("</key>\n");
        sb.append("  <real>").append(value).append("</real>\n");
    }

    /** escapeXml - XML 特殊字符转义 */
    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** newFixedLengthResponse - 创建固定长度响应（便捷方法） */
    private NanoHTTPD.Response newFixedLengthResponse(NanoHTTPD.Response.Status status, String mimeType, String data) {
        return NanoHTTPD.newFixedLengthResponse(status, mimeType, data);
    }
}
