package com.dlnaclock.dlna;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dlnaclock.App;
import com.dlnaclock.dlna.avt.AVTransportService;
import com.dlnaclock.dlna.avt.MediaInfo;
import com.dlnaclock.dlna.avt.TransportState;
import com.dlnaclock.dlna.device.DmrDevice;
import com.dlnaclock.dlna.rc.ConnectionManagerService;
import com.dlnaclock.dlna.rc.RenderingControlService;
import com.dlnaclock.dlna.server.DlnaHttpServer;
import com.dlnaclock.dlna.soap.SoapHandler;
import com.dlnaclock.dlna.ssdp.SsdpClient;
import com.dlnaclock.dlna.ssdp.SsdpConstants;
import com.dlnaclock.dlna.ssdp.SsdpMessage;
import com.dlnaclock.media.MusicPlayerActivity;
import com.dlnaclock.media.VideoPlayerActivity;
import com.dlnaclock.util.NetworkUtil;
import com.dlnaclock.util.PreferenceHelper;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class DlnaManager {

    private static final String TAG = "DlnaManager";
    private static DlnaManager instance;

    private Context context;
    private DmrDevice device;
    private SsdpClient ssdpClient;
    private DlnaHttpServer httpServer;
    private AVTransportService avtService;
    private RenderingControlService rcService;
    private ConnectionManagerService cmService;
    private SoapHandler soapHandler;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private CopyOnWriteArrayList<DlnaEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public interface DlnaEventListener {
        void onMediaUriSet(MediaInfo mediaInfo);
        void onTransportStateChanged(TransportState state);
        void onPlaybackPositionChanged(long positionMs, long durationMs);
        void onMediaCompleted();
        void onDlnaError(int what, int extra);
    }

    private DlnaManager() {}

    public static synchronized DlnaManager getInstance() {
        if (instance == null) {
            instance = new DlnaManager();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();

        // Create device
        device = new DmrDevice();
        device.setUdn("uuid:" + UUID.randomUUID().toString());
        device.setFriendlyName(PreferenceHelper.getDeviceName());
        device.setManufacturer("DlnaClock");
        device.setModelName("DlnaClock ScreenSaver");
        device.setModelNumber("1.0");
        device.setModelDescription("DLNA Media Renderer with Clock ScreenSaver");

        // Create services
        avtService = new AVTransportService();
        rcService = new RenderingControlService(context);
        cmService = new ConnectionManagerService();
        soapHandler = new SoapHandler(avtService, rcService, cmService);

        // Listen to AVTransport events
        avtService.addListener(new AVTransportService.AVTransportListener() {
            @Override
            public void onUriSet(final MediaInfo mediaInfo) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyMediaUriSet(mediaInfo);
                    }
                });
            }

            @Override
            public void onStateChanged(final TransportState state) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyTransportStateChanged(state);
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
        });
    }

    public void start() {
        String localIp = NetworkUtil.getLocalIpAddress();
        Log.i(TAG, "Starting DLNA services on IP: " + localIp);

        // Update device name from preferences
        device.setFriendlyName(PreferenceHelper.getDeviceName());

        // Start HTTP server
        try {
            httpServer = new DlnaHttpServer(device, localIp, soapHandler);
            httpServer.start();
            Log.i(TAG, "HTTP server started on port " + httpServer.getPort());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start HTTP server", e);
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
                Log.d(TAG, "Received NOTIFY from " + remoteIp);
            }
        });
        ssdpClient.start(localIp, httpServer.getPort(), device.getUdn(), device.getDeviceType());
    }

    public void shutdown() {
        Log.i(TAG, "Shutting down DLNA services");

        if (ssdpClient != null) {
            ssdpClient.stop();
            ssdpClient = null;
        }

        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }

        if (avtService != null) {
            avtService.release();
        }
    }

    public void restart() {
        shutdown();
        start();
    }

    // Event listener management
    public void addEventListener(DlnaEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

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
    public boolean isRunning() { return ssdpClient != null && ssdpClient.isRunning(); }

    public void launchPlayerActivity(MediaInfo mediaInfo) {
        Intent intent;
        if (mediaInfo.isVideo()) {
            intent = new Intent(context, VideoPlayerActivity.class);
        } else {
            intent = new Intent(context, MusicPlayerActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
