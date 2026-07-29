package com.dlnaclock.dlna.avt;

/**
 * TransportState - AVTransport 播放状态枚举
 * 定义 UPnP AVTransport 规范中的 5 种播放状态
 */
public enum TransportState {
    /** 无媒体 */
    NO_MEDIA_PRESENT("NO_MEDIA_PRESENT"),
    /** 已停止 */
    STOPPED("STOPPED"),
    /** 播放中 */
    PLAYING("PLAYING"),
    /** 暂停播放 */
    PAUSED_PLAYBACK("PAUSED_PLAYBACK"),
    /** 转换中（加载/缓冲） */
    TRANSITIONING("TRANSITIONING");

    private final String value; // 状态字符串值

    /** TransportState - 构造函数 */
    TransportState(String value) {
        this.value = value;
    }

    /** getValue - 获取状态的字符串表示 */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
