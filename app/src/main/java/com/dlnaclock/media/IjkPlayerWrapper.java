package com.dlnaclock.media;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.net.URI;
import java.util.HashMap;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * IjkPlayerWrapper - IjkMediaPlayer 封装类（FFmpeg 内核，k0.8.8）
 * 提供与 MediaPlayerWrapper 相同的公开接口，用于视频播放
 * 支持硬件解码、错误重试（最多 2 次）、URI 编码切换
 */
public class IjkPlayerWrapper {

    private static final String TAG = "IjkPlayerWrapper";
    private static final int MAX_RETRY = 2;

    private IjkMediaPlayer ijkPlayer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private String currentDataSource;
    private boolean isPrepared = false;
    private boolean useEncodedUri = false;
    private boolean isVideoMode = false;
    private boolean useHardwareDecoder = true; // 默认硬件解码
    private Context context;
    private Surface pendingSurface;

    private MediaPlayerListener listener;

    /**
     * 播放事件回调接口（与 MediaPlayerWrapper.MediaPlayerListener 保持一致）
     */
    public interface MediaPlayerListener {
        void onPrepared();
        void onCompletion();
        void onError(int what, int extra);
        void onBufferingUpdate(int percent);
        void onVideoSizeChanged(int width, int height);
    }

    /**
     * 构造函数 - 加载 IJKPlayer native 库（仅首次调用生效）
     */
    public IjkPlayerWrapper(Context context) {
        this.context = context;
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
    }

    /** 设置播放事件回调监听器 */
    public void setListener(MediaPlayerListener listener) {
        this.listener = listener;
    }

    /** 设置媒体数据源并开始准备播放 */
    public void setDataSource(String dataSource) {
        Log.i(TAG, "setDataSource: " + dataSource);
        this.currentDataSource = dataSource;
        this.retryCount = 0;
        this.useEncodedUri = false;
        preparePlayer();
    }

    private void preparePlayer() {
        release();

        try {
            ijkPlayer = new IjkMediaPlayer();

            // 根据用户选择启用硬件/软件解码
            if (useHardwareDecoder) {
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1);
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1);
            } else {
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 0);
                ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 0);
            }

            // 网络超时 15 秒（单位：微秒）
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 15000000L);

            // User-Agent（避免被 HTTP 服务器拒绝）
            ijkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent",
                    "Android/" + android.os.Build.VERSION.RELEASE + " DlnaClock/1.0");

            // 如果 surface 已在 player 创建前设置，立即绑定
            if (pendingSurface != null) {
                ijkPlayer.setSurface(pendingSurface);
            }

            // 设置事件回调
            ijkPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(IMediaPlayer mp) {
                    isPrepared = true;
                    retryCount = 0;
                    Log.i(TAG, "onPrepared: duration=" + ijkPlayer.getDuration() + "ms");
                    if (listener != null) listener.onPrepared();
                }
            });

            ijkPlayer.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(IMediaPlayer mp) {
                    if (listener != null) listener.onCompletion();
                }
            });

            ijkPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(IMediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "IjkPlayer error: " + what + ", " + extra
                            + ", encoded=" + useEncodedUri);
                    isPrepared = false;
                    if (retryCount < MAX_RETRY) {
                        retryCount++;
                        if (retryCount == 1) {
                            useEncodedUri = true;
                            Log.i(TAG, "Retrying with encoded URI... attempt " + retryCount);
                        } else {
                            Log.i(TAG, "Retrying... attempt " + retryCount);
                        }
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                preparePlayer();
                            }
                        }, 1000);
                        return true;
                    }
                    if (listener != null) listener.onError(what, extra);
                    return true;
                }
            });

            ijkPlayer.setOnBufferingUpdateListener(new IMediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(IMediaPlayer mp, int percent) {
                    if (listener != null) listener.onBufferingUpdate(percent);
                }
            });

            ijkPlayer.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
                    isVideoMode = (width > 0 && height > 0);
                    if (listener != null) listener.onVideoSizeChanged(width, height);
                }
            });

            // 构建 URI（重试时切换为编码后的 URI）
            Uri uri = useEncodedUri ? encodeUriPath(currentDataSource) : Uri.parse(currentDataSource);
            Log.i(TAG, "setDataSource URI: " + uri + " (encoded=" + useEncodedUri + ")");

            if (context != null) {
                ijkPlayer.setDataSource(context, uri, new HashMap<String, String>());
            } else {
                ijkPlayer.setDataSource(uri.toString());
            }

            Log.i(TAG, "prepareAsync() called for: " + currentDataSource);
            ijkPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Error preparing IjkPlayer", e);
            if (retryCount < MAX_RETRY) {
                retryCount++;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        preparePlayer();
                    }
                }, 1000);
            } else {
                if (listener != null) listener.onError(-1, -1);
            }
        }
    }

    /**
     * encodeUriPath - 对 URI 路径中的特殊字符做百分号编码
     * 解决 BubbleUPnP 等控制点生成的 base64 URL 中裸 = 号导致服务器拒绝的问题
     */
    private Uri encodeUriPath(String uriString) {
        try {
            URI uri = new URI(uriString);
            String path = uri.getRawPath();
            if (path != null && (path.contains("=") || path.contains("+"))) {
                String[] segments = path.split("/", -1);
                StringBuilder encodedPath = new StringBuilder();
                for (int i = 0; i < segments.length; i++) {
                    if (i > 0) encodedPath.append("/");
                    if (!segments[i].isEmpty()) {
                        encodedPath.append(Uri.encode(segments[i]));
                    }
                }
                String query = uri.getRawQuery();
                String fragment = uri.getRawFragment();
                String result = uri.getScheme() + "://" + uri.getAuthority() + encodedPath;
                if (query != null) result += "?" + query;
                if (fragment != null) result += "#" + fragment;
                Log.d(TAG, "Encoded URI path: " + result);
                return Uri.parse(result);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to encode URI path, using original", e);
        }
        return Uri.parse(uriString);
    }

    /** 设置渲染目标 Surface */
    public void setSurface(Surface surface) {
        this.pendingSurface = surface;
        if (ijkPlayer != null) {
            ijkPlayer.setSurface(surface);
        }
    }

    /** 开始播放 */
    public void start() {
        if (ijkPlayer != null && isPrepared) {
            try {
                ijkPlayer.start();
                Log.i(TAG, "IjkPlayer started, isPlaying=" + ijkPlayer.isPlaying());
            } catch (Exception e) {
                Log.e(TAG, "Error starting IjkPlayer", e);
            }
        } else {
            Log.w(TAG, "start() ignored: ijkPlayer=" + (ijkPlayer != null) + ", isPrepared=" + isPrepared);
        }
    }

    /** 暂停播放 */
    public void pause() {
        if (ijkPlayer != null && isPrepared && ijkPlayer.isPlaying()) {
            ijkPlayer.pause();
        }
    }

    /** 停止播放 */
    public void stop() {
        if (ijkPlayer != null) {
            try {
                if (ijkPlayer.isPlaying()) {
                    ijkPlayer.stop();
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /** 跳转到指定位置 */
    public void seekTo(int msec) {
        if (ijkPlayer != null && isPrepared) {
            ijkPlayer.seekTo(msec);
        }
    }

    /** 获取当前播放位置（毫秒） */
    public int getCurrentPosition() {
        if (ijkPlayer != null && isPrepared) {
            try {
                return (int) ijkPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /** 获取媒体总时长（毫秒） */
    public int getDuration() {
        if (ijkPlayer != null && isPrepared) {
            try {
                return (int) ijkPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /** 是否正在播放 */
    public boolean isPlaying() {
        if (ijkPlayer != null && isPrepared) {
            try {
                return ijkPlayer.isPlaying();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /** 是否已准备 */
    public boolean isPrepared() {
        return isPrepared;
    }

    /** 是否为视频模式 */
    public boolean isVideoMode() {
        return isVideoMode;
    }

    /** 获取底层 IjkMediaPlayer 实例 */
    public IjkMediaPlayer getIjkMediaPlayer() {
        return ijkPlayer;
    }

    /** 是否使用硬件解码 */
    public boolean isHardwareDecoder() {
        return useHardwareDecoder;
    }

    /**
     * switchDecoder - 切换硬件/软件解码器
     * 从当前位置重新 seek 并恢复播放，确保新解码器生效
     */
    public void switchDecoder(boolean hardware) {
        if (this.useHardwareDecoder == hardware) return;
        this.useHardwareDecoder = hardware;

        // 记录当前位置和播放状态
        final int savedPosition = getCurrentPosition();
        final boolean wasPlaying = isPlaying();

        // 重新准备播放器（使用新解码器）
        preparePlayer();

        // 在 onPrepared 回调后 seek 到保存的位置并恢复播放
        // 由于 preparePlayer 是异步的，这里通过 listener 的 onPrepared 处理
        // 我们在 setListener 中包装一层，在 onPrepared 后自动 seek
        final MediaPlayerListener originalListener = this.listener;
        this.listener = new MediaPlayerListener() {
            @Override
            public void onPrepared() {
                // seek 到保存的位置
                if (ijkPlayer != null && isPrepared) {
                    ijkPlayer.seekTo(savedPosition);
                }
                // 恢复播放
                if (wasPlaying) {
                    start();
                }
                // 恢复原始 listener
                listener = originalListener;
                if (originalListener != null) {
                    originalListener.onPrepared();
                }
            }

            @Override
            public void onCompletion() {
                if (originalListener != null) originalListener.onCompletion();
            }

            @Override
            public void onError(int what, int extra) {
                if (originalListener != null) originalListener.onError(what, extra);
            }

            @Override
            public void onBufferingUpdate(int percent) {
                if (originalListener != null) originalListener.onBufferingUpdate(percent);
            }

            @Override
            public void onVideoSizeChanged(int width, int height) {
                if (originalListener != null) originalListener.onVideoSizeChanged(width, height);
            }
        };
    }

    /**
     * getMediaInfoText - 获取视频/解码详细信息文本
     */
    public String getMediaInfoText() {
        if (ijkPlayer == null || !isPrepared) return "播放器未就绪";

        StringBuilder sb = new StringBuilder();
        sb.append("=== 视频信息 ===\n");

        // 分辨率
        int w = ijkPlayer.getVideoWidth();
        int h = ijkPlayer.getVideoHeight();
        sb.append("分辨率: ").append(w).append("×").append(h).append("\n");

        // 时长
        long duration = ijkPlayer.getDuration();
        long totalSec = duration / 1000;
        sb.append("时长: ").append(String.format(java.util.Locale.getDefault(),
                "%d:%02d:%02d", totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)).append("\n");

        // 码率
        long bitRate = ijkPlayer.getBitRate();
        if (bitRate > 0) {
            sb.append("码率: ").append(bitRate / 1000).append(" kbps\n");
        }

        // 解码信息
        sb.append("\n=== 解码信息 ===\n");
        sb.append("解码器: ").append(useHardwareDecoder ? "硬解 (MediaCodec)" : "软解 (AVCodec)").append("\n");

        // 帧率
        float decodeFps = ijkPlayer.getVideoDecodeFramesPerSecond();
        float outputFps = ijkPlayer.getVideoOutputFramesPerSecond();
        if (decodeFps > 0) {
            sb.append("解码帧率: ").append(String.format(java.util.Locale.getDefault(), "%.1f fps", decodeFps)).append("\n");
        }
        if (outputFps > 0) {
            sb.append("输出帧率: ").append(String.format(java.util.Locale.getDefault(), "%.1f fps", outputFps)).append("\n");
        }

        // 通过 getMediaInfo 获取编解码器详情
        try {
            tv.danmaku.ijk.media.player.MediaInfo mediaInfo = ijkPlayer.getMediaInfo();
            if (mediaInfo != null) {
                if (mediaInfo.mVideoDecoder != null && !mediaInfo.mVideoDecoder.isEmpty()) {
                    sb.append("视频编码: ").append(mediaInfo.mVideoDecoder);
                    if (mediaInfo.mVideoDecoderImpl != null && !mediaInfo.mVideoDecoderImpl.isEmpty()) {
                        sb.append(" (").append(mediaInfo.mVideoDecoderImpl).append(")");
                    }
                    sb.append("\n");
                }
                if (mediaInfo.mAudioDecoder != null && !mediaInfo.mAudioDecoder.isEmpty()) {
                    sb.append("音频编码: ").append(mediaInfo.mAudioDecoder);
                    if (mediaInfo.mAudioDecoderImpl != null && !mediaInfo.mAudioDecoderImpl.isEmpty()) {
                        sb.append(" (").append(mediaInfo.mAudioDecoderImpl).append(")");
                    }
                    sb.append("\n");
                }
                // 采样率
                if (mediaInfo.mMeta != null && mediaInfo.mMeta.mStreams != null) {
                    for (tv.danmaku.ijk.media.player.IjkMediaMeta.IjkStreamMeta stream : mediaInfo.mMeta.mStreams) {
                        if (stream.mSampleRate > 0) {
                            sb.append("采样率: ").append(stream.mSampleRate).append(" Hz\n");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("(获取编码详情失败)\n");
        }

        return sb.toString();
    }

    /** 释放播放器资源 */
    public void release() {
        if (ijkPlayer != null) {
            try {
                ijkPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            ijkPlayer = null;
            isPrepared = false;
        }
    }
}
