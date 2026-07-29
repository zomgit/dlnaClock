package com.dlnaclock.dlna.ssdp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.Map;

/**
 * SsdpMessage - SSDP 消息解析与构建类
 * 解析收到的 SSDP 消息（M-SEARCH/NOTIFY/HTTP 200 OK）
 * 提供静态方法构建搜索响应、alive/byebye 通知消息
 */
public class SsdpMessage {

    private String method; // 消息方法: NOTIFY / M-SEARCH / HTTP/1.1 200 OK
    private String url;    // M-SEARCH 的请求 URL（通常为 *）
    private Map<String, String> headers = new HashMap<>(); // 消息头键值对
    private byte[] rawData; // 原始字节数据

    /** SsdpMessage - 从 DatagramPacket 解析 SSDP 消息 */
    public SsdpMessage(DatagramPacket packet) {
        this.rawData = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), this.rawData, 0, packet.getLength());
        parse(new String(rawData));
    }

    /** SsdpMessage - 从字符串解析 SSDP 消息 */
    public SsdpMessage(String data) {
        this.rawData = data.getBytes();
        parse(data);
    }

    /** parse - 解析 SSDP 消息文本，提取方法和头部字段 */
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

    /** getMethod - 获取消息方法（NOTIFY/M-SEARCH/HTTP OK） */
    public String getMethod() {
        return method;
    }

    /** getUrl - 获取请求 URL */
    public String getUrl() {
        return url;
    }

    /** getHeader - 根据名称获取头部字段值（大小写不敏感） */
    public String getHeader(String name) {
        return headers.get(name.toUpperCase());
    }

    /** getHost - 获取 HOST 头部 */
    public String getHost() {
        return getHeader(SsdpConstants.HEADER_HOST);
    }

    /** getCacheControl - 获取 CACHE-CONTROL 头部 */
    public String getCacheControl() {
        return getHeader(SsdpConstants.HEADER_CACHE_CONTROL);
    }

    /** getLocation - 获取 LOCATION 头部（设备描述 XML 的 URL） */
    public String getLocation() {
        return getHeader(SsdpConstants.HEADER_LOCATION);
    }

    /** getNT - 获取 NT 头部（通知类型） */
    public String getNT() {
        return getHeader(SsdpConstants.HEADER_NT);
    }

    /** getNTS - 获取 NTS 头部（通知子类型: alive/byebye/update） */
    public String getNTS() {
        return getHeader(SsdpConstants.HEADER_NTS);
    }

    /** getUSN - 获取 USN 头部（唯一服务名称） */
    public String getUSN() {
        return getHeader(SsdpConstants.HEADER_USN);
    }

    /** getST - 获取 ST 头部（搜索目标） */
    public String getST() {
        return getHeader(SsdpConstants.HEADER_ST);
    }

    /** getMX - 获取 MX 头部（最大等待时间） */
    public String getMX() {
        return getHeader(SsdpConstants.HEADER_MX);
    }

    /** getMAN - 获取 MAN 头部 */
    public String getMAN() {
        return getHeader(SsdpConstants.HEADER_MAN);
    }

    /** getServer - 获取 SERVER 头部 */
    public String getServer() {
        return getHeader(SsdpConstants.HEADER_SERVER);
    }

    /** getMaxAge - 解析 CACHE-CONTROL 中的 max-age 值（秒） */
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

    /** isMSearch - 判断是否为 M-SEARCH 搜索请求 */
    public boolean isMSearch() {
        return SsdpConstants.METHOD_MSEARCH.equals(method);
    }

    /** isNotify - 判断是否为 NOTIFY 通知 */
    public boolean isNotify() {
        return SsdpConstants.METHOD_NOTIFY.equals(method);
    }

    /** isAlive - 判断是否为 ssdp:alive 通告 */
    public boolean isAlive() {
        return SsdpConstants.NTS_ALIVE.equals(getNTS());
    }

    /** isByeBye - 判断是否为 ssdp:byebye 离线通知 */
    public boolean isByeBye() {
        return SsdpConstants.NTS_BYEBYE.equals(getNTS());
    }

    /** isSearchTargetMatch - 判断搜索目标是否匹配当前设备 */
    public boolean isSearchTargetMatch(String target) {
        if (isMSearch()) {
            String st = getST();
            return target.equals(st) || SsdpConstants.NT_ALL.equals(st);
        }
        return false;
    }

    /** getRawData - 获取原始字节数据 */
    public byte[] getRawData() {
        return rawData;
    }

    /**
     * buildSearchResponse - 构建 M-SEARCH 响应消息 (HTTP 200 OK)
     * @param location 设备描述 XML URL
     * @param udn 设备唯一标识
     * @param st       搜索目标 (ST)，可为设备/服务类型或 UDN
     * @param maxAge 缓存有效期（秒）
     * @param bootId UPnP 1.1 启动 ID（Unix 时间戳秒）
     * @return 格式化的 SSDP 响应字符串
     */
    public static String buildSearchResponse(String location, String udn, String st, int maxAge, long bootId) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("HOST: ").append(SsdpConstants.SSDP_MULTICAST_ADDRESS).append(":").append(SsdpConstants.SSDP_PORT).append("\r\n");
        sb.append("CACHE-CONTROL: max-age=").append(maxAge).append("\r\n");
        // UPnP 规范要求 DATE 使用 RFC 1123 格式（GMT 时区）
        java.text.SimpleDateFormat rfc1123 = new java.text.SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US);
        rfc1123.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        sb.append("DATE: ").append(rfc1123.format(new java.util.Date())).append("\r\n");
        sb.append("EXT: \r\n");
        sb.append("LOCATION: ").append(location).append("\r\n");
        sb.append("SERVER: ").append(SsdpConstants.SERVER_INFO).append("\r\n");
        sb.append("ST: ").append(st).append("\r\n");
        // USN format: if ST is the UDN itself, USN = UDN; otherwise USN = UDN::ST
        sb.append("USN: ").append(udn);
        if (!st.equals(udn)) {
            sb.append("::").append(st);
        }
        sb.append("\r\n");
        // UPnP 1.1 必需头部
        sb.append("OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n");
        sb.append("01-NLS: ").append(bootId).append("\r\n");
        sb.append("BOOTID.UPNP.ORG: ").append(bootId).append("\r\n");
        sb.append("CONFIGID.UPNP.ORG: 1\r\n");
        sb.append("SEARCHPORT.UPNP.ORG: ").append(SsdpConstants.SSDP_PORT).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    /**
     * buildNotifyAlive - 构建 NOTIFY ssdp:alive 通告消息
     * @param location 设备描述 XML URL
     * @param udn 设备唯一标识
     * @param nt 通知类型
     * @param maxAge 缓存有效期（秒）
     * @param bootId UPnP 1.1 启动 ID（Unix 时间戳秒）
     * @return 格式化的 SSDP alive 通知字符串
     */
    public static String buildNotifyAlive(String location, String udn, String nt, int maxAge, long bootId) {
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
        sb.append("\r\n");
        // UPnP 1.1 必需头部
        sb.append("OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n");
        sb.append("01-NLS: ").append(bootId).append("\r\n");
        sb.append("BOOTID.UPNP.ORG: ").append(bootId).append("\r\n");
        sb.append("CONFIGID.UPNP.ORG: 1\r\n");
        sb.append("SEARCHPORT.UPNP.ORG: ").append(SsdpConstants.SSDP_PORT).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    /**
     * buildNotifyByeBye - 构建 NOTIFY ssdp:byebye 离线通知消息
     * @param udn 设备唯一标识
     * @param nt 通知类型
     * @param bootId UPnP 1.1 启动 ID（Unix 时间戳秒）
     * @return 格式化的 SSDP byebye 通知字符串
     */
    public static String buildNotifyByeBye(String udn, String nt, long bootId) {
        StringBuilder sb = new StringBuilder();
        sb.append("NOTIFY * HTTP/1.1\r\n");
        sb.append("HOST: ").append(SsdpConstants.SSDP_MULTICAST_ADDRESS).append(":").append(SsdpConstants.SSDP_PORT).append("\r\n");
        sb.append("NT: ").append(nt).append("\r\n");
        sb.append("NTS: ").append(SsdpConstants.NTS_BYEBYE).append("\r\n");
        sb.append("USN: ").append(udn);
        if (!nt.equals(udn)) {
            sb.append("::").append(nt);
        }
        sb.append("\r\n");
        // 与 alive / search 响应保持 UPnP 1.1 头部一致
        sb.append("OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n");
        sb.append("01-NLS: ").append(bootId).append("\r\n");
        sb.append("BOOTID.UPNP.ORG: ").append(bootId).append("\r\n");
        sb.append("CONFIGID.UPNP.ORG: 1\r\n");
        sb.append("SEARCHPORT.UPNP.ORG: ").append(SsdpConstants.SSDP_PORT).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }
}
