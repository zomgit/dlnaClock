package com.dlnaclock.dlna.server;

import com.dlnaclock.util.LogUtil;

import com.dlnaclock.dlna.device.DeviceDescriptionBuilder;
import com.dlnaclock.dlna.device.DmrDevice;
import com.dlnaclock.dlna.gena.GenaManager;
import com.dlnaclock.dlna.soap.SoapHandler;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * DlnaHttpHandler - HTTP 请求路由处理器
 * 根据 URI 分发请求到对应处理逻辑：
 * /description.xml → 设备描述
 * /scpd/* → 服务定义文档
 * /ctl/* → SOAP 控制端点
 * /event/* → GENA 事件订阅
 */
public class DlnaHttpHandler {

    private static final String TAG = "DlnaHttpHandler";

    private DmrDevice device;                                          // 设备信息
    private String localIp;                                            // 本机 IP
    private SoapHandler soapHandler;                                   // SOAP 处理器
    private GenaManager genaManager;                                   // GENA 事件管理器
    private byte[] iconBytes;                                          // 设备图标 PNG 数据

    /** DlnaHttpHandler - 构造函数 */
    public DlnaHttpHandler(DmrDevice device, String localIp, SoapHandler soapHandler,
                           GenaManager genaManager, byte[] iconBytes) {
        this.device = device;
        this.localIp = localIp;
        this.soapHandler = soapHandler;
        this.genaManager = genaManager;
        this.iconBytes = iconBytes;
    }

    /** handleRequest - 请求路由入口，根据 URI 和方法分发到对应处理逻辑 */
    public NanoHTTPD.Response handleRequest(NanoHTTPD.Method method, String uri,
                                            Map<String, String> headers, String body) {
        LogUtil.d(TAG, "Handling: " + method + " " + uri);

        try {
            // Device description
            if ("/description.xml".equals(uri) && method == NanoHTTPD.Method.GET) {
                LogUtil.d(TAG, "Serving device description.xml");
                return handleDeviceDescription();
            }

            // SCPD documents
            if (uri.startsWith("/scpd/") && method == NanoHTTPD.Method.GET) {
                LogUtil.d(TAG, "Serving SCPD: " + uri);
                return handleSCPD(uri);
            }

            // Device icon
            if ("/icon.png".equals(uri) && method == NanoHTTPD.Method.GET) {
                return handleIcon();
            }

            // Control endpoints
            if (uri.startsWith("/ctl/") && method == NanoHTTPD.Method.POST) {
                return handleControl(uri, headers, body);
            }

            // Event subscription (SUBSCRIBE/UNSUBSCRIBE)
            if (uri.startsWith("/event/")) {
                String methodName = method != null ? method.toString() : "UNKNOWN";
                LogUtil.i(TAG, "GENA " + methodName + " for " + uri);
                return handleEvent(method, uri, headers);
            }

            // Default: 404
            LogUtil.w(TAG, "Unknown request: " + method + " " + uri + " -> 404");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain", "Not Found");

        } catch (Exception e) {
            LogUtil.e(TAG, "Error handling request: " + uri, e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/plain", "Internal Server Error");
        }
    }

    /** handleDeviceDescription - 返回设备描述 XML */
    private NanoHTTPD.Response handleDeviceDescription() {
        String xml = DeviceDescriptionBuilder.build(device, localIp, 49152);
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, "text/xml; charset=utf-8", xml);
        response.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");
        return response;
    }

    /** handleIcon - 返回设备图标 PNG */
    private NanoHTTPD.Response handleIcon() {
        if (iconBytes == null || iconBytes.length == 0) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain", "No icon available");
        }
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, "image/png",
                new java.io.ByteArrayInputStream(iconBytes), iconBytes.length);
        response.addHeader("CACHE-CONTROL", "max-age=86400");
        return response;
    }

    /** handleSCPD - 返回服务 SCPD 文档（AVTransport/RenderingControl/ConnectionManager） */
    private NanoHTTPD.Response handleSCPD(String uri) {
        String xml;
        if (uri.contains("AVTransport")) {
            xml = DeviceDescriptionBuilder.buildAVTransportSCPD();
        } else if (uri.contains("RenderingControl")) {
            xml = DeviceDescriptionBuilder.buildRenderingControlSCPD();
        } else if (uri.contains("ConnectionManager")) {
            xml = DeviceDescriptionBuilder.buildConnectionManagerSCPD();
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain", "Unknown SCPD");
        }
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, "text/xml; charset=utf-8", xml);
        response.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");
        return response;
    }

    /** handleControl - 处理 SOAP 控制请求，解析 Action 并调用 SoapHandler */
    private NanoHTTPD.Response handleControl(String uri, Map<String, String> headers, String body) {
        // Determine service type from URI
        String serviceType;
        if (uri.contains("AVTransport")) {
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1";
        } else if (uri.contains("RenderingControl")) {
            serviceType = "urn:schemas-upnp-org:service:RenderingControl:1";
        } else if (uri.contains("ConnectionManager")) {
            serviceType = "urn:schemas-upnp-org:service:ConnectionManager:1";
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain", "Unknown control endpoint");
        }

        // Parse SOAP action
        String soapAction = headers.get("soapaction");
        if (soapAction == null) {
            soapAction = headers.get("SOAPACTION");
        }

        LogUtil.d(TAG, "Control request - URI: " + uri + ", SOAPAction: " + soapAction);
        if (body != null) {
            LogUtil.d(TAG, "Control body length: " + body.length());
        } else {
            LogUtil.w(TAG, "Control body is NULL! Cannot process SOAP request.");
        }

        String action = SoapHandler.parseAction(soapAction, body);
        if (action == null) {
            LogUtil.w(TAG, "Could not parse SOAP action from header or body");
            SoapHandler.SoapResponse errorResp = SoapHandler.buildErrorResponse(
                    com.dlnaclock.dlna.soap.SoapConstants.ERROR_INVALID_ACTION,
                    "Could not determine SOAP action");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/xml; charset=utf-8", errorResp.body);
        }

        // 关键操作额外日志
        if ("SetAVTransportURI".equals(action) || "GetProtocolInfo".equals(action)
                || "Play".equals(action) || "Stop".equals(action)) {
            LogUtil.d(TAG, "SOAP action: " + action + " on " + serviceType);
        }

        // Handle the action
        SoapHandler.SoapResponse soapResponse = soapHandler.handleRequest(serviceType, action, body);

        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                soapResponse.statusCode == 200 ? NanoHTTPD.Response.Status.OK
                        : NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "text/xml; charset=utf-8",
                soapResponse.body);
        response.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");
        response.addHeader("EXT", "");
        return response;
    }

    /** handleEvent - 处理 GENA 事件订阅（SUBSCRIBE/UNSUBSCRIBE/RENEW） */
    private NanoHTTPD.Response handleEvent(NanoHTTPD.Method method, String uri,
                                           Map<String, String> headers) {
        String methodName = method.toString();
        LogUtil.d(TAG, "Event " + methodName + " for " + uri);

        if ("SUBSCRIBE".equalsIgnoreCase(methodName)) {
            String callback = headers.get("callback");
            String sid = headers.get("sid");
            String timeout = headers.get("timeout");
            int timeoutSec = 1800;
            if (timeout != null) {
                try {
                    timeoutSec = Integer.parseInt(timeout.replace("Second-", "").trim());
                } catch (NumberFormatException e) {
                    timeoutSec = 1800;
                }
            }

            // 检查是否为订阅续期（有 SID 但无 CALLBACK）
            if (sid != null && !sid.isEmpty() && (callback == null || callback.isEmpty())) {
                // Renewal request
                boolean renewed = genaManager.renewSubscription(sid, timeoutSec);
                if (renewed) {
                    NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK, "text/xml", "");
                    response.addHeader("SID", sid);
                    response.addHeader("TIMEOUT", genaManager.getSubscriptionTimeout(sid));
                    response.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");
                    LogUtil.d(TAG, "GENA subscription renewed: " + sid);
                    return response;
                } else {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.PRECONDITION_FAILED,
                            "text/plain", "Invalid SID");
                }
            }

            if (callback == null || callback.isEmpty()) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.PRECONDITION_FAILED,
                        "text/plain", "Missing CALLBACK header");
            }

            // Determine service type from URI
            String serviceType = uri;

            sid = genaManager.addSubscription(callback, serviceType, timeoutSec);

            NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, "text/xml", "");
            response.addHeader("SID", sid);
            response.addHeader("TIMEOUT", genaManager.getSubscriptionTimeout(sid));
            response.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");

            return response;

        } else if ("UNSUBSCRIBE".equalsIgnoreCase(methodName)) {
            String sid = headers.get("sid");
            if (sid != null) {
                genaManager.removeSubscription(sid);
            }
            NanoHTTPD.Response unsubResponse = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/xml", "");
            unsubResponse.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");
            return unsubResponse;

        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "text/plain", "Method not allowed");
        }
    }
}
