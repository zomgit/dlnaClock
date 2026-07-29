package com.dlnaclock.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * NetworkUtil - 网络工具类
 * 提供 WiFi IP 获取（优先 WifiManager API）、网络状态检测
 * DLNA 要求设备和控制点在同一 WiFi 网络
 */
public class NetworkUtil {

    private static final String TAG = "NetworkUtil";

    /**
     * Get local IP address, prioritizing WiFi via WifiManager API.
     * DLNA requires WiFi IP for SSDP multicast and HTTP server.
     */
    public static String getLocalIpAddress(Context context) {
        // First: use WifiManager API - most reliable for WiFi IP
        String wifiIp = getWifiIpAddress(context);
        if (wifiIp != null && !wifiIp.equals("0.0.0.0")) {
            return wifiIp;
        }

        // Fallback: iterate network interfaces
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback()) continue;
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.0.0.0";
    }

    /**
     * Get WiFi IP address via WifiManager API.
     */
    public static String getWifiIpAddress(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    int ipInt = wifiInfo.getIpAddress();
                    if (ipInt != 0) {
                        return String.format("%d.%d.%d.%d",
                                (ipInt & 0xff), (ipInt >> 8 & 0xff),
                                (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.0.0.0";
    }

    /** isNetworkAvailable - 检查网络是否可用 */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    /** isWifiConnected - 检查 WiFi 是否已连接 */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifiNetwork != null && wifiNetwork.isConnected();
        }
        return false;
    }

    /**
     * getWifiNetworkInterface - 获取 WiFi 网络接口
     * 用于 MulticastSocket 绑定到正确的网络接口，确保 SSDP 多播在 WiFi 上收发
     */
    public static NetworkInterface getWifiNetworkInterface(Context context) {
        try {
            String wifiIp = getWifiIpAddress(context);
            if (wifiIp == null || wifiIp.equals("0.0.0.0")) return null;
            InetAddress wifiAddr = InetAddress.getByName(wifiIp);
            return NetworkInterface.getByInetAddress(wifiAddr);
        } catch (Exception e) {
            return null;
        }
    }
}
