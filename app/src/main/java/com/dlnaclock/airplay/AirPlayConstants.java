package com.dlnaclock.airplay;

/**
 * AirPlayConstants - AirPlay 协议常量类
 * 包含端口、mDNS 服务类型、Feature Flags、协议版本、HTTP 端点等常量
 */
public class AirPlayConstants {

    /** AirPlay HTTP 服务器默认端口 */
    public static final int AIRPLAY_PORT = 7000;

    /** mDNS 服务类型（Bonjour 发现） */
    public static final String SERVICE_TYPE_AIRPLAY = "_airplay._tcp.local.";

    /** AirPlay features flags（无加密模式） */
    public static final String FEATURES = "0x5A7FFFF7,0x1E";

    /** 协议版本 */
    public static final String PROTOVERS = "1.0";
    /** 源代码版本（模拟 Apple TV） */
    public static final String SRCVERS = "366.0";
    /** 设备型号（模拟 Apple TV 5,3） */
    public static final String MODEL = "AppleTV5,3";
    /** 操作系统信息 */
    public static final String OS_INFO = "Mac OS X";

    // === AirPlay HTTP 端点 ===
    public static final String ENDPOINT_SERVER_INFO   = "/server-info";
    public static final String ENDPOINT_PLAY           = "/play";
    public static final String ENDPOINT_STOP           = "/stop";
    public static final String ENDPOINT_SCRUB          = "/scrub";
    public static final String ENDPOINT_RATE           = "/rate";
    public static final String ENDPOINT_PLAYBACK_INFO  = "/playback-info";
    public static final String ENDPOINT_PHOTO          = "/photo";
    public static final String ENDPOINT_REVERSE        = "/reverse";
}
