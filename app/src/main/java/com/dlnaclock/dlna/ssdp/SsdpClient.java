package com.dlnaclock.dlna.ssdp;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Timer;
import java.util.TimerTask;

public class SsdpClient {

    private static final String TAG = "SsdpClient";
    private static final int BUFFER_SIZE = 4096;

    private Context context;
    private MulticastSocket socket;
    private InetAddress multicastGroup;
    private Timer notifyTimer;
    private Thread receiveThread;
    private volatile boolean running = false;
    private WifiManager.MulticastLock multicastLock;

    private String localIp;
    private int httpPort;
    private String udn;
    private String deviceType;
    private String locationUrl;

    private SsdpListener listener;

    public interface SsdpListener {
        void onMSearchReceived(String remoteIp, int remotePort, SsdpMessage message);
        void onNotifyReceived(String remoteIp, int remotePort, SsdpMessage message);
    }

    public SsdpClient(Context context) {
        this.context = context;
    }

    public void setListener(SsdpListener listener) {
        this.listener = listener;
    }

    public void start(String localIp, int httpPort, String udn, String deviceType) {
        this.localIp = localIp;
        this.httpPort = httpPort;
        this.udn = udn;
        this.deviceType = deviceType;
        this.locationUrl = "http://" + localIp + ":" + httpPort + "/description.xml";

        if (running) return;

        try {
            // Acquire multicast lock
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifiManager.createMulticastLock("DlnaClockSsdp");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            // Create multicast socket
            multicastGroup = InetAddress.getByName(SsdpConstants.SSDP_MULTICAST_ADDRESS);
            socket = new MulticastSocket(SsdpConstants.SSDP_PORT);
            socket.setReuseAddress(true);
            socket.setLoopbackMode(false); // false = join multicast
            socket.setTimeToLive(4);

            // Join multicast group
            socket.joinGroup(multicastGroup);

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

            // Start periodic NOTIFY announcements
            startNotifyTimer();

            // Send initial alive notifications
            sendAliveNotifications();

            Log.i(TAG, "SSDP client started on " + localIp);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SSDP client", e);
        }
    }

    public void stop() {
        running = false;

        // Send byebye
        sendByeByeNotifications();

        // Stop notify timer
        if (notifyTimer != null) {
            notifyTimer.cancel();
            notifyTimer = null;
        }

        // Close socket
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(multicastGroup);
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing SSDP socket", e);
            }
        }

        // Release multicast lock
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
            multicastLock = null;
        }

        Log.i(TAG, "SSDP client stopped");
    }

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

    public void sendSearchResponse(String remoteIp, int remotePort, SsdpMessage searchRequest) {
        try {
            String st = searchRequest.getST();
            if (st == null) return;

            // Check if we match the search target
            boolean match = false;
            if (SsdpConstants.NT_ALL.equals(st) || SsdpConstants.NT_ROOT_DEVICE.equals(st)) {
                match = true;
            } else if (deviceType.equals(st)) {
                match = true;
            } else if (udn.equals(st)) {
                match = true;
            }

            if (!match) return;

            // Build response(s)
            String[] responseTargets = {deviceType, SsdpConstants.NT_ROOT_DEVICE, udn};
            for (String target : responseTargets) {
                String response = SsdpMessage.buildSearchResponse(locationUrl, udn, target,
                        SsdpConstants.DEFAULT_CACHE_CONTROL);
                byte[] data = response.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        InetAddress.getByName(remoteIp), remotePort);
                socket.send(packet);
            }

            Log.d(TAG, "Sent search response to " + remoteIp + ":" + remotePort);
        } catch (Exception e) {
            Log.e(TAG, "Error sending search response", e);
        }
    }

    private void startNotifyTimer() {
        notifyTimer = new Timer("SSDP-Notify", true);
        notifyTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendAliveNotifications();
            }
        }, SsdpConstants.NOTIFY_INTERVAL * 1000, SsdpConstants.NOTIFY_INTERVAL * 1000);
    }

    private void sendAliveNotifications() {
        String[] ntTypes = {udn, deviceType, SsdpConstants.NT_ROOT_DEVICE};
        for (String nt : ntTypes) {
            sendNotify(SsdpMessage.buildNotifyAlive(locationUrl, udn, nt,
                    SsdpConstants.DEFAULT_CACHE_CONTROL));
        }
    }

    private void sendByeByeNotifications() {
        String[] ntTypes = {udn, deviceType, SsdpConstants.NT_ROOT_DEVICE};
        for (String nt : ntTypes) {
            sendNotify(SsdpMessage.buildNotifyByeBye(udn, nt));
        }
    }

    private void sendNotify(String message) {
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

    public boolean isRunning() {
        return running;
    }
}
