package com.dlnaclock.media;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * MediaPlayerWrapper - MediaPlayer 封装类
 * 提供统一的音频/视频播放接口，支持错误重试（最多 2 次）
 * 封装 prepare/start/stop/release 生命周期管理
 */
public class MediaPlayerWrapper {

    private static final String TAG = "MediaPlayerWrapper";
    private static final int MAX_RETRY = 2; // 最大重试次数

    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private String currentDataSource;
    private boolean isPrepared = false;
    private boolean useEncodedUri = false; // 重试时切换为编码后的 URI
    private boolean isVideoMode = false;
    private Context context;

    private MediaPlayerListener listener;

    /** MediaPlayerWrapper - 构造函数
     * @param context 用于 setDataSource(Context, Uri) 以支持 HTTP 重定向和 content:// URI
     */
    public MediaPlayerWrapper(Context context) {
        this.context = context;
    }

    /** MediaPlayerListener - 播放事件回调接口 */
    public interface MediaPlayerListener {
        void onPrepared();
        void onCompletion();
        void onError(int what, int extra);
        void onBufferingUpdate(int percent);
        void onVideoSizeChanged(int width, int height);
    }

    /** setListener - 设置播放事件回调监听器 */
    public void setListener(MediaPlayerListener listener) {
        this.listener = listener;
    }

    /** setDataSource - 设置媒体数据源并开始准备播放 */
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
            mediaPlayer = new MediaPlayer();
            // 明确设置音频流类型（确保通过 STREAM_MUSIC 通道输出）
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isPrepared = true;
                    retryCount = 0;
                    Log.i(TAG, "onPrepared: duration=" + mp.getDuration() + "ms");
                    if (listener != null) listener.onPrepared();
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (listener != null) listener.onCompletion();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra
                            + ", encoded=" + useEncodedUri);
                    isPrepared = false;
                    if (retryCount < MAX_RETRY) {
                        retryCount++;
                        // 第一次失败后切换为编码后的 URI 重试
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

            mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    if (listener != null) listener.onBufferingUpdate(percent);
                }
            });

            mediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    isVideoMode = (width > 0 && height > 0);
                    if (listener != null) listener.onVideoSizeChanged(width, height);
                }
            });

            // 构建请求头（User-Agent 避免被某些 HTTP 服务器拒绝）
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Android/" + android.os.Build.VERSION.RELEASE + " DlnaClock/1.0");
            headers.put("Icy-Metadata", "1");

            // 对 URI 路径中的特殊字符（如 base64 的 =）做百分号编码
            // 某些控制点（如 BubbleUPnP）生成的 URL 包含未编码的 base64 字符
            Uri uri = useEncodedUri ? encodeUriPath(currentDataSource) : Uri.parse(currentDataSource);
            Log.i(TAG, "setDataSource URI: " + uri + " (encoded=" + useEncodedUri + ")");

            if (context != null) {
                mediaPlayer.setDataSource(context, uri, headers);
            } else {
                mediaPlayer.setDataSource(uri.toString());
            }
            Log.i(TAG, "prepareAsync() called for: " + currentDataSource);
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Error preparing MediaPlayer", e);
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
     * encodeUriPath - 对 URI 路径组件中的特殊字符做百分号编码
     * 保留 scheme/host/port/query/fragment 不变，仅编码 path 中的 = + 等字符
     * 解决 BubbleUPnP 等控制点生成的 base64 URL 中裸 = 号导致服务器拒绝的问题
     */
    private Uri encodeUriPath(String uriString) {
        try {
            URI uri = new URI(uriString);
            String path = uri.getRawPath();
            if (path != null && (path.contains("=") || path.contains("+"))) {
                // 对路径中每个 segment 分别编码
                String[] segments = path.split("/", -1);
                StringBuilder encodedPath = new StringBuilder();
                for (int i = 0; i < segments.length; i++) {
                    if (i > 0) encodedPath.append("/");
                    if (!segments[i].isEmpty()) {
                        // Uri.encode 保留 [a-zA-Z0-9_.!~*'()-] 不编码
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

    public void setSurface(Surface surface) {
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(surface);
        }
    }

    public void start() {
        if (mediaPlayer != null && isPrepared) {
            try {
                mediaPlayer.start();
                Log.i(TAG, "MediaPlayer started, isPlaying=" + mediaPlayer.isPlaying());
            } catch (Exception e) {
                Log.e(TAG, "Error starting MediaPlayer", e);
            }
        } else {
            Log.w(TAG, "start() ignored: mediaPlayer=" + (mediaPlayer != null) + ", isPrepared=" + isPrepared);
        }
    }

    public void pause() {
        if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(msec);
        }
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public boolean isPlaying() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.isPlaying();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public boolean isPrepared() {
        return isPrepared;
    }

    public boolean isVideoMode() {
        return isVideoMode;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void release() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            mediaPlayer = null;
            isPrepared = false;
        }
    }
}
