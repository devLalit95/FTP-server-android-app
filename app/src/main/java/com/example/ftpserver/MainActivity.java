package com.example.ftpserver;
import com.example.ftpserver.databinding.ActivityMainBinding;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 1001;

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private ServerPreferences preferences;

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case FtpServerService.ACTION_STATUS_UPDATE:
                    boolean running = intent.getBooleanExtra(FtpServerService.EXTRA_SERVER_RUNNING, false);
                    int port = intent.getIntExtra(FtpServerService.EXTRA_SERVER_PORT, 0);
                    String address = intent.getStringExtra(FtpServerService.EXTRA_SERVER_ADDRESS);
                    updateStatus(running, address, port);
                    String toastMessage = running ? getString(R.string.toast_server_started) : getString(R.string.toast_server_stopped);
                    showToast(toastMessage);
                    break;
                case FtpServerService.ACTION_LOG_UPDATE:
                    String message = intent.getStringExtra(FtpServerService.EXTRA_MESSAGE);
                    if (!TextUtils.isEmpty(message)) {
                        viewModel.appendLog(message);
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        preferences = new ServerPreferences(this);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setupObservers();
        loadPreferences();
        bindActions();
        registerReceiver();
        updateLocalIp();

        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(FtpServerService.ACTION_STATUS_UPDATE);
        filter.addAction(FtpServerService.ACTION_LOG_UPDATE);
        ContextCompat.registerReceiver(this, serviceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void setupObservers() {
        viewModel.getStatusText().observe(this, binding.textViewStatus::setText);
        viewModel.getServerAddress().observe(this, binding.textViewAddress::setText);
        viewModel.getIpAddress().observe(this, address -> binding.textViewIp.setText(getString(R.string.label_device_ip) + ": " + address));
        viewModel.getLogText().observe(this, text -> {
            binding.textViewLog.setText(text);
            binding.main.post(() -> {
                View scrollView = binding.cardLogs.getParent() instanceof View ? (View) binding.cardLogs.getParent() : null;
                if (scrollView instanceof androidx.core.widget.NestedScrollView) {
                    ((androidx.core.widget.NestedScrollView) scrollView).fullScroll(View.FOCUS_DOWN);
                }
            });
        });
        viewModel.isBusy().observe(this, busy -> binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE));
        viewModel.getServerRunning().observe(this, running -> {
            int colorRes = running ? R.color.success : R.color.error;
            binding.viewStatusIndicator.setBackgroundTintList(ContextCompat.getColorStateList(this, colorRes));
            binding.buttonStart.setEnabled(!running);
            binding.buttonStop.setEnabled(running);
        });
    }

    private void bindActions() {
        binding.buttonStart.setOnClickListener(v -> {
            if (!hasRequiredPermissions()) {
                requestRequiredPermissions();
                return;
            }

            int port = parsePort(binding.editTextPort.getText() != null ? binding.editTextPort.getText().toString() : "");
            if (!NetworkUtils.isValidPort(port)) {
                showSnackbar(getString(R.string.error_port_invalid));
                return;
            }
            if (!NetworkUtils.isWifiConnected(this)) {
                showSnackbar(getString(R.string.error_no_wifi));
                updateLocalIp();
                return;
            }
            savePreferences();
            startFtpService(port);
        });

        binding.buttonStop.setOnClickListener(v -> stopFtpService());

        binding.buttonCopy.setOnClickListener(v -> {
            String address = viewModel.getServerAddress().getValue();
            if (!TextUtils.isEmpty(address)) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("FTP Address", address);
                    clipboard.setPrimaryClip(clip);
                    showToast(getString(R.string.toast_copy_success));
                }
            }
        });

        binding.buttonToggleLogs.setOnClickListener(v -> {
            boolean isVisible = binding.cardLogs.getVisibility() == View.VISIBLE;
            binding.cardLogs.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            binding.buttonToggleLogs.setText(isVisible ? "Show Connection Logs" : "Hide Connection Logs");
        });
    }

    private void loadPreferences() {
        binding.editTextUsername.setText(preferences.getUsername());
        binding.editTextPassword.setText(preferences.getPassword());
        binding.editTextPort.setText(String.valueOf(preferences.getPort()));
        binding.switchAnonymous.setChecked(preferences.isAnonymousEnabled());
        binding.switchSecure.setChecked(preferences.isSecureEnabled());
    }

    private void savePreferences() {
        preferences.setUsername(binding.editTextUsername.getText() != null ? binding.editTextUsername.getText().toString().trim() : "lalit");
        preferences.setPassword(binding.editTextPassword.getText() != null ? binding.editTextPassword.getText().toString() : "lalit");
        preferences.setPort(parsePort(binding.editTextPort.getText() != null ? binding.editTextPort.getText().toString() : ""));
        preferences.setAnonymousEnabled(binding.switchAnonymous.isChecked());
        preferences.setSecureEnabled(binding.switchSecure.isChecked());
    }

    private int parsePort(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void startFtpService(int port) {
        Intent serviceIntent = new Intent(this, FtpServerService.class);
        serviceIntent.setAction(FtpServerService.ACTION_START_SERVER);
        serviceIntent.putExtra(FtpServerService.EXTRA_PORT, port);
        serviceIntent.putExtra(FtpServerService.EXTRA_ANONYMOUS_ENABLED, binding.switchAnonymous.isChecked());
        serviceIntent.putExtra(FtpServerService.EXTRA_SECURE_ENABLED, binding.switchSecure.isChecked());
        serviceIntent.putExtra(FtpServerService.EXTRA_USERNAME, binding.editTextUsername.getText() != null ? binding.editTextUsername.getText().toString() : "lalit");
        serviceIntent.putExtra(FtpServerService.EXTRA_PASSWORD, binding.editTextPassword.getText() != null ? binding.editTextPassword.getText().toString() : "lalit");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
        viewModel.setBusy(true);
    }

    private void stopFtpService() {
        Intent serviceIntent = new Intent(this, FtpServerService.class);
        serviceIntent.setAction(FtpServerService.ACTION_STOP_SERVER);
        startService(serviceIntent);
        viewModel.setBusy(true);
    }

    private void updateStatus(boolean running, String address, int port) {
        viewModel.setBusy(false);
        viewModel.setServerRunning(running);
        String formattedAddress = (!TextUtils.isEmpty(address) && port > 0)
                ? getString(R.string.ftp_url_template, address, port)
                : getString(R.string.ftp_url_template, "--", preferences.getPort());
        viewModel.setServerAddress(formattedAddress);
    }

    private void updateLocalIp() {
        String ip = NetworkUtils.getLocalIpAddress();
        if (TextUtils.isEmpty(ip)) {
            ip = "--";
        }
        viewModel.setIpAddress(ip);
        int port = parsePort(binding.editTextPort.getText() != null ? binding.editTextPort.getText().toString() : "");
        if (!NetworkUtils.isValidPort(port)) {
            port = preferences.getPort();
        }
        viewModel.setServerAddress(getString(R.string.ftp_url_template, ip, port));
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                viewModel.appendLog("ACTION REQUIRED: Please grant 'All Files Access' in the next screen to see your files.");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.POST_NOTIFICATIONS
            }, REQUEST_PERMISSIONS);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (!granted) {
                showSnackbar(getString(R.string.error_permission_denied));
            }
        }
    }

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private void showToast(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(serviceReceiver);
    }
}
