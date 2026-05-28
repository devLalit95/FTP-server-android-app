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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 1001;

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private ServerPreferences preferences;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver hotspotReceiver;
    private String lastKnownIp = null;
    private long lastIpUpdate = 0;
    private static final long IP_UPDATE_THROTTLE_MS = 2000;

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
                    
                    Boolean wasRunning = viewModel.getServerRunning().getValue();
                    updateStatus(running, address, port);
                    
                    // Only show toast if the status has actually changed
                    if (wasRunning == null || wasRunning != running) {
                        String toastMessage = running ? getString(R.string.toast_server_started) : getString(R.string.toast_server_stopped);
                        showToast(toastMessage);
                    }
                    break;
                case FtpServerService.ACTION_LOG_UPDATE:
                    String message = intent.getStringExtra(FtpServerService.EXTRA_MESSAGE);
                    if (!TextUtils.isEmpty(message)) {
                        viewModel.appendLog(message);
                    }
                    break;
                case FtpServerService.ACTION_CLIENTS_UPDATE:
                    int count = intent.getIntExtra(FtpServerService.EXTRA_CLIENTS, 0);
                    viewModel.updateClients(count);
                    break;
                case FtpServerService.ACTION_STATS_UPDATE:
                    int clients = intent.getIntExtra(FtpServerService.EXTRA_CLIENTS, 0);
                    long totalBytes = intent.getLongExtra(FtpServerService.EXTRA_TOTAL_BYTES, 0);
                    viewModel.updateStats(clients, totalBytes);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
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
        
        // Sync state if server is already running in background
        FtpServerManager manager = FtpServerManager.getInstance();
        if (manager.isRunning()) {
            viewModel.syncWithServerManager();
            updateStatus(true, manager.getAddress(), manager.getPort());
        }

        setupObservers();
        loadPreferences();
        bindActions();
        registerReceiver();
        updateLocalIp();
        setupNavigation();
        setupBackNavigation();

        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(FtpServerService.ACTION_STATUS_UPDATE);
        filter.addAction(FtpServerService.ACTION_LOG_UPDATE);
        filter.addAction(FtpServerService.ACTION_CLIENTS_UPDATE);
        filter.addAction(FtpServerService.ACTION_STATS_UPDATE);
        ContextCompat.registerReceiver(this, serviceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void setupObservers() {
        viewModel.getCurrentNavId().observe(this, this::handleNavigation);
        viewModel.getStatusText().observe(this, binding.textViewStatus::setText);
        viewModel.getServerAddress().observe(this, binding.textViewAddress::setText);
        viewModel.getIpAddress().observe(this, address -> binding.textViewIp.setText(getString(R.string.ip_template, address)));
        viewModel.getClientsCount().observe(this, count -> binding.textViewClients.setText(String.valueOf(count)));
        viewModel.getUptime().observe(this, uptime -> binding.textViewUptime.setText(uptime));
        viewModel.getTransferRate().observe(this, rate -> binding.textViewTxRate.setText(rate));
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
            
            // Switch button visibility instead of just enabling/disabling
            binding.buttonStart.setVisibility(running ? View.GONE : View.VISIBLE);
            binding.buttonStop.setVisibility(running ? View.VISIBLE : View.GONE);
        });
    }

    private void bindActions() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            viewModel.setCurrentNavId(item.getItemId());
            return true;
        });

        binding.buttonStart.setOnClickListener(v -> {
            if (!hasRequiredPermissions()) {
                requestRequiredPermissions();
                return;
            }

            int port = preferences.getPort();
            if (!NetworkUtils.isValidPort(port)) {
                showSnackbar(getString(R.string.error_port_invalid));
                return;
            }
            if (!NetworkUtils.isNetworkAvailableForFtp(this)) {
                showSnackbar(getString(R.string.error_no_wifi));
                updateLocalIp();
                return;
            }
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
        // Now handled by SecurityFragment for credentials
        binding.switchAnonymous.setChecked(preferences.isAnonymousEnabled());
        binding.switchSecure.setChecked(preferences.isSecureEnabled());
    }

    private void startFtpService(int port) {
        Intent serviceIntent = new Intent(this, FtpServerService.class);
        serviceIntent.setAction(FtpServerService.ACTION_START_SERVER);
        serviceIntent.putExtra(FtpServerService.EXTRA_PORT, port);
        serviceIntent.putExtra(FtpServerService.EXTRA_ANONYMOUS_ENABLED, preferences.isAnonymousEnabled());
        serviceIntent.putExtra(FtpServerService.EXTRA_SECURE_ENABLED, preferences.isSecureEnabled());
        serviceIntent.putExtra(FtpServerService.EXTRA_USERNAME, preferences.getUsername());
        serviceIntent.putExtra(FtpServerService.EXTRA_PASSWORD, preferences.getPassword());
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

    private void checkAndUpdateIp() {
        long now = System.currentTimeMillis();
        // Hard throttle: don't even scan for IP more than once every 2 seconds
        if (now - lastIpUpdate < 2000) return;

        String currentIp = NetworkUtils.getLocalIpAddress();
        
        if (currentIp != null && (!currentIp.equals(lastKnownIp) || (now - lastIpUpdate) > IP_UPDATE_THROTTLE_MS)) {
            lastKnownIp = currentIp;
            lastIpUpdate = now;
            updateLocalIp(currentIp);
        }
    }

    private void updateLocalIp(String currentAddress) {
        viewModel.setIpAddress(currentAddress);
        FtpServerManager manager = FtpServerManager.getInstance();
        int port = manager.isRunning() ? manager.getPort() : preferences.getPort();
        String displayAddress = manager.isRunning() ? manager.getAddress() : currentAddress;
        if (displayAddress == null) displayAddress = currentAddress;
        
        viewModel.setServerAddress(getString(R.string.ftp_url_template, displayAddress, port));
    }

    private void updateLocalIp() {
        checkAndUpdateIp();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
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

    private void setupNavigation() {
        // Initial fragment
        handleNavigation(R.id.nav_server);
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    checkAndUpdateIp();
                    viewModel.appendLog("Network available: " + network);
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    checkAndUpdateIp();
                    viewModel.appendLog("Network lost: " + network);
                });
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                runOnUiThread(() -> checkAndUpdateIp());
            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, @NonNull android.net.LinkProperties linkProperties) {
                runOnUiThread(() -> checkAndUpdateIp());
            }
        };

        // Use a broader request to catch more network changes, including those without internet
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        cm.registerNetworkCallback(request, networkCallback);
        
        // Also listen for Hotspot specifically if possible via broadcasts for legacy support
        hotspotReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra("wifi_state", 0);
                if (state == 13) { // AP_STATE_ENABLED
                    viewModel.appendLog("Hotspot enabled");
                } else if (state == 11) { // AP_STATE_DISABLED
                    viewModel.appendLog("Hotspot disabled");
                }
                checkAndUpdateIp();
            }
        };
        IntentFilter filter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        registerReceiver(hotspotReceiver, filter);
    }

    private void setupBackNavigation() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("FILE_BROWSER");
                if (fragment instanceof FileBrowserFragment) {
                    if (((FileBrowserFragment) fragment).onBackPressed()) {
                        return;
                    }
                }

                Integer currentNavId = viewModel.getCurrentNavId().getValue();
                if (currentNavId != null && currentNavId != R.id.nav_server) {
                    binding.bottomNav.setSelectedItemId(R.id.nav_server);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void handleNavigation(int id) {
        if (id == R.id.nav_server) {
            binding.scrollView.setVisibility(View.VISIBLE);
            binding.appBarLayout.setVisibility(View.VISIBLE);
            binding.bottomNav.setVisibility(View.VISIBLE);
            binding.fragmentContainer.setVisibility(View.GONE);
            
            // Remove browser fragment if exists
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("FILE_BROWSER");
            if (fragment != null) {
                getSupportFragmentManager().beginTransaction().remove(fragment).commit();
            }
        } else if (id == R.id.nav_files) {
            binding.scrollView.setVisibility(View.GONE);
            binding.appBarLayout.setVisibility(View.GONE);
            binding.bottomNav.setVisibility(View.GONE);
            binding.fragmentContainer.setVisibility(View.VISIBLE);
            
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragmentContainer, new FileBrowserFragment(), "FILE_BROWSER")
                    .commit();
        } else if (id == R.id.nav_security) {
            binding.scrollView.setVisibility(View.GONE);
            binding.appBarLayout.setVisibility(View.GONE);
            binding.bottomNav.setVisibility(View.VISIBLE);
            binding.fragmentContainer.setVisibility(View.VISIBLE);
            
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragmentContainer, new SecurityFragment(), "SECURITY")
                    .commit();
        } else {
            showToast("Feature coming soon!");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(serviceReceiver);
        if (hotspotReceiver != null) {
            unregisterReceiver(hotspotReceiver);
        }
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }
}
