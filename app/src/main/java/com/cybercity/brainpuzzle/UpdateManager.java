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

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateManager {
    private static final String VERSION_URL = "https://raw.githubusercontent.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/main/update.json";
    private static final String APK_URL = "https://github.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/releases/latest/download/TambaurBubbleShooter.apk";
    private static final String APK_NAME = "TambaurBubbleShooter-update.apk";
    private static boolean receiverRegistered = false;
    private static long activeDownloadId = -1;
    private static Activity currentActivity;

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
                int currentCode = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
                if (latestCode > currentCode && !activity.isFinishing()) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle("Update available • " + latestName)
                            .setMessage(notes + "\n\nTap UPDATE NOW to download and open the installer directly inside the app.")
                            .setPositiveButton("UPDATE NOW", (dialog, which) -> downloadAndInstall(activity))
                            .setNegativeButton("LATER", null)
                            .show());
                }
            } catch (Exception ignored) {
                // Optional update check; game remains playable if offline.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static void downloadAndInstall(Activity activity) {
        try {
            currentActivity = activity;
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Allow app updates")
                        .setMessage("Android needs permission to install updates downloaded from this app. Allow Tambaur Bubble Shooter, then tap UPDATE NOW again.")
                        .setPositiveButton("OPEN SETTINGS", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        })
                        .setNegativeButton("CANCEL", null).show();
                return;
            }
            registerReceiver(activity);
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(APK_URL));
            request.setTitle("Tambaur Bubble Shooter update");
            request.setDescription("Downloading the latest game version…");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_NAME);
            request.setMimeType("application/vnd.android.package-archive");
            activeDownloadId = manager.enqueue(request);
            Toast.makeText(activity, "Update downloading. Installer will open when ready.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Update download could not start. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private static void registerReceiver(Activity activity) {
        if (receiverRegistered) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != activeDownloadId) return;
                try {
                    Activity a = currentActivity;
                    if (a == null || a.isFinishing()) return;
                    File apk = new File(a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME);
                    if (!apk.exists() || apk.length() == 0) throw new IllegalStateException("APK missing");
                    Uri apkUri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(install);
                } catch (Exception e) {
                    Toast.makeText(context, "Download finished, but installer could not open. Open the notification to retry.", Toast.LENGTH_LONG).show();
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
        receiverRegistered = true;
    }
}
