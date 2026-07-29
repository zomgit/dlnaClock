package com.dlnaclock.dlna.ssdp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.Map;

public class SsdpMessage {

    private String method; // NOTIFY, M-SEARCH, or HTTP/1.1 200 OK
    private String url; // For M-SEARCH, the * URL
    private Map<String, String> headers = new HashMap<>();
    private byte[] rawData;

    public SsdpMessage(DatagramPacket packet) {
        this.rawData = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), this.rawData, 0, packet.getLength());
        parse(new String(rawData));
    }

    public SsdpMessage(String data) {
        this.rawData = data.getBytes();
        parse(data);
    }

    private void parse(String data) {
        if (data == null || data.isEmpty()) return;

        String[] lines = data.split("\r\n");
        if (lines.length == 0) return;

        // First line: method or status
        String firstLine = lines[0].trim();
        if (firstLine.startsWith("NOTIFY")) {
            method = SsdpConstants.METHOD_NOTIFY;
        } else if (firstLine.startsWith("M-SEARCH")) {
            method = SsdpConstants.METHOD_MSEARCH;
            // Parse URL if present
            String[] parts = firstLine.split("\\s+");
            if (parts.length >= 2) {
                url = parts[1];
            }
        } else if (firstLine.startsWith("HTTP/")) {
            method = SsdpConstants.METHOD_HTTP_OK;
        }

        // Parse headers
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim().toUpperCase();
                String value = line.substring(colonIdx + 1).trim();
                headers.put(key, value);
            }
        }
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getHeader(String name) {
        return headers.get(name.toUpperCase());
    }

    public String getHost() {
        return getHeader(SsdpConstants.HEADER_HOST);
    }

    public String getCacheControl() {
        return getHeader(SsdpConstants.HEADER_CACHE_CONTROL);
    }

    public String getLocation() {
        return getHeader(SsdpConstants.HEADER_LOCATION);
    }

    public String getNT() {
        return getHeader(SsdpConstants.HEADER_NT);
    }

    public String getNTS() {
        return getHeader(SsdpConstants.HEADER_NTS);
    }

    public String getUSN() {
        return getHeader(SsdpConstants.HEADER_USN);
    }

    public String getST() {
        return getHeader(SsdpConstants.HEADER_ST);
    }

    public String getMX() {
        return getHeader(SsdpConstants.HEADER_MX);
    }

    public String getMAN() {
        return getHeader(SsdpConstants.HEADER_MAN);
    }

    public String getServer() {
        return getHeader(SsdpConstants.HEADER_SERVER);
    }

    public int getMaxAge() {
        String cc = getCacheControl();
        if (cc != null) {
            String maxAgeStr = cc.replace("max-age=", "").trim();
            try {
                return Integer.parseInt(maxAgeStr);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return SsdpConstants.DEFAULT_CACHE_CONTROL;
    }

    public boolean isMSearch() {
        return SsdpConstants.METHOD_MSEARCH.equals(method);
    }

    public boolean isNotify() {
        return SsdpConstants.METHOD_NOTIFY.equals(method);
    }

    public boolean isAlive() {
        return SsdpConstants.NTS_ALIVE.equals(getNTS());
    }

    public boolean isByeBye() {
        return SsdpConstants.NTS_BYEBYE.equals(getNTS());
    }

    public boolean isSearchTargetMatch(String target) {
        if (isMSearch()) {
            String st = getST();
            return target.equals(st) || SsdpConstants.NT_ALL.equals(st);
        }
        return false;
    }

    public byte[] getRawData() {
        return rawData;
    }

    // Build response for M-SEARCH
    public static String buildSearchResponse(String location, String udn, String deviceType, int maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("CACHE-CONTROL: max-age=").append(maxAge).append("\r\n");
        sb.append("DATE: ").append(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date())).append("\r\n");
        sb.append("EXT:\r\n");
        sb.append("LOCATION: ").append(location).append("\r\n");
        sb.append("SERVER: ").append(SsdpConstants.SERVER_INFO).append("\r\n");
        sb.append("ST: ").append(deviceType).append("\r\n");
        sb.append("USN: ").append(udn).append("::").append(deviceType).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    // Build NOTIFY alive message
    public static String buildNotifyAlive(String location, String udn, String nt, int maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append("NOTIFY * HTTP/1.1\r\n");
        sb.append("HOST: ").append(SsdpConstants.SSDP_MULTICAST_ADDRESS).append(":").append(SsdpConstants.SSDP_PORT).append("\r\n");
        sb.append("CACHE-CONTROL: max-age=").append(maxAge).append("\r\n");
        sb.append("LOCATION: ").append(location).append("\r\n");
        sb.append("NT: ").append(nt).append("\r\n");
        sb.append("NTS: ").append(SsdpConstants.NTS_ALIVE).append("\r\n");
        sb.append("SERVER: ").append(SsdpConstants.SERVER_INFO).append("\r\n");
        sb.append("USN: ").append(udn);
        if (!nt.equals(udn)) {
            sb.append("::").append(nt);
        }
        sb.append("\r\n\r\n");
        return sb.toString();
    }

    // Build NOTIFY byebye message
    public static String buildNotifyByeBye(String udn, String nt) {
        StringBuilder sb = new StringBuilder();
        sb.append("NOTIFY * HTTP/1.1\r\n");
        sb.append("HOST: ").append(SsdpConstants.SSDP_MULTICAST_ADDRESS).append(":").append(SsdpConstants.SSDP_PORT).append("\r\n");
        sb.append("NT: ").append(nt).append("\r\n");
        sb.append("NTS: ").append(SsdpConstants.NTS_BYEBYE).append("\r\n");
        sb.append("USN: ").append(udn);
        if (!nt.equals(udn)) {
            sb.append("::").append(nt);
        }
        sb.append("\r\n\r\n");
        return sb.toString();
    }
}
