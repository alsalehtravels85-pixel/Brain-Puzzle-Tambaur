package com.cybercity.brainpuzzle;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateManager {
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/main/update.json";
    private static final String APK_URL =
            "https://github.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/releases/latest/download/TambaurBubbleShooter.apk";

    private static long downloadId = -1L;
    private static Uri pendingInstallUri;
    private static BroadcastReceiver downloadReceiver;
    private static boolean receiverRegistered = false;

    private UpdateManager() {}

    public static void check(Activity activity) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestMethod("GET");
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
                int currentCode = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0).versionCode;
                if (latestCode > currentCode && !activity.isFinishing()) {
                    activity.runOnUiThread(() -> showUpdateDialog(activity, latestName, notes));
                }
            } catch (Exception ignored) {
                // Update check is optional; the game continues normally when offline.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static void showUpdateDialog(Activity activity, String version, String notes) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("New update available" + (version.isEmpty() ? "" : " • " + version))
                .setMessage(notes + "\n\nWould you like to download and install the latest version?")
                .setPositiveButton("Update now", (dialog, which) -> downloadUpdate(activity))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void downloadUpdate(Activity activity) {
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(APK_URL));
            request.setTitle("Tambaur Bubble Shooter update");
            request.setDescription("Downloading the latest game version");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "TambaurBubbleShooter-update.apk");

            if (downloadReceiver == null) {
                downloadReceiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                        if (completedId != downloadId) return;
                        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                        Uri fileUri = dm.getUriForDownloadedFile(downloadId);
                        if (fileUri == null) {
                            Toast.makeText(context, "Update download failed. Please try again.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        pendingInstallUri = fileUri;
                        launchInstallerIfAllowed((Activity) context);
                    }
                };
            }
            if (!receiverRegistered) {
                IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
                else activity.registerReceiver(downloadReceiver, filter);
                receiverRegistered = true;
            }
            downloadId = manager.enqueue(request);
            Toast.makeText(activity, "Update downloading. You will get a notification when it is ready.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Could not start update download.", Toast.LENGTH_LONG).show();
        }
    }

    private static void launchInstallerIfAllowed(Activity activity) {
        if (pendingInstallUri == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= 26 &&
                !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Allow app updates")
                    .setMessage("Android needs permission to install updates downloaded from this app. Allow Tambaur Bubble Shooter, then return here.")
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(pendingInstallUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
            pendingInstallUri = null;
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to open the installer. Please use the download notification.", Toast.LENGTH_LONG).show();
        }
    }

    public static void resumeInstall(Activity activity) {
        if (pendingInstallUri != null) launchInstallerIfAllowed(activity);
    }

    public static void unregister(Activity activity) {
        if (receiverRegistered && downloadReceiver != null) {
            try { activity.unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }
}
