package com.dlnaclock.airplay;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

/**
 * MdnsService - JmDNS 封装，负责注册 _airplay._tcp Bonjour 服务
 * 使 iOS/macOS 设备能在局域网中发现本机作为 AirPlay 接收器
 * 需要 WifiManager.MulticastLock 保证多播包正常接收
 */
public class MdnsService {

    private static final String TAG = "MdnsService";

    private Context context;
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private WifiManager.MulticastLock multicastLock;
    private volatile boolean running = false;

    /** MdnsService - 构造函数 */
    public MdnsService(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * start - 启动 mDNS 服务，注册 AirPlay Bonjour 记录
     * @param localIp    本机 WiFi IP 地址
     * @param deviceName 设备显示名称（在 iOS 设备列表中显示）
     */
    public void start(String localIp, String deviceName) {
        if (running) return;

        try {
            // 获取 MulticastLock，防止 WiFi 休眠时丢失多播包（与 SsdpClient 相同的模式）
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifiManager.createMulticastLock("DlnaClockAirPlay");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            // 获取设备 MAC 地址作为 deviceid（AirPlay 用于唯一标识）
            String deviceId = getDeviceMac();
            if (deviceId == null) {
                // 回退：生成随机 MAC
                deviceId = generateRandomMac();
            }

            // 构造 TXT 记录（AirPlay 发现所需字段）
            Map<String, String> txtRecords = new HashMap<>();
            txtRecords.put("deviceid", deviceId);
            txtRecords.put("features", AirPlayConstants.FEATURES);
            txtRecords.put("model", AirPlayConstants.MODEL);
            txtRecords.put("srcvers", AirPlayConstants.SRCVERS);
            txtRecords.put("protovers", AirPlayConstants.PROTOVERS);
            txtRecords.put("vv", "2");
            txtRecords.put("pk", "0000000000000000000000000000000000000000000000000000000000000000");

            // 创建 JmDNS 实例（绑定到本机 IP）
            InetAddress inetAddress = InetAddress.getByName(localIp);
            jmdns = JmDNS.create(inetAddress, deviceName);

            // 注册 AirPlay 服务
            serviceInfo = ServiceInfo.create(
                    AirPlayConstants.SERVICE_TYPE_AIRPLAY,
                    deviceName,
                    AirPlayConstants.AIRPLAY_PORT,
                    0,  // weight
                    0,  // priority
                    txtRecords
            );
            jmdns.registerService(serviceInfo);

            running = true;
            Log.i(TAG, "mDNS AirPlay service registered: " + deviceName
                    + " (" + deviceId + ") on " + localIp + ":" + AirPlayConstants.AIRPLAY_PORT);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start mDNS service", e);
            stop();
        }
    }

    /** stop - 停止 mDNS 服务，注销 Bonjour 记录并释放资源 */
    public void stop() {
        running = false;

        if (jmdns != null) {
            try {
                if (serviceInfo != null) {
                    jmdns.unregisterService(serviceInfo);
                    serviceInfo = null;
                }
                jmdns.close();
                jmdns = null;
            } catch (Exception e) {
                Log.e(TAG, "Error closing JmDNS", e);
            }
        }

        // 释放 MulticastLock
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
            multicastLock = null;
        }

        Log.i(TAG, "mDNS AirPlay service stopped");
    }

    /** isRunning - 返回 mDNS 服务是否正在运行 */
    public boolean isRunning() {
        return running;
    }

    /**
     * getDeviceMac - 获取设备 WiFi MAC 地址
     * AirPlay 使用 MAC 地址作为 deviceid 字段
     */
    private String getDeviceMac() {
        try {
            // 通过网络接口获取 MAC 地址（Android 6.0+ 不推荐使用 WifiInfo.getMacAddress）
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                String ip = null;
                try {
                    int ipInt = wifiManager.getConnectionInfo().getIpAddress();
                    if (ipInt != 0) {
                        ip = String.format("%d.%d.%d.%d",
                                (ipInt & 0xff), (ipInt >> 8 & 0xff),
                                (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                    }
                } catch (Exception ignored) {}

                if (ip != null) {
                    InetAddress addr = InetAddress.getByName(ip);
                    NetworkInterface intf = NetworkInterface.getByInetAddress(addr);
                    if (intf != null) {
                        byte[] mac = intf.getHardwareAddress();
                        if (mac != null) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < mac.length; i++) {
                                sb.append(String.format("%02X", mac[i]));
                                if (i < mac.length - 1) sb.append(":");
                            }
                            return sb.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get MAC address via NetworkInterface", e);
        }

        // 回退：使用 WifiInfo（低版本 Android 可用）
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                String mac = wifiManager.getConnectionInfo().getMacAddress();
                if (mac != null && !mac.equals("02:00:00:00:00:00")) {
                    return mac.toUpperCase();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get MAC address via WifiInfo", e);
        }

        return null;
    }

    /** generateRandomMac - 生成随机 MAC 地址（回退方案） */
    private String generateRandomMac() {
        java.util.Random random = new java.util.Random();
        byte[] mac = new byte[6];
        random.nextBytes(mac);
        // 设置本地管理位（bit 1 of first byte），清除多播位（bit 0）
        mac[0] = (byte) ((mac[0] | 0x02) & 0xFE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X", mac[i]));
            if (i < mac.length - 1) sb.append(":");
        }
        return sb.toString();
    }
}
