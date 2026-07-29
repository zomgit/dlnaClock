package com.dlnaclock.dlna.avt;

import android.os.Handler;
import android.os.Looper;
import com.dlnaclock.util.LogUtil;

import com.dlnaclock.dlna.rc.RenderControl;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AVTransportService - AVTransport 播放控制服务（核心状态机）
 * 管理播放状态、媒体信息、进度跟踪
 * 本身不直接播放媒体，实际播放由播放器 Activity 负责
 * 播放器 Activity 通过回调方法报告进度和状态
 */
public class AVTransportService {

    private static final String TAG = "AVTransportService";

    private TransportState currentState = TransportState.NO_MEDIA_PRESENT; // 当前播放状态
    private MediaInfo mediaInfo = new MediaInfo();    // 当前媒体信息
    private Handler handler = new Handler(Looper.getMainLooper()); // 主线程 Handler
    private long currentPositionMs = 0;  // 当前播放位置（毫秒）
    private long durationMs = 0;         // 媒体总时长（毫秒）
    private boolean isPrepared = false;  // 播放器是否已准备
    private RenderControl renderControl; // 实际播放控制器（由播放器 Activity 设置）
    private volatile boolean pendingPlay = false; // Play 命令在播放器就绪前到达时标记
    private String activeSource = null; // 当前活跃的协议来源："DLNA" 或 "AirPlay"

    private CopyOnWriteArrayList<AVTransportListener> listeners = new CopyOnWriteArrayList<>(); // 事件监听器列表
    private Runnable transitionToPlayingRunnable; // 可取消的 TRANSITIONING → PLAYING 延迟任务

    /** AVTransportListener - AVTransport 事件监听接口，供 DlnaManager 和播放器 Activity 实现 */
    public interface AVTransportListener {
        /** onUriSet - 当新的媒体 URI 被设置时回调 */
        void onUriSet(MediaInfo mediaInfo);
        /** onStateChanged - 当播放状态变化时回调 */
        void onStateChanged(TransportState state);
        /** onPlaybackPositionChanged - 当播放进度更新时回调 */
        void onPlaybackPositionChanged(long positionMs, long durationMs);
        /** onMediaCompleted - 当媒体播放完成时回调 */
        void onMediaCompleted();
        /** onError - 当播放出错时回调 */
        void onError(int what, int extra);
    }

    /** addListener - 添加事件监听器 */
    public void addListener(AVTransportListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** removeListener - 移除事件监听器 */
    public void removeListener(AVTransportListener listener) {
        listeners.remove(listener);
    }

    /** getListenerCount - 获取监听器数量（诊断用） */
    public int getListenerCount() {
        return listeners.size();
    }

    /** setRenderControl - 设置实际播放控制器，并处理挂起的 Play 命令 */
    public void setRenderControl(RenderControl control) {
        this.renderControl = control;
        // 如果 Play 命令在播放器就绪前到达，现在播放器就绪了，执行挂起的 Play
        // 注意：play() 会先设 TRANSITIONING，所以这里检查 TRANSITIONING 或 PLAYING
        if (control != null && pendingPlay
                && (currentState == TransportState.PLAYING || currentState == TransportState.TRANSITIONING)) {
            pendingPlay = false;
            LogUtil.i(TAG, "Executing pending play after render control attached");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (renderControl != null) renderControl.play();
                }
            });
            startPositionUpdate();
            // TRANSITIONING → PLAYING 的延迟任务已在 play() 中设置，无需额外处理
        }
    }

    /**
     * setAVTransportURI - 设置播放媒体 URI（核心方法）
     * 停止当前播放、解析元数据、推断 MIME 类型、通知监听器
     * 状态流转：→ STOPPED + 发送 LastChange（不自动进入 PLAYING）
     */
    public void setAVTransportURI(int instanceId, String uri, String metadata) {
        LogUtil.i(TAG, "setAVTransportURI: " + uri);

        // 协议互斥：如果当前由 AirPlay 控制，DLNA 的 SetURI 需先停止 AirPlay 播放
        if ("AirPlay".equals(activeSource)) {
            LogUtil.i(TAG, "DLNA SetURI while AirPlay is active, stopping AirPlay first");
            stop();
        }

        // 取消挂起的 TRANSITIONING → PLAYING 任务
        cancelTransitionToPlaying();

        // Stop current playback
        stopInternal();

        // Update media info
        mediaInfo.clear();
        mediaInfo.setCurrentUri(uri);
        if (metadata != null && !metadata.isEmpty()) {
            mediaInfo.parseMetadata(metadata);
        }

        // Determine MIME type from URI if not set
        if (mediaInfo.getMimeType() == null || mediaInfo.getMimeType().isEmpty()) {
            mediaInfo.setMimeType(guessMimeType(uri));
        }

        // 默认标记来源为 DLNA
        this.activeSource = "DLNA";

        // Notify listeners of URI change
        for (AVTransportListener l : listeners) {
            l.onUriSet(mediaInfo);
        }

        // 状态转为 STOPPED 并发送 LastChange（GENA NOTIFY）
        setTransportState(TransportState.STOPPED);
    }

    /**
     * setAVTransportURI - 带协议来源标记的重载版本
     * 供 AirPlayManager 等非 DLNA 协议调用
     */
    public void setAVTransportURI(String uri, String metadata, String source) {
        // 协议互斥：如果当前由 DLNA 控制，AirPlay 的 play 需先停止 DLNA 播放
        if ("DLNA".equals(activeSource)) {
            LogUtil.i(TAG, "AirPlay play while DLNA is active, stopping DLNA first");
            stop();
        }
        setAVTransportURI(0, uri, metadata);
        this.activeSource = source;
    }

    /** play - 开始播放（通过 RenderControl 控制实际播放器）
     *  状态流转：→ TRANSITIONING → PLAYING（网络缓冲/prepare 过渡） */
    public void play(String speed) {
        LogUtil.d(TAG, "play, current state: " + currentState);

        if (currentState == TransportState.NO_MEDIA_PRESENT) {
            LogUtil.w(TAG, "No media to play");
            return;
        }

        cancelTransitionToPlaying();

        // 先进入 TRANSITIONING（播放器需要 prepare/buffer）
        setTransportState(TransportState.TRANSITIONING);

        // Delegate to actual player on main thread (MediaPlayer must be accessed from main thread)
        if (renderControl != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (renderControl != null) renderControl.play();
                }
            });
        } else {
            // 播放器尚未就绪（Activity 还没创建完毕），标记挂起 Play
            LogUtil.i(TAG, "Render control not ready, marking pending play");
            pendingPlay = true;
        }

        startPositionUpdate();

        // 延迟转为 PLAYING（给播放器 prepare/buffer 时间）
        transitionToPlayingRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState == TransportState.TRANSITIONING) {
                    setTransportState(TransportState.PLAYING);
                }
            }
        };
        handler.postDelayed(transitionToPlayingRunnable, 150);
    }

    /** pause - 暂停播放（通过 RenderControl 控制实际播放器） */
    public void pause() {
        LogUtil.d(TAG, "pause, current state: " + currentState);
        pendingPlay = false; // 取消挂起的 Play
        if (currentState == TransportState.PLAYING) {
            setTransportState(TransportState.PAUSED_PLAYBACK);
            stopPositionUpdate();

            // Delegate to actual player on main thread
            if (renderControl != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (renderControl != null) renderControl.pause();
                    }
                });
            }
        }
    }

    /** stop - 停止播放（控制端主动停止，直接 → STOPPED，不经 TRANSITIONING） */
    public void stop() {
        LogUtil.d(TAG, "stop, current state: " + currentState);
        pendingPlay = false;
        cancelTransitionToPlaying();
        stopPositionUpdate();
        activeSource = null;

        setTransportState(TransportState.STOPPED);

        // Delegate to actual player on main thread
        if (renderControl != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (renderControl != null) renderControl.stop();
                }
            });
        }
    }

    /** seek - 跳转到指定位置
     *  状态流转：PLAYING → TRANSITIONING → PLAYING / PAUSED → TRANSITIONING → PAUSED */
    public void seek(String unit, String target) {
        LogUtil.d(TAG, "seek: " + unit + " -> " + target);
        try {
            long targetMs = parseTimeToMs(target);
            currentPositionMs = targetMs;
    
            // 记住 seek 前的状态，seek 完成后恢复
            final TransportState stateBeforeSeek = currentState;
    
            // 进入 TRANSITIONING
            setTransportState(TransportState.TRANSITIONING);
    
            // Delegate to actual player on main thread
            if (renderControl != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (renderControl != null) renderControl.seekTo(targetMs);
                        // seek 完成后恢复原状态
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (currentState == TransportState.TRANSITIONING) {
                                    setTransportState(stateBeforeSeek);
                                }
                            }
                        });
                    }
                });
            } else {
                // 无播放器，直接恢复
                setTransportState(stateBeforeSeek);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Error seeking", e);
        }
    }

    /** next - 下一首（单曲目模式下为空操作） */
    public void next() {
        LogUtil.d(TAG, "next - not implemented for single track");
    }

    /** previous - 上一首（单曲目模式下为空操作） */
    public void previous() {
        LogUtil.d(TAG, "previous - not implemented for single track");
    }

    /** getTransportInfo - 获取播放状态信息（供 SOAP GetTransportInfo 调用） */
    public String[] getTransportInfo() {
        return new String[]{
                currentState.getValue(),
                currentState == TransportState.PLAYING || currentState == TransportState.PAUSED_PLAYBACK
                        ? "OK" : "OK",
                "1" // speed
        };
    }

    /** getMediaInfo - 获取媒体信息（供 SOAP GetMediaInfo 调用） */
    public String[] getMediaInfo() {
        String duration = mediaInfo.getDuration();
        String uri = mediaInfo.getCurrentUri();
        String metadata = ""; // Could include DIDL-Lite metadata
        return new String[]{"1", duration, uri, metadata};
    }

    /** getPositionInfo - 获取播放进度信息（供 SOAP GetPositionInfo 调用） */
    public String[] getPositionInfo() {
        String duration = mediaInfo.getDuration();
        String uri = mediaInfo.getCurrentUri();
        String relTime = formatTime(currentPositionMs);
        return new String[]{duration, uri, relTime};
    }

    /** getCurrentState - 获取当前播放状态 */
    public TransportState getCurrentState() {
        return currentState;
    }

    /** getMediaInfoObject - 获取媒体信息对象 */
    public MediaInfo getMediaInfoObject() {
        return mediaInfo;
    }

    /** getCurrentPositionMs - 获取当前播放位置（毫秒） */
    public long getCurrentPositionMs() {
        return currentPositionMs;
    }

    /** getDurationMs - 获取媒体总时长（毫秒） */
    public long getDurationMs() {
        return durationMs;
    }

    /** setActiveSource - 设置当前活跃的协议来源 */
    public void setActiveSource(String source) {
        this.activeSource = source;
    }

    /** getActiveSource - 获取当前活跃的协议来源 */
    public String getActiveSource() {
        return activeSource;
    }

    /**
     * Update playback position from the actual player.
     * Called by player Activities to sync state with control points.
     */
    public void updatePlaybackPosition(long positionMs, long durationMs) {
        this.currentPositionMs = positionMs;
        if (durationMs > 0) {
            this.durationMs = durationMs;
            this.mediaInfo.setDuration(formatTime(durationMs));
        }
    }

    /**
     * Notify that media playback completed.
     * 状态流转：PLAYING → TRANSITIONING（RelativeTime=Duration，发送 LastChange）→ STOPPED（发送 LastChange）
     * 不清除 CurrentTrackURI，保持直到新的 SetAVTransportURI
     */
    public void notifyCompleted() {
        cancelTransitionToPlaying();
        stopPositionUpdate();

        // 已播放到末尾（确保 LastChange 中 RelativeTimeCounter = Duration）
        currentPositionMs = durationMs;

        // 通知正在切换
        setTransportState(TransportState.TRANSITIONING);

        // 进入 STOPPED（不清除 mediaInfo，保持 CurrentTrackURI）
        setTransportState(TransportState.STOPPED);

        for (AVTransportListener l : listeners) {
            l.onMediaCompleted();
        }
    }

    /**
     * Notify error from the actual player.
     */
    public void notifyPlayerError(int what, int extra) {
        cancelTransitionToPlaying();
        setTransportState(TransportState.STOPPED);
        for (AVTransportListener l : listeners) {
            l.onError(what, extra);
        }
    }

    /** stopInternal - 内部停止，重置进度和准备状态 */
    private void stopInternal() {
        stopPositionUpdate();
        currentPositionMs = 0;
        isPrepared = false;
        pendingPlay = false; // 重置挂起 Play 标记
    }

    /**
     * setTransportState - 统一状态变更入口
     * 所有 TransportState 变更都通过此方法，确保：
     * 1. 状态实际更新
     * 2. 本地监听器通知（触发 GENA NOTIFY）
     * 3. 日志记录
     */
    private void setTransportState(TransportState newState) {
        if (currentState == newState) {
            return;
        }
        LogUtil.d(TAG, "setTransportState: " + currentState + " -> " + newState);
        currentState = newState;
        // 通知本地监听器 → DlnaManager.onStateChanged() → GenaManager GENA NOTIFY
        for (AVTransportListener l : listeners) {
            l.onStateChanged(currentState);
        }
    }

    /** cancelTransitionToPlaying - 取消挂起的 TRANSITIONING → PLAYING 延迟任务 */
    private void cancelTransitionToPlaying() {
        if (transitionToPlayingRunnable != null) {
            handler.removeCallbacks(transitionToPlayingRunnable);
            transitionToPlayingRunnable = null;
        }
    }


    /** positionUpdater - 定时进度更新 Runnable，每秒更新位置（优先从 RenderControl 获取真实位置） */
    private Runnable positionUpdater = new Runnable() {
        @Override
        public void run() {
            if (currentState == TransportState.PLAYING) {
                // Prefer real position from RenderControl
                if (renderControl != null) {
                    currentPositionMs = renderControl.getPosition();
                    long dur = renderControl.getDuration();
                    if (dur > 0) {
                        durationMs = dur;
                        mediaInfo.setDuration(formatTime(dur));
                    }
                } else {
                    // Fallback: simulated increment
                    currentPositionMs += 1000;
                }
                for (AVTransportListener l : listeners) {
                    l.onPlaybackPositionChanged(currentPositionMs, durationMs);
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    /** startPositionUpdate - 启动定时进度更新 */
    private void startPositionUpdate() {
        handler.removeCallbacks(positionUpdater);
        handler.post(positionUpdater);
    }

    /** stopPositionUpdate - 停止定时进度更新 */
    private void stopPositionUpdate() {
        handler.removeCallbacks(positionUpdater);
    }

    /** guessMimeType - 根据 URI 推断 MIME 类型（覆盖 Kodi 全部格式） */
    private String guessMimeType(String uri) {
        if (uri == null) return "application/octet-stream";
        String lower = uri.toLowerCase();
        // 音频格式（优先检查，避免 .m4a 被 .mp4 规则误判为视频）
        if (lower.contains(".mp3")) return "audio/mpeg";
        if (lower.contains(".m4a")) return "audio/mp4";
        if (lower.contains(".flac")) return "audio/flac";
        if (lower.contains(".wav")) return "audio/x-wav";
        if (lower.contains(".aac")) return "audio/aac";
        if (lower.contains(".ogg")) return "audio/ogg";
        if (lower.contains(".wma")) return "audio/x-ms-wma";
        if (lower.contains(".ac3")) return "audio/ac3";
        if (lower.contains(".aiff") || lower.contains(".aif")) return "audio/aiff";
        if (lower.contains(".mka")) return "audio/x-matroska";
        if (lower.contains(".ra") || lower.contains(".ram")) return "audio/vnd.rn-realaudio";
        if (lower.contains(".mid") || lower.contains(".midi")) return "audio/midi";
        if (lower.contains(".opus")) return "audio/opus";
        if (lower.contains(".speex")) return "audio/speex";
        // 视频格式
        if (lower.contains(".mp4")) return "video/mp4";
        if (lower.contains(".mkv")) return "video/x-matroska";
        if (lower.contains(".avi")) return "video/avi";
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".flv")) return "video/x-flv";
        if (lower.contains(".wmv")) return "video/x-ms-wmv";
        if (lower.contains(".3gp")) return "video/3gpp";
        if (lower.contains(".ts")) return "video/mp2t";
        if (lower.contains(".asf")) return "video/x-ms-asf";
        if (lower.contains(".mov")) return "video/quicktime";
        if (lower.contains(".mpg") || lower.contains(".mpeg")) return "video/mpeg";
        if (lower.contains(".rmvb") || lower.contains(".rm")) return "video/vnd.rn-realvideo";
        if (lower.contains(".divx")) return "video/x-divx";
        if (lower.contains(".xvid")) return "video/x-xvid";
        if (lower.contains(".ogm") || lower.contains(".ogv")) return "video/x-ogm";
        if (lower.contains(".hls") || lower.contains(".m3u8")) return "application/vnd.apple.mpegurl";
        if (lower.contains(".vc1")) return "video/vc1";
        // 图片格式
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".tiff") || lower.contains(".tif")) return "image/tiff";
        if (lower.contains(".webp")) return "image/webp";
        // URI 关键词回退
        if (lower.contains("audio")) return "audio/mpeg";
        if (lower.contains("video")) return "video/mp4";
        return "application/octet-stream";
    }

    /** formatTime - 将毫秒时间格式化为 HH:MM:SS */
    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** parseTimeToMs - 将 HH:MM:SS 或 MM:SS 格式的时间字符串解析为毫秒 */
    private long parseTimeToMs(String time) {
        if (time == null || time.isEmpty()) return 0;
        try {
            String[] parts = time.split(":");
            if (parts.length == 3) {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                long seconds = Long.parseLong(parts[2].split("\\.")[0]);
                return (hours * 3600 + minutes * 60 + seconds) * 1000;
            } else if (parts.length == 2) {
                long minutes = Long.parseLong(parts[0]);
                long seconds = Long.parseLong(parts[1].split("\\.")[0]);
                return (minutes * 60 + seconds) * 1000;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Error parsing time: " + time, e);
        }
        return 0;
    }

    /**
     * stopPlayback - 停止播放并重置状态，但不清除监听器
     * 供 shutdown/restart 场景使用，避免影响其他协议（AirPlay）注册的监听器
     */
    public void stopPlayback() {
        stopInternal();
        stopPositionUpdate();
        currentState = TransportState.NO_MEDIA_PRESENT;
        mediaInfo.clear();
    }

    /** release - 完全释放：停止播放并清除所有监听器（仅在应用终止时调用） */
    public void release() {
        stopPlayback();
        listeners.clear();
    }
}
