package com.example.ftpserver;

import android.content.Context;
import android.content.SharedPreferences;

public class ServerPreferences {
    private static final String PREFERENCES_NAME = "ftp_server_prefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_PORT = "port";
    private static final String KEY_ANONYMOUS = "anonymous_enabled";
    private static final String KEY_SECURE = "secure_enabled";

    private final SharedPreferences prefs;

    public ServerPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "lalit");
    }

    public void setUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, "lalit");
    }

    public void setPassword(String password) {
        prefs.edit().putString(KEY_PASSWORD, password).apply();
    }

    public int getPort() {
        return prefs.getInt(KEY_PORT, 2221);
    }

    public void setPort(int port) {
        prefs.edit().putInt(KEY_PORT, port).apply();
    }

    public boolean isAnonymousEnabled() {
        return prefs.getBoolean(KEY_ANONYMOUS, true);
    }

    public void setAnonymousEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ANONYMOUS, enabled).apply();
    }

    public boolean isSecureEnabled() {
        return prefs.getBoolean(KEY_SECURE, true);
    }

    public void setSecureEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SECURE, enabled).apply();
    }
}
