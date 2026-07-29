package com.dlnaclock.airplay;

import android.content.Context;
import android.util.Log;

import com.dlnaclock.App;
import com.dlnaclock.dlna.avt.AVTransportService;
import com.dlnaclock.dlna.avt.MediaInfo;
import com.dlnaclock.dlna.avt.TransportState;
import com.dlnaclock.util.NetworkUtil;
import com.dlnaclock.util.PreferenceHelper;

/**
 * AirPlayManager - AirPlay 总协调器（单例模式）
 * 管理 mDNS 服务注册、HTTP 服务器生命周期
 * 桥接 AirPlay 播放事件到共享的 AVTransportService 状态机
 * 复用 DlnaManager.launchPlayerActivity() 启动播放器
 */
public class AirPlayManager {

    private static final String TAG = "AirPlayManager";
    private static AirPlayManager instance; // 单例实例

    private Context context;
    private MdnsService mdnsService;
    private AirPlayHttpServer httpServer;
    private AirPlayHttpHandler httpHandler;
    private AVTransportService avtService;   // 共享的播放状态机引用
    private volatile boolean playerLaunched = false;  // 是否已启动播放器（防止重复启动）
    private float pendingStartPosition = -1f; // 挂起的起始位置（等播放器就绪后执行 seek）

    private String deviceId;  // 设备 MAC 或随机生成的 deviceid
    private String deviceName; // 设备显示名称

    /** AirPlayManager - 私有构造函数（单例模式） */
    private AirPlayManager() {}

    /** getInstance - 获取单例实例 */
    public static synchronized AirPlayManager getInstance() {
        if (instance == null) {
            instance = new AirPlayManager();
        }
        return instance;
    }

    /**
     * init - 初始化 AirPlay 管理器
     * 获取共享的 AVTransportService 引用，并注册 AVT 事件监听器
     */
    public void init(Context context) {
        this.context = context.getApplicationContext();

        // 获取共享的 AVTransportService（通过 App → DlnaManager 链路）
        if (App.getInstance() != null && App.getInstance().getDlnaManager() != null) {
            this.avtService = App.getInstance().getDlnaManager().getAvtService();
        }

        // 监听 AVT 状态变化（用于响应 playback-info 查询、重置 playerLaunched 标志）
        if (avtService != null) {
            avtService.addListener(avtListener);
        }

        Log.i(TAG, "AirPlayManager initialized, avtService=" + (avtService != null));
    }

    /**
     * start - 启动 AirPlay 服务（mDNS + HTTP）
     * 由 AirPlayService 在 onStartCommand 中调用
     */
    public void start() {
        String localIp = NetworkUtil.getLocalIpAddress(context);
        deviceName = PreferenceHelper.getAirPlayDeviceName();

        Log.i(TAG, "Starting AirPlay services on IP: " + localIp + ", name: " + deviceName);

        // 1. 启动 mDNS 服务（Bonjour 注册）
        mdnsService = new MdnsService(context);
        mdnsService.start(localIp, deviceName);

        // 2. 启动 HTTP 服务器
        httpHandler = new AirPlayHttpHandler(this);
        httpServer = new AirPlayHttpServer(AirPlayConstants.AIRPLAY_PORT, httpHandler);
        try {
            httpServer.start();
            Log.i(TAG, "AirPlay HTTP server started on port " + AirPlayConstants.AIRPLAY_PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AirPlay HTTP server", e);
        }

        // 确保 AVT 监听器已注册（shutdown 后重新 start 时需要）
        if (avtService != null) {
            avtService.removeListener(avtListener); // 先移除避免重复
            avtService.addListener(avtListener);
        }
    }

    /**
     * shutdown - 关闭 AirPlay 服务，释放所有资源
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down AirPlay services");

        if (mdnsService != null) {
            mdnsService.stop();
            mdnsService = null;
        }

        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }

        if (avtService != null) {
            avtService.removeListener(avtListener);
        }

        playerLaunched = false;
        pendingStartPosition = -1f;
    }

    /** isRunning - 返回 AirPlay 服务是否正在运行 */
    public boolean isRunning() {
        return mdnsService != null && mdnsService.isRunning();
    }

    /** getDeviceId - 获取设备 deviceid（供 server-info 响应使用） */
    public String getDeviceId() {
        if (deviceId == null) {
            // 尝试从 MdnsService 获取，否则生成默认值
            deviceId = "AA:BB:CC:DD:EE:FF";
        }
        return deviceId;
    }

    /** getDeviceName - 获取设备显示名称（供 server-info 响应使用） */
    public String getDeviceName() {
        return deviceName != null ? deviceName : PreferenceHelper.getAirPlayDeviceName();
    }

    // ========================================================================
    // === 播放事件处理（由 AirPlayHttpHandler 从 HTTP 线程调用）===
    // AVTransportService 内部已有 mainHandler.post 机制保证 MediaPlayer 在主线程操作
    // ========================================================================

    /**
     * onPlay - 处理 AirPlay /play 请求
     * 设置媒体 URI 到 AVTransportService，启动播放，并拉起播放器 Activity
     * @param url           媒体 URL（Content-Location）
     * @param startPosition 起始位置（0.0-1.0，AirPlay 协议中的百分比）
     */
    public void onPlay(String url, float startPosition) {
        Log.i(TAG, "AirPlay play: " + url + " at " + startPosition);
        if (avtService == null) {
            Log.w(TAG, "AVTransportService not available");
            return;
        }

        // 设置 URI（带 AirPlay 来源标记，覆盖 activeSource）
        avtService.setAVTransportURI(url, null, "AirPlay");

        // 播放
        avtService.play("1");

        // 保存 pending seek，等播放器开始播放后再执行
        if (startPosition > 0) {
            this.pendingStartPosition = startPosition;
        }

        // 启动播放器 Activity（复用 DlnaManager 的逻辑）
        if (!playerLaunched) {
            playerLaunched = true;
            MediaInfo mi = avtService.getMediaInfoObject();
            if (App.getInstance() != null && App.getInstance().getDlnaManager() != null) {
                App.getInstance().getDlnaManager().launchPlayerActivity(mi);
            }
        }
    }

    /**
     * onStop - 处理 AirPlay /stop 请求
     * 停止当前播放并重置 playerLaunched 标志
     */
    public void onStop() {
        Log.i(TAG, "AirPlay stop");
        if (avtService == null) return;
        avtService.stop();
        playerLaunched = false;
    }

    /**
     * onScrub - 处理 AirPlay /scrub?position=xx 请求
     * 跳转到指定秒数的播放位置
     * @param positionSeconds 目标位置（秒）
     */
    public void onScrub(float positionSeconds) {
        if (positionSeconds < 0) positionSeconds = 0;
        Log.i(TAG, "AirPlay scrub: " + positionSeconds);
        if (avtService == null) return;
        long positionMs = (long) (positionSeconds * 1000);
        String timeStr = formatTime(positionMs);
        avtService.seek("REL_TIME", timeStr);
    }

    /**
     * onRate - 处理 AirPlay /rate?value=xx 请求
     * value=0 表示暂停，value>=1 表示播放
     * @param value 速率值（0.0 或 1.0）
     */
    public void onRate(float value) {
        Log.i(TAG, "AirPlay rate: " + value);
        if (avtService == null) return;
        if (value == 0) {
            avtService.pause();
        } else {
            avtService.play("1");
        }
    }

    // ========================================================================
    // === 播放状态查询（供 playback-info 响应使用）===
    // ========================================================================

    /**
     * getPlaybackState - 获取当前播放状态字符串
     * 将 AVTransport 状态映射为 AirPlay 协议的状态名
     * @return "playing"、"paused" 或 "stopped"
     */
    public String getPlaybackState() {
        if (avtService == null) return "stopped";
        switch (avtService.getCurrentState()) {
            case PLAYING:
                return "playing";
            case PAUSED_PLAYBACK:
                return "paused";
            case STOPPED:
            case NO_MEDIA_PRESENT:
                return "stopped";
            default:
                return "stopped";
        }
    }

    /** getRate - 获取当前播放速率（playing=1.0，其他=0.0） */
    public float getRate() {
        return "playing".equals(getPlaybackState()) ? 1.0f : 0.0f;
    }

    /** getPositionSeconds - 获取当前播放位置（秒） */
    public double getPositionSeconds() {
        return avtService != null ? avtService.getCurrentPositionMs() / 1000.0 : 0;
    }

    /** getDurationSeconds - 获取媒体总时长（秒） */
    public double getDurationSeconds() {
        return avtService != null ? avtService.getDurationMs() / 1000.0 : 0;
    }

    // ========================================================================
    // === AVT 事件监听器 ===
    // ========================================================================

    /** avtListener - 监听 AVTransportService 状态变化，用于重置 playerLaunched 标志 */
    private AVTransportService.AVTransportListener avtListener = new AVTransportService.AVTransportListener() {
        @Override
        public void onUriSet(MediaInfo mediaInfo) {
            // URI 变化由 AirPlayManager 自身驱动，无需额外处理
        }

        @Override
        public void onStateChanged(TransportState state) {
            // 停止或无媒体时重置标志，允许下次 onPlay 重新启动播放器
            if (state == TransportState.STOPPED || state == TransportState.NO_MEDIA_PRESENT) {
                playerLaunched = false;
            }

            // 播放器开始播放后执行挂起的 seek
            if (state == TransportState.PLAYING && pendingStartPosition > 0) {
                long dur = avtService.getDurationMs();
                if (dur > 0) {
                    long positionMs = (long) (pendingStartPosition * dur);
                    avtService.seek("REL_TIME", formatTime(positionMs));
                    pendingStartPosition = -1f;
                }
            }
        }

        @Override
        public void onPlaybackPositionChanged(long positionMs, long durationMs) {
            // 进度由 AVT 内部管理，AirPlayManager 通过 getPositionSeconds() 按需查询
        }

        @Override
        public void onMediaCompleted() {
            Log.i(TAG, "Media completed, resetting playerLaunched");
            playerLaunched = false;
        }

        @Override
        public void onError(int what, int extra) {
            Log.w(TAG, "Player error: what=" + what + ", extra=" + extra);
            playerLaunched = false;
        }
    };

    // ========================================================================
    // === 辅助方法 ===
    // ========================================================================

    /**
     * formatTime - 将毫秒时间格式化为 HH:MM:SS（与 AVTransportService 中一致）
     * @param ms 毫秒时间
     * @return 格式化后的时间字符串
     */
    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
