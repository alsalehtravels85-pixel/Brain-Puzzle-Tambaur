package com.cybercity.brainpuzzle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateManager {
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/main/update.json";
    private static final String RELEASE_URL =
            "https://github.com/alsalehtravels85-pixel/Brain-Puzzle-Tambaur/releases/latest/download/TambaurBubbleShooter.apk";
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
                int currentCode = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
                if (latestCode > currentCode && !activity.isFinishing()) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle("Update available" + (latestName.isEmpty() ? "" : " • " + latestName))
                            .setMessage(notes + "\n\nDownload the latest APK now?")
                            .setPositiveButton("Download APK", (dialog, which) -> {
                                try {
                                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_URL)));
                                } catch (Exception ignored) { }
                            })
                            .setNegativeButton("Later", null)
                            .show());
                }
            } catch (Exception ignored) {
                // Optional update check; the game remains playable offline.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }
}
