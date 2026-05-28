package com.example.ftpserver;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {
    private static final String TAG = "NetworkUtils";

    public static boolean isValidPort(int port) {
        return port >= 1024 && port <= 65535;
    }

    public static boolean isNetworkAvailableForFtp(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        // Check if we are connected to a WiFi network as a client
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
        }

        // If not connected to WiFi as a client, check if Hotspot is active
        return isHotspotActive();
    }

    /**
     * Detects if Mobile Hotspot is active by looking for common tethering interfaces
     * or the characteristic Hotspot gateway IP address.
     */
    public static boolean isHotspotActive() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) continue;

                // Common hotspot interface names: ap0, softap0, wlan1, etc.
                String name = intf.getName().toLowerCase();
                if (name.contains("ap") || name.contains("rndis") || name.contains("tether")) {
                    for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            Log.d(TAG, "Hotspot detected on interface: " + name + " with IP: " + addr.getHostAddress());
                            return true;
                        }
                    }
                }
                
                // Fallback: check for the common hotspot IP 192.168.43.1
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.getHostAddress().startsWith("192.168.43.")) {
                        Log.d(TAG, "Hotspot detected by IP pattern: " + addr.getHostAddress());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking hotspot status", e);
        }
        return false;
    }

    public static String getLocalIpAddress() {
        String hotspotIp = null;
        String wifiIp = null;
        String fallbackIp = null;

        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) continue;

                String name = intf.getName().toLowerCase();
                boolean isAp = name.contains("ap") || name.contains("softap") || name.contains("rndis") || name.contains("tether");
                
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip == null || ip.startsWith("127.")) continue;
                        
                        // Prioritize Hotspot IP if it's the standard gateway
                        if (isAp || ip.startsWith("192.168.43.")) {
                            hotspotIp = ip;
                        } else if (name.contains("wlan")) {
                            wifiIp = ip;
                        } else if (fallbackIp == null) {
                            fallbackIp = ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }

        if (hotspotIp != null) return hotspotIp;
        if (wifiIp != null) return wifiIp;
        return fallbackIp;
    }
}
