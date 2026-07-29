package com.dlnaclock.dlna;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.dlnaclock.util.LogUtil;

import com.dlnaclock.dlna.avt.AVTransportService;
import com.dlnaclock.dlna.avt.MediaInfo;
import com.dlnaclock.dlna.avt.TransportState;
import com.dlnaclock.dlna.device.DmrDevice;
import com.dlnaclock.dlna.gena.GenaManager;
import com.dlnaclock.dlna.rc.CastAction;
import com.dlnaclock.dlna.rc.ConnectionManagerService;
import com.dlnaclock.dlna.rc.RenderControl;
import com.dlnaclock.dlna.rc.RenderingControlService;
import com.dlnaclock.dlna.server.DlnaHttpServer;
import com.dlnaclock.dlna.soap.SoapHandler;
import com.dlnaclock.dlna.ssdp.SsdpClient;
import com.dlnaclock.dlna.ssdp.SsdpMessage;
import com.dlnaclock.media.MusicPlayerActivity;
import com.dlnaclock.media.VideoPlayerActivity;
import com.dlnaclock.util.IconGenerator;
import com.dlnaclock.util.NetworkUtil;
import com.dlnaclock.util.PreferenceHelper;

import java.net.BindException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DlnaManager - DLNA 总管理器（单例）
 * 协调 SSDP 发现、HTTP 服务器、SOAP 控制、AVTransport 状态机
 * 负责事件分发和播放器 Activity 启动
 */
public class DlnaManager {

    private static final String TAG = "DlnaManager";
    private static DlnaManager instance; // 单例实例

    private Context context;
    private DmrDevice device;                // DMR 设备模型
    private SsdpClient ssdpClient;           // SSDP 发现客户端
    private DlnaHttpServer httpServer;       // HTTP 服务器
    private AVTransportService avtService;   // AVTransport 播放状态机
    private RenderingControlService rcService;   // 渲染控制（音量）
    private ConnectionManagerService cmService;  // 连接管理
    private SoapHandler soapHandler;             // SOAP 请求处理器
    private GenaManager genaManager;             // GENA 事件管理器
    private Handler mainHandler = new Handler(Looper.getMainLooper()); // 主线程 Handler
    private volatile boolean running = false; // 服务运行状态标志

    private CopyOnWriteArrayList<DlnaEventListener> eventListeners = new CopyOnWriteArrayList<>(); // UI 层事件监听器
    private RenderControl renderControl; // 实际播放控制器（由播放器 Activity 设置）
    private boolean playerLaunched = false; // 当前媒体是否已启动播放器（防止重复启动）

    /** DlnaEventListener - DLNA 事件监听接口，供 ScreenSaverActivity 等 UI 层实现 */
    public interface DlnaEventListener {
        void onMediaUriSet(MediaInfo mediaInfo);
        void onTransportStateChanged(TransportState state);
        void onPlaybackPositionChanged(long positionMs, long durationMs);
        void onMediaCompleted();
        void onDlnaError(int what, int extra);
    }

    /** DlnaManager - 私有构造函数（单例模式） */
    private DlnaManager() {}

    /** getInstance - 获取单例实例 */
    public static synchronized DlnaManager getInstance() {
        if (instance == null) {
            instance = new DlnaManager();
        }
        return instance;
    }

    /**
     * init - 初始化 DLNA 管理器
     * 创建设备模型、三个服务实例、SOAP 处理器，注册 AVT 事件监听
     */
    public void init(Context context) {
        this.context = context.getApplicationContext();

        // Create device with persistent UDN
        device = new DmrDevice();
        device.setUdn(PreferenceHelper.getUdn());
        LogUtil.i(TAG, "Using persistent UDN: " + device.getUdn());
        device.setFriendlyName(PreferenceHelper.getDeviceName());
        device.setManufacturer("DlnaClock");
        device.setModelName("DlnaClock ScreenSaver");
        device.setModelNumber("1.0");
        device.setModelDescription("DLNA Media Renderer with Clock ScreenSaver");

        // Create services
        avtService = new AVTransportService();
        rcService = new RenderingControlService(context);
        cmService = new ConnectionManagerService();
        genaManager = new GenaManager(avtService, rcService);
        soapHandler = new SoapHandler(avtService, rcService, cmService);

        // AVT 监听器在 start() 中注册，确保 restart() 后也能重新注册
        registerAvtListeners();
    }

    /** start - 启动 DLNA 服务（HTTP 服务器 + SSDP 客户端），含重入保护和端口回退 */
    public synchronized void start() {
        // 重入保护：如果已在运行，先关闭再启动
        if (running) {
            LogUtil.w(TAG, "start() called while already running, shutting down first");
            shutdown();
        }

        // 重新注册 AVT 监听器（shutdown 会停止播放但不清除监听器）
        registerAvtListeners();

        // 重新创建 GenaManager（shutdown 中 release 会关闭 executor，需要新建）
        genaManager = new GenaManager(avtService, rcService);

        String localIp = NetworkUtil.getLocalIpAddress(context);
        LogUtil.i(TAG, "Starting DLNA services on IP: " + localIp);

        // Update device name from preferences
        device.setFriendlyName(PreferenceHelper.getDeviceName());

        // 生成设备图标（用于 description.xml 中的 iconList）
        byte[] iconBytes = null;
        try {
            iconBytes = IconGenerator.generateIcon();
            LogUtil.d(TAG, "Generated device icon: " + (iconBytes != null ? iconBytes.length : 0) + " bytes");
        } catch (Exception e) {
            LogUtil.w(TAG, "Failed to generate device icon", e);
        }

        // Start HTTP server with port fallback (49152-49162)
        int httpPort = -1;
        for (int attempt = 0; attempt <= DlnaHttpServer.MAX_PORT_RETRY; attempt++) {
            int port = DlnaHttpServer.DEFAULT_HTTP_PORT + attempt;
            try {
                httpServer = new DlnaHttpServer(device, localIp, soapHandler, genaManager, iconBytes, port);
                httpServer.start();
                httpPort = httpServer.getPort();
                LogUtil.i(TAG, "HTTP server started on port " + httpPort);
                break;
            } catch (BindException e) {
                LogUtil.w(TAG, "Port " + port + " already in use, trying next port");
                httpServer = null;
            } catch (Exception e) {
                LogUtil.e(TAG, "Failed to start HTTP server on port " + port, e);
                httpServer = null;
                break;
            }
        }

        if (httpPort < 0) {
            LogUtil.e(TAG, "Failed to start HTTP server after port retry, aborting start()");
            return;
        }

        // Start SSDP client
        ssdpClient = new SsdpClient(context);
        ssdpClient.setListener(new SsdpClient.SsdpListener() {
            @Override
            public void onMSearchReceived(String remoteIp, int remotePort, SsdpMessage message) {
                ssdpClient.sendSearchResponse(remoteIp, remotePort, message);
            }

            @Override
            public void onNotifyReceived(String remoteIp, int remotePort, SsdpMessage message) {
                // We are a DMR, typically we don't need to handle incoming notifications
                // But log them for debugging
                LogUtil.d(TAG, "Received NOTIFY from " + remoteIp);
            }
        });
        ssdpClient.start(localIp, httpPort, device.getUdn(), device.getDeviceType());
        running = true;
    }

    /** shutdown - 关闭所有 DLNA 服务（停止播放但不清除 AVT 监听器，避免影响 AirPlay） */
    public synchronized void shutdown() {
        LogUtil.i(TAG, "Shutting down DLNA services");
        running = false;
        playerLaunched = false;

        if (ssdpClient != null) {
            ssdpClient.stop();
            ssdpClient = null;
        }

        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }

        if (avtService != null) {
            // 使用 stopPlayback 而非 release，避免清除共享 AVT 的所有监听器（含 AirPlay）
            avtService.stopPlayback();
        }

        if (genaManager != null) {
            // release 关闭 notifyExecutor，start() 中会重新创建 GenaManager
            genaManager.release();
            genaManager = null;
        }
    }

    /** restart - 重启 DLNA 服务（网络变化时调用），含重入保护 */
    public synchronized void restart() {
        LogUtil.i(TAG, "Restarting DLNA services");
        shutdown();
        // 短暂等待旧资源释放
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        start();
    }

    // Event listener management
    /** addEventListener - 添加 DLNA 事件监听器 */
    public void addEventListener(DlnaEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    /** removeEventListener - 移除 DLNA 事件监听器 */
    public void removeEventListener(DlnaEventListener listener) {
        eventListeners.remove(listener);
    }

    private void notifyMediaUriSet(MediaInfo mediaInfo) {
        for (DlnaEventListener l : eventListeners) {
            l.onMediaUriSet(mediaInfo);
        }
    }

    private void notifyTransportStateChanged(TransportState state) {
        for (DlnaEventListener l : eventListeners) {
            l.onTransportStateChanged(state);
        }
    }

    private void notifyPlaybackPositionChanged(long positionMs, long durationMs) {
        for (DlnaEventListener l : eventListeners) {
            l.onPlaybackPositionChanged(positionMs, durationMs);
        }
    }

    private void notifyMediaCompleted() {
        for (DlnaEventListener l : eventListeners) {
            l.onMediaCompleted();
        }
    }

    private void notifyDlnaError(int what, int extra) {
        for (DlnaEventListener l : eventListeners) {
            l.onDlnaError(what, extra);
        }
    }

    // Getters
    public AVTransportService getAvtService() { return avtService; }
    public RenderingControlService getRcService() { return rcService; }
    public DmrDevice getDevice() { return device; }
    public boolean isRunning() { return running; }
    /** getHttpPort - 获取 HTTP 服务器实际使用的端口，未运行时返回 -1 */
    public int getHttpPort() { return httpServer != null ? httpServer.getPort() : -1; }

    /** setRenderControl - 设置播放控制器（播放器 Activity 启动时调用） */
    public void setRenderControl(RenderControl control) {
        this.renderControl = control;
        avtService.setRenderControl(control);
    }

    /** clearRenderControl - 清除播放控制器（播放器 Activity 销毁时调用） */
    public void clearRenderControl() {
        this.renderControl = null;
        avtService.setRenderControl(null);
    }

    /** clearRenderControlIfOwner - 条件清除：仅当当前 renderControl 是指定实例时才清除
     *  防止旧 Activity 销毁时误清新 Activity 设置的 renderControl */
    public void clearRenderControlIfOwner(RenderControl control) {
        if (this.renderControl == control) {
            clearRenderControl();
        }
    }

    /**
     * registerAvtListeners - 注册 AVTransport 事件监听器
     * 在 init() 和 start() 中调用，确保 restart() 后监听器不丢失
     * （shutdown → avtService.release() 会清除所有监听器）
     */
    private void registerAvtListeners() {
        // 先清除旧监听器，避免重复注册（addListener 内部有去重，但显式清除更安全）
        // 注意：release() 已经清除了，这里主要是防止 start() 被多次调用
        avtService.removeListener(avtListener);
        avtService.addListener(avtListener);
        LogUtil.d(TAG, "AVT listeners registered, listener count=" + avtService.getListenerCount());
    }

    /** AVTransport 事件监听器实例（提取为字段，方便移除和重新注册） */
    private AVTransportService.AVTransportListener avtListener = new AVTransportService.AVTransportListener() {
        @Override
        public void onUriSet(final MediaInfo mediaInfo) {
            LogUtil.d(TAG, "onUriSet callback received, playerLaunched=" + playerLaunched
                    + ", uri=" + (mediaInfo != null ? mediaInfo.getCurrentUri() : "null"));
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 新 URI 到来时立即重置 playerLaunched，允许启动新的播放器
                        playerLaunched = false;

                        if (mediaInfo != null
                                && mediaInfo.getCurrentUri() != null
                                && !mediaInfo.getCurrentUri().isEmpty()) {
                            playerLaunched = true;
                            launchPlayerActivity(mediaInfo);
                        } else {
                            LogUtil.w(TAG, "onUriSet: invalid mediaInfo or empty URI");
                        }
                        notifyMediaUriSet(mediaInfo);
                    } catch (Exception e) {
                        LogUtil.e(TAG, "Error in onUriSet handler", e);
                    }
                }
            });
        }

        @Override
        public void onStateChanged(final TransportState state) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == TransportState.STOPPED || state == TransportState.NO_MEDIA_PRESENT) {
                        playerLaunched = false;
                    }
                    notifyTransportStateChanged(state);
                    if (genaManager != null) {
                        genaManager.notifyTransportStateChanged();
                    }
                }
            });
        }

        @Override
        public void onPlaybackPositionChanged(final long positionMs, final long durationMs) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyPlaybackPositionChanged(positionMs, durationMs);
                }
            });
        }

        @Override
        public void onMediaCompleted() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    playerLaunched = false;
                    notifyMediaCompleted();
                }
            });
        }

        @Override
        public void onError(final int what, final int extra) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyDlnaError(what, extra);
                }
            });
        }
    };

    /** launchPlayerActivity - 根据媒体类型启动对应播放器 Activity */
    public void launchPlayerActivity(MediaInfo mediaInfo) {
        try {
            LogUtil.i(TAG, "launchPlayerActivity: uri=" + mediaInfo.getCurrentUri()
                    + ", isVideo=" + mediaInfo.isVideo() + ", mimeType=" + mediaInfo.getMimeType());

            // Build CastAction from MediaInfo
            CastAction castAction = new CastAction();
            castAction.setUri(mediaInfo.getCurrentUri());
            castAction.setTitle(mediaInfo.getTitle());
            castAction.setMimeType(mediaInfo.getMimeType());
            castAction.setArtist(mediaInfo.getArtist());
            castAction.setAlbum(mediaInfo.getAlbum());
            castAction.setAlbumArtUri(mediaInfo.getAlbumArtUri());

            if (mediaInfo.isVideo()) {
                launchVideoPlayer(mediaInfo, castAction);
            } else {
                Intent intent = new Intent(context, MusicPlayerActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                castAction.toIntent(intent);
                context.startActivity(intent);
                LogUtil.d(TAG, "MusicPlayerActivity launched successfully");
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to launch player activity", e);
        }
    }

    /**
     * launchVideoPlayer - 启动视频播放器
     * 检查是否配置了外部播放器，有则调用，否则回退到内置播放器
     */
    private void launchVideoPlayer(MediaInfo mediaInfo, CastAction castAction) {
        String externalPkg = PreferenceHelper.getVideoPlayerPackage();
        String uri = mediaInfo.getCurrentUri();

        if (externalPkg != null && !externalPkg.isEmpty()) {
            // Try to launch external video player
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(uri), "video/*");
                intent.setPackage(externalPkg);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("title", mediaInfo.getTitle());

                // Verify the target app exists
                PackageManager pm = context.getPackageManager();
                ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
                if (resolveInfo != null) {
                    LogUtil.d(TAG, "Launching external video player: " + externalPkg + " for URI: " + uri);
                    context.startActivity(intent);
                    return;
                } else {
                    LogUtil.w(TAG, "External video player not found: " + externalPkg + ", falling back to built-in");
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "Failed to launch external video player: " + externalPkg, e);
            }
        }

        // Fallback to built-in video player
        LogUtil.d(TAG, "Launching built-in video player for URI: " + uri);
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        castAction.toIntent(intent);
        context.startActivity(intent);
    }
}
