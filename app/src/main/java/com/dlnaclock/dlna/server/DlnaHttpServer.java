package com.dlnaclock.dlna.server;

import android.util.Log;

import com.dlnaclock.dlna.gena.GenaManager;
import com.dlnaclock.dlna.soap.SoapHandler;
import com.dlnaclock.util.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

/**
 * DlnaHttpServer - DLNA HTTP 服务器
 * 继承 NanoHTTPD，处理 UPnP 设备描述、SCPD、SOAP 控制、GENA 事件等请求
 * 监听端口 49152
 */
public class DlnaHttpServer extends NanoHTTPD {

    private static final String TAG = "DlnaHttpServer";
    public static final int DEFAULT_HTTP_PORT = 49152; // DLNA 标准 HTTP 端口
    public static final int MAX_PORT_RETRY = 10;       // 端口占用时最大重试次数

    private int actualPort;                            // 实际使用的端口
    private DlnaHttpHandler requestHandler; // 请求路由处理器

    /** DlnaHttpServer - 构造函数，创建 HTTP 服务器（不自动启动） */
    public DlnaHttpServer(com.dlnaclock.dlna.device.DmrDevice device, String localIp,
                          SoapHandler soapHandler, GenaManager genaManager, byte[] iconBytes, int port) {
        super(port);
        this.actualPort = port;
        this.requestHandler = new DlnaHttpHandler(device, localIp, soapHandler, genaManager, iconBytes);
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

        // 仅对 POST/PUT 请求读取 body（GET/HEAD 等无 body，读取会导致 Socket 异常）
        String body = null;
        if (method == Method.POST || method == Method.PUT) {
            body = readBody(session);
        }

        if (body != null) {
            Log.d(TAG, "Request body length: " + body.length());
            // SOAP /ctl/ 请求：打印完整请求体及请求头，用于诊断 BubbleUPnP 等控制端的行为
            if (uri.startsWith("/ctl/")) {
                LogUtil.i(TAG, "========== SOAP REQUEST ==========");
                LogUtil.i(TAG, method + " " + uri);
                java.util.Map<String, String> hdrs = session.getHeaders();
                for (java.util.Map.Entry<String, String> e : hdrs.entrySet()) {
                    LogUtil.i(TAG, e.getKey() + ": " + e.getValue());
                }
                LogUtil.i(TAG, "");
                LogUtil.i(TAG, body);
                LogUtil.i(TAG, "===================================");
            } else {
                // 非 SOAP 请求：仅记录前 200 字符预览
                Log.d(TAG, "Request body preview: " + body.substring(0, Math.min(200, body.length())));
            }
        }

        return requestHandler.handleRequest(method, uri, session.getHeaders(), body);
    }

    /** readBody - 从输入流读取请求体（支持无 Content-Length 的情况，1MB 安全限制） */
    private String readBody(IHTTPSession session) {
        try {
            InputStream is = session.getInputStream();
            if (is == null) return null;

            // Try to determine content length
            int contentLength = -1;
            String contentLengthStr = session.getHeaders().get("content-length");
            if (contentLengthStr != null) {
                try {
                    contentLength = Integer.parseInt(contentLengthStr.trim());
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid content-length: " + contentLengthStr);
                }
            }

            // Read all available data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalRead = 0;

            if (contentLength > 0) {
                // Read exact amount
                while (totalRead < contentLength) {
                    bytesRead = is.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead));
                    if (bytesRead == -1) break;
                    baos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
            } else {
                // Read until stream ends or no data available
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

    /** getPort - 获取 HTTP 服务器实际使用的端口号 */
    public int getPort() {
        return actualPort;
    }
}
