package com.cybercity.brainpuzzle;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateManager {
    private static final String VERSION_URL = "https://raw.githubusercontent.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/main/update.json";
    private static final String APK_URL = "https://github.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/releases/latest/download/TambaurBubbleShooter.apk";
    private static final String APK_NAME = "TambaurBubbleShooter-update.apk";
    private static final String PREFS = "tbs_update";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_INSTALL_PENDING = "install_pending";
    private static Activity currentActivity;
    private static Context appContext;
    private static boolean receiverRegistered;
    private static boolean installerLaunching;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static int monitorAttempts;

    // Keep a strong reference: a locally-created receiver can be garbage-collected
    // before DownloadManager sends ACTION_DOWNLOAD_COMPLETE.
    private static final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            Context c = context.getApplicationContext();
            long expected = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DOWNLOAD_ID, -1L);
            if (completedId != expected || expected < 0) return;

            DownloadManager manager = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(completedId);
            try (Cursor cursor = manager.query(query)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    Toast.makeText(c, "Update download status could not be checked.", Toast.LENGTH_LONG).show();
                    return;
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putBoolean(KEY_INSTALL_PENDING, true).apply();
                    Activity a = currentActivity;
                    if (a != null && !a.isFinishing()) a.runOnUiThread(() -> resumeInstall(a));
                } else {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    Toast.makeText(c, "Update download failed (" + reason + "). Please try again.", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(c, "Download finished. Reopen the game to continue installation.", Toast.LENGTH_LONG).show();
            }
        }
    };

    private UpdateManager() {}

    public static void check(Activity activity) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setUseCaches(false);
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject data = new JSONObject(body.toString());
                int latestCode = data.optInt("versionCode", 0);
                String latestName = data.optString("versionName", "");
                String notes = data.optString("releaseNotes", "New improvements are available.");
                int currentCode;
                if (Build.VERSION.SDK_INT >= 28) {
                    currentCode = (int) activity.getPackageManager()
                            .getPackageInfo(activity.getPackageName(), 0).getLongVersionCode();
                } else {
                    currentCode = activity.getPackageManager()
                            .getPackageInfo(activity.getPackageName(), 0).versionCode;
                }
                if (latestCode > currentCode && !activity.isFinishing()) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle("Update available • " + latestName)
                            .setMessage(notes + "\n\nTap UPDATE NOW. The installer will open automatically after the APK finishes downloading.")
                            .setPositiveButton("UPDATE NOW", (dialog, which) -> downloadAndInstall(activity))
                            .setNegativeButton("LATER", null)
                            .show());
                }
            } catch (Exception ignored) {
                // The game remains playable when the optional update check is offline.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static void downloadAndInstall(Activity activity) {
        try {
            currentActivity = activity;
            appContext = activity.getApplicationContext();
            registerReceiver(appContext);

            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Allow app updates")
                        .setMessage("Allow Tambaur Bubble Shooter to install its downloaded update. Return to the game; installation will continue automatically.")
                        .setPositiveButton("OPEN SETTINGS", (d, w) -> {
                            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(settings);
                        })
                        .setNegativeButton("CANCEL", null).show();
                return;
            }

            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Download service unavailable");

            // Avoid queuing duplicate copies if the user taps UPDATE NOW more than once.
            long previousId = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getLong(KEY_DOWNLOAD_ID, -1L);
            if (previousId >= 0) {
                DownloadManager.Query oldQuery = new DownloadManager.Query().setFilterById(previousId);
                try (Cursor cursor = manager.query(oldQuery)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int oldStatus = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (oldStatus == DownloadManager.STATUS_RUNNING || oldStatus == DownloadManager.STATUS_PENDING) {
                            Toast.makeText(activity, "Update is already downloading. Installer will open when ready.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
            }

            File oldApk = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME);
            if (oldApk.exists()) oldApk.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(APK_URL));
            request.setTitle("Tambaur Bubble Shooter update");
            request.setDescription("Downloading update. Installer opens automatically when complete.");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_NAME);
            request.setMimeType("application/vnd.android.package-archive");
            long id = manager.enqueue(request);
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_DOWNLOAD_ID, id).putBoolean(KEY_INSTALL_PENDING, false).apply();
            Toast.makeText(activity, "Update downloading. Installer will open automatically when ready.", Toast.LENGTH_LONG).show();
            startDownloadMonitor(activity, id);
        } catch (Exception e) {
            Toast.makeText(activity, "Update could not start. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    // Poll DownloadManager while the game is visible as a fallback for delayed broadcasts.
    private static void startDownloadMonitor(Activity activity, long id) {
        monitorAttempts = 0;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing()) return;
                monitorAttempts++;
                try {
                    DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                    try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_INSTALL_PENDING, true).apply();
                                resumeInstall(activity);
                                return;
                            }
                            if (status == DownloadManager.STATUS_FAILED) {
                                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                                Toast.makeText(activity, "Update download failed (" + reason + "). Please try again.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) { }
                if (monitorAttempts < 1800) mainHandler.postDelayed(this, 1000L);
            }
        });
    }
    public static void resumeInstall(Activity activity) {
        currentActivity = activity;
        appContext = activity.getApplicationContext();
        try {
            registerReceiver(appContext);
            android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long id = prefs.getLong(KEY_DOWNLOAD_ID, -1L);
            boolean pending = prefs.getBoolean(KEY_INSTALL_PENDING, false);

            // Recover completion if Android delivered the broadcast while the app was backgrounded.
            if (!pending && id >= 0) {
                DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            prefs.edit().putBoolean(KEY_INSTALL_PENDING, true).apply();
                            pending = true;
                        }
                    }
                }
            }

            if (!pending || installerLaunching) return;
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("One permission needed")
                        .setMessage("Allow Tambaur Bubble Shooter to install updates. After allowing, return to the game and the installer will open.")
                        .setPositiveButton("ALLOW", (d, w) -> activity.startActivity(new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()))))
                        .setNegativeButton("LATER", null).show();
                return;
            }

            File apk = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME);
            if (!apk.exists() || apk.length() < 100_000L) {
                Toast.makeText(activity, "Downloaded update file is missing or incomplete. Please download again.", Toast.LENGTH_LONG).show();
                return;
            }
            openInstaller(activity, apk);
            prefs.edit().putBoolean(KEY_INSTALL_PENDING, false).apply();
        } catch (Exception e) {
            installerLaunching = false;
            Toast.makeText(activity, "Could not open installer. Please tap the update notification or try again.", Toast.LENGTH_LONG).show();
        }
    }

    private static void openInstaller(Activity activity, File apk) throws Exception {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setData(uri);
        install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        install.setClipData(ClipData.newRawUri("Tambaur Bubble Shooter update", uri));
        installerLaunching = true;
        activity.startActivity(install);
    }

    private static void registerReceiver(Context context) {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(downloadReceiver, filter);
        receiverRegistered = true;
    }
}
