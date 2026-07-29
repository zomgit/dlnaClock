package com.dlnaclock.dlna.server;

import android.util.Log;

import com.dlnaclock.dlna.device.DeviceDescriptionBuilder;
import com.dlnaclock.dlna.device.DmrDevice;
import com.dlnaclock.dlna.soap.SoapHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD;

public class DlnaHttpHandler {

    private static final String TAG = "DlnaHttpHandler";

    private DmrDevice device;
    private String localIp;
    private SoapHandler soapHandler;
    private Map<String, String> subscriptions = new ConcurrentHashMap<>();

    public DlnaHttpHandler(DmrDevice device, String localIp, SoapHandler soapHandler) {
        this.device = device;
        this.localIp = localIp;
        this.soapHandler = soapHandler;
    }

    public NanoHTTPD.Response handleRequest(NanoHTTPD.Method method, String uri,
                                            Map<String, String> headers, String body) {
        Log.d(TAG, "Handling: " + method + " " + uri);

        try {
            // Device description
            if ("/description.xml".equals(uri) && method == NanoHTTPD.Method.GET) {
                return handleDeviceDescription();
            }

            // SCPD documents
            if (uri.startsWith("/scpd/") && method == NanoHTTPD.Method.GET) {
                return handleSCPD(uri);
            }

            // Control endpoints
            if (uri.startsWith("/ctl/") && method == NanoHTTPD.Method.POST) {
                return handleControl(uri, headers, body);
            }

            // Event subscription
            if (uri.startsWith("/event/")) {
                return handleEvent(method, uri, headers);
            }

            // Default: 404
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain", "Not Found");

        } catch (Exception e) {
            Log.e(TAG, "Error handling request: " + uri, e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/plain", "Internal Server Error");
        }
    }

    private NanoHTTPD.Response handleDeviceDescription() {
        String xml = DeviceDescriptionBuilder.build(device, localIp, 49152);
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, "text/xml; charset=utf-8", xml);
        response.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");
        return response;
    }

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

        String action = SoapHandler.parseAction(soapAction, body);
        if (action == null) {
            Log.w(TAG, "Could not parse SOAP action from header or body");
            SoapHandler.SoapResponse errorResp = SoapHandler.buildErrorResponse(
                    com.dlnaclock.dlna.soap.SoapConstants.ERROR_INVALID_ACTION,
                    "Could not determine SOAP action");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/xml; charset=utf-8", errorResp.body);
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

    private NanoHTTPD.Response handleEvent(NanoHTTPD.Method method, String uri,
                                           Map<String, String> headers) {
        if (method == NanoHTTPD.Method.SUBSCRIBE) {
            // New subscription or renewal
            String sid = "uuid:" + UUID.randomUUID().toString();
            String timeout = headers.get("timeout");
            if (timeout == null) timeout = "Second-1800";

            subscriptions.put(sid, uri);

            NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, "text/xml", "");
            response.addHeader("SID", sid);
            response.addHeader("TIMEOUT", timeout);
            response.addHeader("SERVER", "Android/4.4 UPnP/1.1 DlnaClock/1.0");
            return response;

        } else if (method == NanoHTTPD.Method.UNSUBSCRIBE) {
            String sid = headers.get("sid");
            if (sid != null) {
                subscriptions.remove(sid);
            }
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/xml", "");

        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "text/plain", "Method not allowed");
        }
    }
}
