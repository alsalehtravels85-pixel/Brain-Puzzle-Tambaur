package com.cybercity.brainpuzzle;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    private WebView gameView;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF111827);
        getWindow().setNavigationBarColor(0xFF111827);
        gameView = new WebView(this);
        gameView.setBackgroundColor(0xFF111827);
        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        gameView.setWebChromeClient(new WebChromeClient());
        setContentView(gameView);
        gameView.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed() {
        if (gameView != null && gameView.canGoBack()) gameView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (gameView != null) { gameView.destroy(); gameView = null; }
        super.onDestroy();
    }
}
