package com.example.ftpserver;

import android.text.TextUtils;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;

import org.apache.ftpserver.usermanager.impl.BaseUser;

import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

public class FtpServerManager {
    public interface ServerListener {
        void onStarted(int port, String address);

        void onStopped();

        void onError(String message);

        void onLog(String message);
    }

    private static FtpServerManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private FtpServer server;
    private int port;
    private String address;

    private FtpServerManager() {
    }

    public static synchronized FtpServerManager getInstance() {
        if (instance == null) {
            instance = new FtpServerManager();
        }
        return instance;
    }

    public synchronized boolean isRunning() {
        return server != null && !server.isStopped();
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized String getAddress() {
        return address;
    }

    public void startServer(final int port,
            final boolean anonymousEnabled,
            final boolean secureEnabled,
            final String username,
            final String password,
            final File homeDirectory,
            final ServerListener listener) {
        
        if (listener != null) {
            listener.onLog("Initializing FTP server components...");
        }

        final Future<?>[] startFuture = new Future<?>[1];
        
        // Setup timeout task
        scheduler.schedule(() -> {
            if (startFuture[0] != null && !startFuture[0].isDone()) {
                startFuture[0].cancel(true);
                if (listener != null) {
                    listener.onLog("ERROR: Server start timed out after 10 seconds.");
                    listener.onError("Server start timed out.");
                }
            }
        }, 10, TimeUnit.SECONDS);

        startFuture[0] = executor.submit(() -> {
            if (isRunning()) {
                if (listener != null) {
                    listener.onLog("FTP server already running.");
                    listener.onStarted(this.port, this.address);
                }
                return;
            }

            if (listener != null) {
                listener.onLog("Checking file system permissions...");
            }

            if (homeDirectory == null) {
                if (listener != null) {
                    listener.onLog("ERROR: Home directory is invalid (null).");
                    listener.onError("Home directory is invalid.");
                }
                return;
            }

            if (listener != null) {
                listener.onLog("Preparing home directory: " + homeDirectory.getAbsolutePath());
            }

            if (!homeDirectory.exists() && !homeDirectory.mkdirs()) {
                if (listener != null) {
                    listener.onLog("ERROR: Failed to create directories at " + homeDirectory.getAbsolutePath());
                    listener.onError("Unable to create FTP home folder.");
                }
                return;
            }

            try {
                if (listener != null) {
                    listener.onLog("Loading FTP driver and configuring listeners...");
                }
                FtpServerFactory serverFactory = new FtpServerFactory();
                ListenerFactory listenerFactory = new ListenerFactory();
                listenerFactory.setPort(port);
                serverFactory.addListener("default", listenerFactory.createListener());

                if (listener != null) {
                    listener.onLog("Setting up User Manager...");
                }
                PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
                userManagerFactory.setPasswordEncryptor(new ClearTextPasswordEncryptor());
                UserManager userManager = userManagerFactory.createUserManager();

                if (anonymousEnabled) {
                    if (listener != null) {
                        listener.onLog("Adding anonymous user...");
                    }
                    BaseUser anonymous = new BaseUser();
                    anonymous.setName("anonymous");
                    anonymous.setPassword("");
                    anonymous.setHomeDirectory(homeDirectory.getAbsolutePath());
                    anonymous.setAuthorities(Arrays.asList(new WritePermission()));
                    userManager.save(anonymous);
                }

                if (secureEnabled && !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
                    if (listener != null) {
                        listener.onLog("Adding secure user: " + username);
                    }
                    BaseUser secureUser = new BaseUser();
                    secureUser.setName(username);
                    secureUser.setPassword(password);
                    secureUser.setHomeDirectory(homeDirectory.getAbsolutePath());
                    secureUser.setAuthorities(Arrays.asList(new WritePermission()));
                    userManager.save(secureUser);
                }

                serverFactory.setUserManager(userManager);
                
                if (listener != null) {
                    listener.onLog("Attaching session loggers...");
                }
                Map<String, DefaultFtplet> ftplets = new HashMap<>();
                ftplets.put("ftpLogger", new DefaultFtplet() {
                    @Override
                    public FtpletResult onConnect(FtpSession session) {
                        if (listener != null) {
                            listener.onLog("Client connected from: " + session.getClientAddress());
                        }
                        return FtpletResult.DEFAULT;
                    }

                    @Override
                    public FtpletResult onDisconnect(FtpSession session) {
                        if (listener != null) {
                            listener.onLog("Client disconnected: " + session.getClientAddress());
                        }
                        return FtpletResult.DEFAULT;
                    }
                });
                serverFactory.setFtplets(new HashMap<>(ftplets));

                if (listener != null) {
                    listener.onLog("Starting Apache FtpServer engine...");
                }
                server = serverFactory.createServer();
                server.start();

                synchronized (this) {
                    this.port = port;
                    this.address = NetworkUtils.getLocalIpAddress();
                }

                if (listener != null) {
                    listener.onLog("SUCCESS: Server is online at ftp://" + address + ":" + port);
                    listener.onStarted(port, address);
                }
            } catch (Exception exception) {
                if (listener != null) {
                    listener.onLog("CRITICAL ERROR: " + exception.getMessage());
                    listener.onError(
                            exception.getMessage() != null ? exception.getMessage() : "Unable to start FTP server.");
                }
            }
        });
    }

    public void stopServer(final ServerListener listener) {
        executor.execute(() -> {
            synchronized (this) {
                if (server != null && !server.isStopped()) {
                    server.stop();
                    server = null;
                    port = 0;
                    address = null;
                    if (listener != null) {
                        listener.onLog("Server stopped.");
                        listener.onStopped();
                    }
                } else if (listener != null) {
                    listener.onLog("FTP server is not running.");
                    listener.onStopped();
                }
            }
        });
    }
}
