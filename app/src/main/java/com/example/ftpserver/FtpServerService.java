package com.example.ftpserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import androidx.annotation.NonNull;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

public class FtpServerService extends Service {
    public static final String ACTION_START_SERVER = "com.example.ftpserver.action.START_SERVER";
    public static final String ACTION_STOP_SERVER = "com.example.ftpserver.action.STOP_SERVER";
    public static final String ACTION_STATUS_UPDATE = "com.example.ftpserver.action.STATUS_UPDATE";
    public static final String ACTION_LOG_UPDATE = "com.example.ftpserver.action.LOG_UPDATE";
    public static final String ACTION_STATS_UPDATE = "com.example.ftpserver.action.STATS_UPDATE";
    public static final String ACTION_CLIENTS_UPDATE = "com.example.ftpserver.action.CLIENTS_UPDATE";
    public static final String EXTRA_SERVER_RUNNING = "extra_server_running";
    public static final String EXTRA_SERVER_PORT = "extra_server_port";
    public static final String EXTRA_SERVER_ADDRESS = "extra_server_address";
    public static final String EXTRA_MESSAGE = "extra_message";
    public static final String EXTRA_CLIENTS = "extra_clients";
    public static final String EXTRA_TOTAL_BYTES = "extra_total_bytes";
    public static final String EXTRA_USERNAME = "extra_username";
    public static final String EXTRA_PASSWORD = "extra_password";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_ANONYMOUS_ENABLED = "extra_anonymous_enabled";
    public static final String EXTRA_SECURE_ENABLED = "extra_secure_enabled";

    private static final String CHANNEL_ID = "ftp_server_channel";
    private static final int NOTIFICATION_ID = 3401;
    private FtpServerManager serverManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private String lastKnownIp = null;
    private long lastNotificationUpdate = 0;
    private static final long NOTIFICATION_THROTTLE_MS = 3000;

    @Override
    public void onCreate() {
        super.onCreate();
        serverManager = FtpServerManager.getInstance();
        createNotificationChannel();
        registerNetworkMonitor();
    }

    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties lp) {
                // Link properties change often, only update if IP changed or throttled
                checkAndUpdateNetworkInfo();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities nc) {
                // Capabilities like signal strength change constantly, only update if IP changed or throttled
                checkAndUpdateNetworkInfo();
            }
        };

        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.e("FtpServerService", "Failed to register network callback", e);
        }
    }

    private synchronized void checkAndUpdateNetworkInfo() {
        if (!serverManager.isRunning()) return;

        long now = System.currentTimeMillis();
        // Hard throttle: don't even scan for IP more than once every 2 seconds
        if (now - lastNotificationUpdate < 2000) return;

        String currentIp = NetworkUtils.getLocalIpAddress();
        
        // Only trigger heavy notification/broadcast if IP changed OR it's been a while (throttle)
        if (currentIp != null && (!currentIp.equals(lastKnownIp) || (now - lastNotificationUpdate) > NOTIFICATION_THROTTLE_MS)) {
            lastKnownIp = currentIp;
            lastNotificationUpdate = now;
            updateServiceNotification(currentIp);
        }
    }

    private void updateServiceNotification(String address) {
        int port = serverManager.getPort();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, createNotification(address, port));
        }
        // Also broadcast the new status to update the UI
        sendStatus(true, port, address);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_START_SERVER:
                int port = intent.getIntExtra(EXTRA_PORT, 2121);
                boolean anonymousEnabled = intent.getBooleanExtra(EXTRA_ANONYMOUS_ENABLED, true);
                boolean secureEnabled = intent.getBooleanExtra(EXTRA_SECURE_ENABLED, true);
                String username = intent.getStringExtra(EXTRA_USERNAME);
                String password = intent.getStringExtra(EXTRA_PASSWORD);
                startServer(port, anonymousEnabled, secureEnabled, username, password);
                break;
            case ACTION_STOP_SERVER:
                stopServer();
                break;
        }
        return START_STICKY;
    }

    private void startServer(int port,
            boolean anonymousEnabled,
            boolean secureEnabled,
            String username,
            String password) {
        File homeDirectory = resolveHomeDirectory();
        serverManager.startServer(port, anonymousEnabled, secureEnabled, username, password, homeDirectory,
                new FtpServerManager.ServerListener() {
                    @Override
                    public void onStarted(int startedPort, String address) {
                        sendStatus(true, startedPort, address);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            int serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                serviceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                                serviceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING;
                            }
                            startForeground(NOTIFICATION_ID, createNotification(address, startedPort), serviceTypes);
                        } else {
                            startForeground(NOTIFICATION_ID, createNotification(address, startedPort));
                        }
                        sendLog("FTP server is running at ftp://" + address + ":" + startedPort);
                    }

                    @Override
                    public void onStopped() {
                        sendStatus(false, 0, "");
                        stopForeground(true);
                        stopSelf();
                        sendLog("FTP server stopped.");
                    }

                    @Override
                    public void onError(String message) {
                        sendStatus(false, 0, "");
                        stopForeground(true);
                        stopSelf();
                        sendError(message);
                        Log.e("FtpServerService", "Server error: " + message);
                    }

                    @Override
                    public void onLog(String message) {
                        sendLog(message);
                    }

                    @Override
                    public void onStatsUpdate(int clients, long totalBytes) {
                        Intent statsIntent = new Intent(ACTION_STATS_UPDATE);
                        statsIntent.putExtra(EXTRA_CLIENTS, clients);
                        statsIntent.putExtra(EXTRA_TOTAL_BYTES, totalBytes);
                        sendBroadcast(statsIntent);
                    }

                    @Override
                    public void onClientsUpdate(int activeSessions) {
                        Intent clientsIntent = new Intent(ACTION_CLIENTS_UPDATE);
                        clientsIntent.putExtra(EXTRA_CLIENTS, activeSessions);
                        sendBroadcast(clientsIntent);
                    }
                });
    }

    private void stopServer() {
        serverManager.stopServer(new FtpServerManager.ServerListener() {
            @Override
            public void onStarted(int port, String address) {
            }

            @Override
            public void onStopped() {
                sendStatus(false, 0, "");
                stopForeground(true);
                stopSelf();
            }

            @Override
            public void onError(String message) {
                sendError(message);
            }

            @Override
            public void onLog(String message) {
                sendLog(message);
            }

            @Override
            public void onStatsUpdate(int clients, long totalBytes) {
            }

            @Override
            public void onClientsUpdate(int activeSessions) {
            }
        });
    }

    private void sendStatus(boolean running, int port, String address) {
        Intent statusIntent = new Intent(ACTION_STATUS_UPDATE);
        statusIntent.putExtra(EXTRA_SERVER_RUNNING, running);
        statusIntent.putExtra(EXTRA_SERVER_PORT, port);
        statusIntent.putExtra(EXTRA_SERVER_ADDRESS, address);
        sendBroadcast(statusIntent);
    }

    private void sendLog(String message) {
        Intent logIntent = new Intent(ACTION_LOG_UPDATE);
        logIntent.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(logIntent);
    }

    private void sendError(String message) {
        sendLog("Error: " + message);
    }

    private File resolveHomeDirectory() {
        // Use the absolute root of internal storage for broad access
        File externalRoot = Environment.getExternalStorageDirectory();
        
        boolean hasPermission = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager();
        } else {
            hasPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        if (hasPermission && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            // Return the root directory instead of a subfolder for permanent broad access
            Log.d("FtpServerService", "FTP Root set to: " + externalRoot.getAbsolutePath());
            return externalRoot;
        }

        // Fallback to app-specific directory if root is not accessible
        File fallbackRoot = new File(getExternalFilesDir(null), "ftp_root");
        if (!fallbackRoot.exists()) {
            fallbackRoot.mkdirs();
        }
        Log.w("FtpServerService", "Permission not granted. Falling back to: " + fallbackRoot.getAbsolutePath());
        return fallbackRoot;
    }

    private Notification createNotification(String address, int port) {
        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE);
        String title = getString(R.string.notification_channel_name);
        String content = getString(R.string.ftp_url_template, address == null ? "--" : address, port);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.unregisterNetworkCallback(networkCallback);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
