package com.dlnaclock.airplay;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * AirPlayRequestParser - AirPlay 请求解析工具类
 * 解析 /play POST body（纯文本 key-value）、plist XML 值提取、URI query 参数解析
 */
public class AirPlayRequestParser {

    private static final String TAG = "AirPlayRequestParser";

    /**
     * PlayRequest - /play 请求的解析结果
     * 包含 Content-Location（媒体 URL）和 Start-Position（起始位置 0.0-1.0）
     */
    public static class PlayRequest {
        public String contentLocation;  // 媒体播放 URL
        public float startPosition;     // 起始位置（0.0=开头，0.5=中间）

        public PlayRequest() {
            this.startPosition = 0.0f;
        }
    }

    /**
     * parsePlayRequest - 解析 /play POST body
     * body 格式为纯文本 key-value，每行一个，例如：
     *   Content-Location: http://example.com/video.mp4
     *   Start-Position: 0.5
     * @param body POST 请求体
     * @return PlayRequest 对象，解析失败返回 null
     */
    public static PlayRequest parsePlayRequest(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        PlayRequest request = new PlayRequest();
        String[] lines = body.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) continue;

            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            if ("Content-Location".equalsIgnoreCase(key)) {
                request.contentLocation = value;
            } else if ("Start-Position".equalsIgnoreCase(key)) {
                try {
                    request.startPosition = Float.parseFloat(value);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid Start-Position: " + value);
                    request.startPosition = 0.0f;
                }
            }
        }

        return request;
    }

    /**
     * parsePlistValue - 从简单 plist XML 中提取指定 key 的 string 值
     * 仅处理简单的 key-string 对，不支持嵌套结构
     * @param plist plist XML 内容
     * @param key   要提取的 key 名称
     * @return 对应的 string 值，未找到返回 null
     */
    public static String parsePlistValue(String plist, String key) {
        if (plist == null || key == null) return null;

        String keyTag = "<key>" + key + "</key>";
        int keyIdx = plist.indexOf(keyTag);
        if (keyIdx < 0) return null;

        // 从 key 标签结束后查找 <string> 标签
        int searchStart = keyIdx + keyTag.length();
        int strStart = plist.indexOf("<string>", searchStart);
        if (strStart < 0) return null;

        strStart += "<string>".length();
        int strEnd = plist.indexOf("</string>", strStart);
        if (strEnd < 0) return null;

        return plist.substring(strStart, strEnd).trim();
    }

    /**
     * parseQueryString - 从 URI 解析 query 参数
     * 例如 "/scrub?position=123.5" 返回 {"position": "123.5"}
     * @param uri 完整的 URI（可能包含 query 部分）
     * @return query 参数 Map，无 query 返回空 Map
     */
    public static Map<String, String> parseQueryString(String uri) {
        Map<String, String> params = new HashMap<>();
        if (uri == null) return params;

        int queryIdx = uri.indexOf('?');
        if (queryIdx < 0 || queryIdx >= uri.length() - 1) {
            return params;
        }

        String query = uri.substring(queryIdx + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String key = pair.substring(0, eqIdx).trim();
                String value = pair.substring(eqIdx + 1).trim();
                try {
                    value = java.net.URLDecoder.decode(value, "UTF-8");
                } catch (Exception ignored) {}
                params.put(key, value);
            }
        }

        return params;
    }
}
