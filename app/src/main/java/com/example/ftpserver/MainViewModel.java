package com.example.ftpserver;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainViewModel extends ViewModel {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final MutableLiveData<Boolean> serverRunning = new MutableLiveData<>(false);
    private final MutableLiveData<String> statusText = new MutableLiveData<>("Stopped");
    private final MutableLiveData<String> serverAddress = new MutableLiveData<>("");
    private final MutableLiveData<String> ipAddress = new MutableLiveData<>("--");
    private final MutableLiveData<String> logText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> currentNavId = new MutableLiveData<>(R.id.nav_server);
    private final MutableLiveData<Integer> clientsCount = new MutableLiveData<>(0);
    private final MutableLiveData<String> uptime = new MutableLiveData<>("--");
    private final MutableLiveData<String> transferRate = new MutableLiveData<>("0 B/s");

    private long startTime = 0;
    private long lastTotalBytes = 0;

    public LiveData<Boolean> getServerRunning() {
        return serverRunning;
    }

    public LiveData<String> getStatusText() {
        return statusText;
    }

    public LiveData<String> getServerAddress() {
        return serverAddress;
    }

    public LiveData<String> getIpAddress() {
        return ipAddress;
    }

    public LiveData<String> getLogText() {
        return logText;
    }

    public LiveData<Boolean> isBusy() {
        return busy;
    }

    public void syncWithServerManager() {
        FtpServerManager manager = FtpServerManager.getInstance();
        if (manager.isRunning()) {
            startTime = manager.getStartTime();
            serverRunning.setValue(true);
            statusText.setValue("Running");
            clientsCount.setValue(manager.getActiveSessions());
            busy.setValue(false);
        }
    }

    public void setServerRunning(boolean running) {
        Boolean wasRunning = serverRunning.getValue();
        serverRunning.setValue(running);
        statusText.setValue(running ? "Running" : "Stopped");
        if (running) {
            if (wasRunning == null || !wasRunning) {
                startTime = System.currentTimeMillis();
            }
        } else {
            startTime = 0;
            clientsCount.setValue(0);
            uptime.setValue("--");
            transferRate.setValue("0 B/s");
            lastTotalBytes = 0;
        }
    }

    public void setServerAddress(String address) {
        serverAddress.setValue(address);
    }

    public void setIpAddress(String address) {
        ipAddress.setValue(address);
    }

    public void setBusy(boolean value) {
        busy.setValue(value);
    }

    private static final int MAX_LOG_LINES = 100;

    public void appendLog(String message) {
        String timestamp = dateFormat.format(new Date());
        String entry = "[" + timestamp + "] " + message;
        
        String current = logText.getValue();
        if (current == null || current.isEmpty()) {
            logText.setValue(entry);
            return;
        }
        
        // Fast truncation without split() if log is too long
        int lineCount = 0;
        for (int i = 0; i < current.length(); i++) {
            if (current.charAt(i) == '\n') lineCount++;
        }
        
        if (lineCount >= MAX_LOG_LINES) {
            int firstNewline = current.indexOf('\n');
            if (firstNewline != -1) {
                current = current.substring(firstNewline + 1);
            }
        }
        
        logText.setValue(current + "\n" + entry);
    }

    public void clearLogs() {
        logText.setValue("");
    }

    public LiveData<Integer> getCurrentNavId() {
        return currentNavId;
    }

    public void setCurrentNavId(int id) {
        currentNavId.setValue(id);
    }

    public LiveData<Integer> getClientsCount() {
        return clientsCount;
    }

    public LiveData<String> getUptime() {
        return uptime;
    }

    public LiveData<String> getTransferRate() {
        return transferRate;
    }

    public void updateClients(int clients) {
        clientsCount.setValue(clients);
        updateUptime();
    }

    public void updateStats(int clients, long totalBytes) {
        clientsCount.setValue(clients);
        updateUptime();

        // Calculate rate
        if (lastTotalBytes == 0 && totalBytes > 0) {
            lastTotalBytes = totalBytes;
            transferRate.setValue("0 B/s");
            return;
        }

        long delta = (totalBytes >= lastTotalBytes) ? (totalBytes - lastTotalBytes) : 0;
        lastTotalBytes = totalBytes;
        
        // Since statsTask runs every 1.5s, we divide by 1.5 to get bytes per second
        long bytesPerSecond = (long) (delta / 1.5);
        transferRate.setValue(formatRate(bytesPerSecond));
    }

    private void updateUptime() {
        if (startTime > 0) {
            long diff = System.currentTimeMillis() - startTime;
            long seconds = diff / 1000;
            if (seconds > 0) {
                long h = seconds / 3600;
                long m = (seconds % 3600) / 60;
                long s = seconds % 60;
                uptime.setValue(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
            }
        }
    }

    private String formatRate(long bytesPerSecond) {
        if (bytesPerSecond <= 0) return "0 B/s";
        if (bytesPerSecond < 1024) return bytesPerSecond + " B/s";
        int exp = (int) (Math.log(bytesPerSecond) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.getDefault(), "%.1f %sB/s", bytesPerSecond / Math.pow(1024, exp), pre);
    }
}
