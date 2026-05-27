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

    public void setServerRunning(boolean running) {
        serverRunning.setValue(running);
        statusText.setValue(running ? "Running" : "Stopped");
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

    public void appendLog(String message) {
        String timestamp = dateFormat.format(new Date());
        String current = logText.getValue();
        if (current == null) {
            current = "";
        }
        String entry = "[" + timestamp + "] " + message;
        String newLog = current.isEmpty() ? entry : current + "\n" + entry;
        logText.setValue(newLog);
    }

    public void clearLogs() {
        logText.setValue("");
    }
}
