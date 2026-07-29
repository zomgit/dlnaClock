package com.dlnaclock.dlna.ssdp;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.dlnaclock.util.NetworkUtil;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * SsdpClient - SSDP 设备发现客户端
 * 负责加入多播组、监听 M-SEARCH 请求、发送 NOTIFY alive/byebye 通告
 * 使用 MulticastSocket 在 239.255.255.250:1900 上通信
 */
public class SsdpClient {

    private static final String TAG = "SsdpClient";
    private static final int BUFFER_SIZE = 4096; // 接收缓冲区大小

    private Context context;
    private MulticastSocket socket;              // 多播套接字
    private InetAddress multicastGroup;          // 多播组地址 (239.255.255.250)
    private Timer notifyTimer;                   // 定时发送 alive 通告
    private Thread receiveThread;                // 接收线程
    private volatile boolean running = false;    // 运行状态标志
    private WifiManager.MulticastLock multicastLock; // 多播锁，防止 WiFi 休眠时丢失多播包

    private String localIp;     // 本机 WiFi IP
    private int httpPort;       // HTTP 服务器端口
    private String udn;         // 设备唯一标识符
    private String deviceType;  // 设备类型 URN
    private String locationUrl; // 设备描述 XML 的 URL
    private long bootId;        // UPnP 1.1 启动 ID（Unix 时间戳秒）

    private SsdpListener listener;
    private Random random = new Random();

    /** SsdpListener - SSDP 事件监听接口 */
    public interface SsdpListener {
        /** onMSearchReceived - 收到 M-SEARCH 搜索请求 */
        void onMSearchReceived(String remoteIp, int remotePort, SsdpMessage message);
        /** onNotifyReceived - 收到 NOTIFY 通知 */
        void onNotifyReceived(String remoteIp, int remotePort, SsdpMessage message);
    }

    /** SsdpClient - 构造函数 */
    public SsdpClient(Context context) {
        this.context = context;
    }

    /** setListener - 设置 SSDP 事件监听器 */
    public void setListener(SsdpListener listener) {
        this.listener = listener;
    }

    /**
     * start - 启动 SSDP 客户端
     * 获取多播锁、创建多播套接字、加入多播组、启动接收线程和定时通告
     * @param localIp 本机 WiFi IP 地址
     * @param httpPort HTTP 服务器端口
     * @param udn 设备唯一标识符
     * @param deviceType 设备类型 URN
     */
    public void start(String localIp, int httpPort, String udn, String deviceType) {
        this.localIp = localIp;
        this.httpPort = httpPort;
        this.udn = udn;
        this.deviceType = deviceType;
        this.locationUrl = "http://" + localIp + ":" + httpPort + "/description.xml";
        this.bootId = System.currentTimeMillis() / 1000; // UPnP 1.1 BOOTID

        if (running) return;

        try {
            // Acquire multicast lock
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifiManager.createMulticastLock("DlnaClockSsdp");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            // Create multicast socket bound to WiFi interface
            multicastGroup = InetAddress.getByName(SsdpConstants.SSDP_MULTICAST_ADDRESS);
            socket = new MulticastSocket(SsdpConstants.SSDP_PORT);
            socket.setReuseAddress(true);

            // 显式绑定到 WiFi 网络接口，避免多网卡环境下多播收发异常
            NetworkInterface wifiIntf = NetworkUtil.getWifiNetworkInterface(context);
            if (wifiIntf != null) {
                Log.i(TAG, "Using WiFi interface: " + wifiIntf.getDisplayName() + " (" + localIp + ")");
            } else {
                Log.w(TAG, "WiFi interface not found, using default interface");
            }

            socket.setLoopbackMode(false);
            socket.setTimeToLive(4);

            // Join multicast group on WiFi interface
            if (wifiIntf != null) {
                socket.joinGroup(new java.net.InetSocketAddress(multicastGroup, SsdpConstants.SSDP_PORT), wifiIntf);
            } else {
                socket.joinGroup(multicastGroup);
            }

            running = true;

            // Start receive thread
            receiveThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveLoop();
                }
            }, "SSDP-Receive");
            receiveThread.setDaemon(true);
            receiveThread.start();

            // Start periodic NOTIFY announcements (every 120s)
            startNotifyTimer();

            // 启动时异步发送 3 轮 alive NOTIFY（间隔 10s），避免阻塞调用线程
            Thread startupThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        sendAliveNotifications();
                        Thread.sleep(10000);
                        if (running) sendAliveNotifications();
                        Thread.sleep(10000);
                        if (running) sendAliveNotifications();
                    } catch (InterruptedException ignored) {}
                }
            }, "SSDP-Startup-Notify");
            startupThread.setDaemon(true);
            startupThread.start();

            Log.i(TAG, "SSDP client started on " + localIp);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SSDP client", e);
        }
    }

    /** stop - 停止 SSDP 客户端，发送 byebye 并释放资源 */
    public void stop() {
        running = false;

        // Stop notify timer
        if (notifyTimer != null) {
            notifyTimer.cancel();
            notifyTimer = null;
        }

        // 在子线程发送 byebye 并关闭 socket，避免 NetworkOnMainThreadException
        // 注意：不能先设 socket=null，因为 sendNotify() 检查 this.socket
        Thread byebyeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 发送 byebye（socket 仍可用）
                sendByeByeNotifications();
                // 关闭 socket
                synchronized (SsdpClient.this) {
                    if (socket != null && !socket.isClosed()) {
                        try {
                            if (multicastGroup != null) socket.leaveGroup(multicastGroup);
                            socket.close();
                        } catch (Exception e) {
                            Log.e(TAG, "Error closing SSDP socket", e);
                        }
                    }
                }
                // 释放多播锁
                if (multicastLock != null && multicastLock.isHeld()) {
                    multicastLock.release();
                }
                Log.i(TAG, "SSDP client stopped");
            }
        }, "SSDP-ByeBye");
        byebyeThread.setDaemon(true);
        byebyeThread.start();
    }

    /** receiveLoop - 接收循环，在独立线程中运行，解析收到的 SSDP 消息 */
    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String remoteIp = packet.getAddress().getHostAddress();
                int remotePort = packet.getPort();

                SsdpMessage message = new SsdpMessage(packet);

                if (message.isMSearch()) {
                    Log.d(TAG, "Received M-SEARCH from " + remoteIp + ":" + remotePort);
                    if (listener != null) {
                        listener.onMSearchReceived(remoteIp, remotePort, message);
                    }
                } else if (message.isNotify()) {
                    Log.d(TAG, "Received NOTIFY from " + remoteIp + ":" + remotePort);
                    if (listener != null) {
                        listener.onNotifyReceived(remoteIp, remotePort, message);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "Error in SSDP receive loop", e);
                }
            }
        }
    }

    /**
     * sendSearchResponse - 发送 M-SEARCH 响应
     * 根据搜索目标 (ST) 匹配响应：ssdp:all / 设备类型 / UDN / 服务类型
     * 包含 0-120ms 随机延迟（符合 UPnP 规范）
     * @param remoteIp 搜索方 IP
     * @param remotePort 搜索方端口
     * @param searchRequest 搜索请求消息
     */
    public void sendSearchResponse(String remoteIp, int remotePort, SsdpMessage searchRequest) {
        try {
            String st = searchRequest.getST();
            if (st == null) return;

            Log.d(TAG, "M-SEARCH ST: " + st + " from " + remoteIp + ":" + remotePort);

            // Build list of matching response targets
            java.util.List<String[]> responses = new java.util.ArrayList<>();

            if (SsdpConstants.NT_ALL.equals(st)) {
                // ssdp:all - respond with everything
                responses.add(new String[]{deviceType, udn + "::" + deviceType});
                responses.add(new String[]{SsdpConstants.NT_ROOT_DEVICE, udn + "::" + SsdpConstants.NT_ROOT_DEVICE});
                responses.add(new String[]{udn, udn});
                // Service types
                responses.add(new String[]{SsdpConstants.SERVICE_TYPE_AVTRANSPORT,
                        udn + "::" + SsdpConstants.SERVICE_TYPE_AVTRANSPORT});
                responses.add(new String[]{SsdpConstants.SERVICE_TYPE_RENDERING_CONTROL,
                        udn + "::" + SsdpConstants.SERVICE_TYPE_RENDERING_CONTROL});
                responses.add(new String[]{SsdpConstants.SERVICE_TYPE_CONNECTION_MANAGER,
                        udn + "::" + SsdpConstants.SERVICE_TYPE_CONNECTION_MANAGER});
            } else if (deviceType.equals(st) || SsdpConstants.NT_ROOT_DEVICE.equals(st) || udn.equals(st)) {
                responses.add(new String[]{st, udn + "::" + st});
            } else if (st.startsWith("urn:schemas-upnp-org:service:")) {
                // Service type search
                responses.add(new String[]{st, udn + "::" + st});
            }

            if (responses.isEmpty()) return;

            // UPnP spec: random delay 0..MX*1000 ms; parse MX from request, default 120ms
            int mxMs = 120;
            try {
                String mxStr = searchRequest.getMX();
                if (mxStr != null && !mxStr.isEmpty()) {
                    int parsed = Integer.parseInt(mxStr.trim());
                    if (parsed > 0) {
                        mxMs = parsed * 1000; // MX 单位为秒，转为毫秒
                    }
                }
            } catch (NumberFormatException ignored) {}
            int delayMs = random.nextInt(Math.max(1, mxMs)); // 0..mxMs-1 毫秒
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}

            // 发送 2 次响应，间隔 200ms，对抗 UDP 丢包
            for (int attempt = 0; attempt < 2; attempt++) {
                for (String[] resp : responses) {
                    String response = SsdpMessage.buildSearchResponse(locationUrl, udn, resp[0],
                            SsdpConstants.DEFAULT_CACHE_CONTROL, bootId);
                    byte[] data = response.getBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length,
                            InetAddress.getByName(remoteIp), remotePort);
                    socket.send(packet);
                }
                if (attempt < 1) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }

            Log.d(TAG, "Sent " + responses.size() + " search responses (x2) to " + remoteIp + ":" + remotePort);
        } catch (Exception e) {
            Log.e(TAG, "Error sending search response", e);
        }
    }

    /** startNotifyTimer - 启动定时发送 alive 通告（间隔 120s） */
    private void startNotifyTimer() {
        notifyTimer = new Timer("SSDP-Notify", true);
        notifyTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendAliveNotifications();
            }
        }, SsdpConstants.NOTIFY_INTERVAL * 1000, SsdpConstants.NOTIFY_INTERVAL * 1000);
    }

    /** sendAliveNotifications - 发送所有类型的 alive 通知（设备/服务类型） */
    private void sendAliveNotifications() {
        String[] ntTypes = {udn, deviceType, SsdpConstants.NT_ROOT_DEVICE,
                SsdpConstants.SERVICE_TYPE_AVTRANSPORT,
                SsdpConstants.SERVICE_TYPE_RENDERING_CONTROL,
                SsdpConstants.SERVICE_TYPE_CONNECTION_MANAGER};
        for (String nt : ntTypes) {
            sendNotify(SsdpMessage.buildNotifyAlive(locationUrl, udn, nt,
                    SsdpConstants.DEFAULT_CACHE_CONTROL, bootId));
        }
    }

    /** sendByeByeNotifications - 发送所有类型的 byebye 离线通知 */
    private void sendByeByeNotifications() {
        String[] ntTypes = {udn, deviceType, SsdpConstants.NT_ROOT_DEVICE,
                SsdpConstants.SERVICE_TYPE_AVTRANSPORT,
                SsdpConstants.SERVICE_TYPE_RENDERING_CONTROL,
                SsdpConstants.SERVICE_TYPE_CONNECTION_MANAGER};
        for (String nt : ntTypes) {
            sendNotify(SsdpMessage.buildNotifyByeBye(udn, nt, bootId));
        }
    }

    /** sendNotify - 发送单个 NOTIFY 消息到多播组（synchronized 保护与 socket.close 互斥） */
    private void sendNotify(String message) {
        synchronized (this) {
            if (socket == null || socket.isClosed()) return;
            try {
                byte[] data = message.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        multicastGroup, SsdpConstants.SSDP_PORT);
                socket.send(packet);
            } catch (Exception e) {
                Log.e(TAG, "Error sending NOTIFY", e);
            }
        }
    }

    /** isRunning - 返回 SSDP 客户端是否正在运行 */
    public boolean isRunning() {
        return running;
    }
}
