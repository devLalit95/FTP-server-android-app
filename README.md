# Android FTP Server

A premium, modern FTP server application for Android built with Material 3 and Apache FtpServer. This app allows you to share your entire Android device storage with other devices (like laptops or other phones) over a local Wi-Fi network.

## 🚀 Features
- **Modern UI**: Clean, premium Material 3 design with card-based layouts.
- **Full Storage Access**: Share the absolute root of internal storage.
- **Detailed Logs**: Real-time diagnostic logs with timestamps for troubleshooting.
- **Secure/Anonymous Modes**: Supports both password-protected and anonymous access.
- **Auto-Timeout**: 10-second safety timeout for server initialization.
- **Persistent Service**: Runs as a Foreground Service to prevent the system from killing it.

---

## 🛠 Troubleshooting & Lessons Learned (Problems We Fixed)

During the development of this app, we solved several critical issues related to modern Android security and API changes:

### 1. Foreground Service Type Error (Android 14+)
- **Problem**: The app would fail to build or crash because Android 14 (API 34) requires specific types for foreground services. Additionally, accessing media files (audio/video) would cause the server to stop if the correct types weren't declared.
- **Solution**: We added `foregroundServiceType="dataSync|mediaPlayback|mediaProcessing"` in the `AndroidManifest.xml` and requested the corresponding `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `FOREGROUND_SERVICE_MEDIA_PROCESSING` permissions. We removed `connectedDevice` as it was causing permission security exceptions.

### 2. BroadcastReceiver Security Exception
- **Problem**: `java.lang.SecurityException` on startup. Android 14 requires a flag (`RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`) when registering receivers.
- **Solution**: Updated the registration to use `ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_NOT_EXPORTED)`.

### 3. Missing Files on Connected Devices (Android 11-13)
- **Problem**: Users could connect to the FTP server but saw no files or an empty directory.
- **Solution**: 
    - Implemented **MANAGE_EXTERNAL_STORAGE** ("All Files Access").
    - Updated `resolveHomeDirectory` to point to `Environment.getExternalStorageDirectory()` (the `/sdcard` root).
    - Added a system settings redirect to allow the user to grant high-level storage access.

### 4. Packaging Conflict (META-INF)
- **Problem**: Build error: `3 files found with path 'META-INF/DEPENDENCIES'`.
- **Solution**: Added a `packaging` block in `build.gradle.kts` to exclude conflicting dependency metadata.

---

## 📥 Installation & Setup

If you are installing this app or building it from source, follow these steps to ensure it works correctly:

### Prerequisites
- Android device running **Android 7.0 (API 24)** or higher.
- Both the phone and the client (laptop) must be on the **same Wi-Fi network**.

### Step-by-Step Setup
1. **Install and Open**: Launch the app on your device.
2. **Grant Permissions**:
   - The app will prompt for **Notification permissions** (to show the service status).
   - **CRITICAL**: On Android 11+, the app will take you to a system settings page. Find this app and toggle **"Allow access to manage all files"** to **ON**. This is required to see your files over FTP.
3. **Configure**:
   - Set a **Username** and **Password** (or enable **Anonymous Access**).
   - The default port is `2121`.
4. **Start Server**: Tap the **Start Server** button.
5. **Connect**:
   - Open your File Explorer (Windows) or Finder (Mac) on your laptop.
   - Enter the URL shown in the app (e.g., `ftp://192.168.1.5:2121`).
   - Log in with the credentials you set.

---

## 🏗 Built With
- **Apache FtpServer**: Core FTP engine.
- **Material 3**: User interface components.
- **ViewBinding**: Safe interaction with UI elements.
- **ViewModel & LiveData**: Reactive UI state management.
