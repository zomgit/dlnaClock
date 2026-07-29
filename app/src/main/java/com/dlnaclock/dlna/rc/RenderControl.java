package com.dlnaclock.dlna.rc;

/**
 * RenderControl - 播放控制接口
 * 参考 DLNA-Cast 的 RenderControl 设计，解耦 DLNA 协议层与实际播放器
 * 播放器 Activity 实现此接口，AVTransportService 通过此接口控制播放
 */
public interface RenderControl {

    /**
     * play - 开始/恢复播放
     * @return true 如果操作成功
     */
    boolean play();

    /**
     * pause - 暂停播放
     * @return true 如果操作成功
     */
    boolean pause();

    /**
     * stop - 停止播放
     * @return true 如果操作成功
     */
    boolean stop();

    /**
     * seekTo - 跳转到指定位置
     * @param positionMs 目标位置（毫秒）
     * @return true 如果操作成功
     */
    boolean seekTo(long positionMs);

    /**
     * getState - 获取当前播放状态
     * @return 播放状态字符串（PLAYING/PAUSED_PLAYBACK/STOPPED 等）
     */
    String getState();

    /**
     * getPosition - 获取当前播放位置（毫秒）
     * @return 当前播放位置
     */
    long getPosition();

    /**
     * getDuration - 获取媒体总时长（毫秒）
     * @return 媒体时长
     */
    long getDuration();

    /**
     * setMediaUri - 设置媒体 URI
     * @param uri 媒体播放地址
     * @param metadata 媒体元数据（DIDL-Lite XML）
     */
    void setMediaUri(String uri, String metadata);

    /**
     * isPlaying - 是否正在播放
     * @return true 如果正在播放
     */
    boolean isPlaying();
}
