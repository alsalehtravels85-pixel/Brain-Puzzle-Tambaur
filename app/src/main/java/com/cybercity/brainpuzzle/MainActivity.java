package com.cybercity.brainpuzzle;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.view.ViewGroup;
import android.view.View;
import android.content.pm.ActivityInfo;
import android.content.Intent;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class MainActivity extends Activity {
    private static final boolean TEST_ADS = BuildConfig.DEBUG;
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    private static final String LIVE_BANNER = "ca-app-pub-9762635033302606/4968501939";
    private static final String LIVE_INTERSTITIAL = "ca-app-pub-9762635033302606/1539558663";
    private static final String LIVE_REWARDED = "ca-app-pub-9762635033302606/7188517343";

    private WebView gameView;
    private AdView bannerAd;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private long lastInterstitialAt = 0L;
    private boolean adsReady = false;

    private String bannerId() { return TEST_ADS ? TEST_BANNER : LIVE_BANNER; }
    private String interstitialId() { return TEST_ADS ? TEST_INTERSTITIAL : LIVE_INTERSTITIAL; }
    private String rewardedId() { return TEST_ADS ? TEST_REWARDED : LIVE_REWARDED; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applyImmersiveMode();
        getWindow().setStatusBarColor(0xFF111827);
        getWindow().setNavigationBarColor(0xFF111827);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF111827);

        gameView = new WebView(this);
        gameView.setBackgroundColor(0xFF111827);
        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        gameView.setWebChromeClient(new WebChromeClient());
        gameView.addJavascriptInterface(new GameAdsBridge(), "AndroidAds");
        root.addView(gameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Keep the AdMob banner visible below the game.
        bannerAd = new AdView(this);
        bannerAd.setAdUnitId(bannerId());
        int adWidth = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        bannerAd.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth));
        root.addView(bannerAd, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        gameView.loadUrl("file:///android_asset/index.html");
        UpdateManager.check(this);

        MobileAds.initialize(this, initializationStatus -> runOnUiThread(() -> {
            adsReady = true;
            bannerAd.loadAd(new AdRequest.Builder().build());
            loadInterstitial();
            loadRewarded();
        }));
    }

    private void loadInterstitial() {
        if (!adsReady || isFinishing()) return;
        InterstitialAd.load(this, interstitialId(), new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) {
                interstitialAd = ad;
                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() { interstitialAd = null; loadInterstitial(); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError error) { interstitialAd = null; loadInterstitial(); }
                });
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { interstitialAd = null; }
        });
    }

    private void loadRewarded() {
        if (!adsReady || isFinishing()) return;
        RewardedAd.load(this, rewardedId(), new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) {
                rewardedAd = ad;
                rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() { rewardedAd = null; loadRewarded(); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError error) { rewardedAd = null; loadRewarded(); }
                });
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { rewardedAd = null; }
        });
    }

    private void showInterstitialAtBreak() {
        long now = System.currentTimeMillis();
        if (interstitialAd == null || now - lastInterstitialAt < 120000L) return;
        lastInterstitialAt = now;
        interstitialAd.show(this);
    }

    private void showRewardedForBonus() {
        if (rewardedAd == null) {
            Toast.makeText(this, "Reward ad is loading. Please try again shortly.", Toast.LENGTH_SHORT).show();
            loadRewarded();
            return;
        }
        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        ad.show(this, (RewardItem reward) -> {
            if (gameView != null) gameView.evaluateJavascript("window.onNativeRewarded && window.onNativeRewarded();", null);
        });
    }

    private class GameAdsBridge {
        @JavascriptInterface public void shareApp() { runOnUiThread(() -> {
            String link = "https://alsalehtravels85-pixel.github.io/Brain-Puzzle-Tambaur/download.html";
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Tambaur Bubble Shooter");
            send.putExtra(Intent.EXTRA_TEXT, "Play Tambaur Bubble Shooter! Download here: " + link);
            startActivity(Intent.createChooser(send, "Share Tambaur Bubble Shooter"));
        }); }
        @JavascriptInterface public void showInterstitial() { runOnUiThread(() -> showInterstitialAtBreak()); }
        @JavascriptInterface public void showRewarded() { runOnUiThread(() -> showRewardedForBonus()); }
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override protected void onPause() {
        if (bannerAd != null) bannerAd.pause();
        if (gameView != null) gameView.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bannerAd != null) bannerAd.resume();
        if (gameView != null) gameView.onResume();
    }

    @Override public void onBackPressed() {
        if (gameView != null && gameView.canGoBack()) gameView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (bannerAd != null) { bannerAd.destroy(); bannerAd = null; }
        if (gameView != null) { gameView.destroy(); gameView = null; }
        super.onDestroy();
    }
}
