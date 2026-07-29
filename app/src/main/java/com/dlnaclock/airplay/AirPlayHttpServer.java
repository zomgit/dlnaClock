package com.dlnaclock.airplay;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

/**
 * AirPlayHttpServer - AirPlay HTTP 服务器
 * 继承 NanoHTTPD，监听端口 7000，将请求路由到 AirPlayHttpHandler
 * 处理 /server-info、/play、/stop、/scrub、/rate、/playback-info、/photo、/reverse 端点
 */
public class AirPlayHttpServer extends NanoHTTPD {

    private static final String TAG = "AirPlayHttpServer";

    private AirPlayHttpHandler requestHandler; // 请求路由处理器

    /**
     * AirPlayHttpServer - 构造函数
     * @param port    监听端口（AirPlayConstants.AIRPLAY_PORT）
     * @param handler 请求处理器引用
     */
    public AirPlayHttpServer(int port, AirPlayHttpHandler handler) {
        super(port);
        this.requestHandler = handler;
    }

    /**
     * serve - 处理所有 HTTP 请求
     * 直接从 InputStream 读取请求体（不调用 parseBody 避免流被提前消费）
     */
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, method + " " + uri);

        // 仅对 POST/PUT 请求读取 body（GET 等无 body，读取会导致 Socket 异常）
        String body = null;
        if (method == Method.POST || method == Method.PUT) {
            body = readBody(session);
        }

        if (body != null) {
            Log.d(TAG, "Request body length: " + body.length());
        }

        // 根据 URI path 分发到对应处理方法
        String path = uri;
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }

        switch (path) {
            case AirPlayConstants.ENDPOINT_SERVER_INFO:
                return requestHandler.handleServerInfo(session);
            case AirPlayConstants.ENDPOINT_PLAY:
                return requestHandler.handlePlay(session, body);
            case AirPlayConstants.ENDPOINT_STOP:
                return requestHandler.handleStop(session);
            case AirPlayConstants.ENDPOINT_SCRUB:
                return requestHandler.handleScrub(session, uri);
            case AirPlayConstants.ENDPOINT_RATE:
                return requestHandler.handleRate(session, uri);
            case AirPlayConstants.ENDPOINT_PLAYBACK_INFO:
                return requestHandler.handlePlaybackInfo(session);
            case AirPlayConstants.ENDPOINT_PHOTO:
                return requestHandler.handlePhoto(session, body);
            case AirPlayConstants.ENDPOINT_REVERSE:
                return requestHandler.handleReverse(session);
            default:
                Log.w(TAG, "Unknown endpoint: " + path);
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
    }

    /** getPort - 获取 HTTP 服务器端口号 */
    public int getPort() {
        return AirPlayConstants.AIRPLAY_PORT;
    }

    /** readBody - 从输入流读取请求体（支持无 Content-Length 的情况，1MB 安全限制） */
    private String readBody(IHTTPSession session) {
        try {
            InputStream is = session.getInputStream();
            if (is == null) return null;

            int contentLength = -1;
            String contentLengthStr = session.getHeaders().get("content-length");
            if (contentLengthStr != null) {
                try {
                    contentLength = Integer.parseInt(contentLengthStr.trim());
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid content-length: " + contentLengthStr);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalRead = 0;

            if (contentLength > 0) {
                while (totalRead < contentLength) {
                    bytesRead = is.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead));
                    if (bytesRead == -1) break;
                    baos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
            } else {
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (totalRead > 1024 * 1024) break; // 1MB safety limit
                }
            }

            if (totalRead > 0) {
                return new String(baos.toByteArray(), "UTF-8");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading body", e);
        }
        return null;
    }
}
