package com.dlnaclock.dlna.server;

import android.util.Log;

import com.dlnaclock.dlna.device.DeviceDescriptionBuilder;
import com.dlnaclock.dlna.device.DmrDevice;
import com.dlnaclock.dlna.soap.SoapHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class DlnaHttpServer extends NanoHTTPD {

    private static final String TAG = "DlnaHttpServer";
    private static final int HTTP_PORT = 49152;

    private DmrDevice device;
    private String localIp;
    private SoapHandler soapHandler;
    private DlnaHttpHandler requestHandler;

    public DlnaHttpServer(DmrDevice device, String localIp, SoapHandler soapHandler) {
        super(HTTP_PORT);
        this.device = device;
        this.localIp = localIp;
        this.soapHandler = soapHandler;
        this.requestHandler = new DlnaHttpHandler(device, localIp, soapHandler);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, method + " " + uri);

        // Read body if present
        Map<String, String> bodyMap = new HashMap<>();
        try {
            session.parseBody(bodyMap);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing body", e);
        }

        String body = bodyMap.get("postData");
        if (body == null) {
            // Try reading raw body
            body = readBody(session);
        }

        return requestHandler.handleRequest(method, uri, session.getHeaders(), body);
    }

    private String readBody(IHTTPSession session) {
        try {
            InputStream is = session.getInputStream();
            if (is == null) return null;

            String contentLengthStr = session.getHeaders().get("content-length");
            if (contentLengthStr == null) return null;

            int contentLength = Integer.parseInt(contentLengthStr);
            if (contentLength <= 0) return null;

            byte[] buffer = new byte[contentLength];
            int bytesRead = 0;
            while (bytesRead < contentLength) {
                int read = is.read(buffer, bytesRead, contentLength - bytesRead);
                if (read == -1) break;
                bytesRead += read;
            }
            return new String(buffer, 0, bytesRead, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Error reading body", e);
            return null;
        }
    }

    public int getPort() {
        return HTTP_PORT;
    }
}
