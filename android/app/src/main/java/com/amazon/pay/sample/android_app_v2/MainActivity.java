package com.amazon.pay.sample.android_app_v2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    static volatile String token = null;
    static volatile String webviewUrl = null;

    private WebView webView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);

        // enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

        // enable Web Storage
        webView.getSettings().setDomStorageEnabled(true);

        // allow redirect by JavaScript
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // redirect console log into AndroidStudio's Run console.
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cslMsg) {
                Log.d("MyApp", cslMsg.message() + " at line "
                        + cslMsg.lineNumber() + " of "
                        + cslMsg.sourceId());
                return super.onConsoleMessage(cslMsg);
            }
        });

        webView.addJavascriptInterface(this, "androidApp");

        webView.loadUrl("https://10.0.2.2:3443/sample/cart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        String url = webviewUrl;
        if (url != null) {
            webviewUrl = null;
            webView.loadUrl("javascript:loadUrl('" + url + "')");
        } else {
            webView.loadUrl("javascript:if(window.uncoverScreen) {uncoverScreen();}");
        }
    }

    void invokeAppLoginPage(Context context) {
        token = UUID.randomUUID().toString();
        invokeSecureWebview(context, "https://10.0.2.2:3443/appLogin?client=androidApp&token=" + token);
    }

    void invokeAuthorizePage(Context context, String url) {
        invokeSecureWebview(context, url);
    }

    private void invokeSecureWebview(Context context, String url) {
        CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder().build();

        // Specify Chrome as the browser to be launched since Amazon Pay only supports it
        // Refer: https://pay.amazon.com/help/202023380
        // In case you have to support other browsers supporting Custom Tabs protocol, try by checking the source code below
        // Refer: https://github.com/GoogleChrome/custom-tabs-client/blob/master/shared/src/main/java/org/chromium/customtabsclient/shared/CustomTabsHelper.java#L64
        tabsIntent.intent.setPackage("com.android.chrome");

        // Set a flag to automatically close Chrome Custom Tabs when another Activity is invoked
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Set a flag to prevent Chrome Custom Tabs from remaining as a History process when it is closed
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        // Launch Chrome Custom Tabs
        tabsIntent.launchUrl(context, Uri.parse(url));
    }

    @JavascriptInterface
    public void login() {
        Log.d("[JsCallback]", "login");
        invokeAppLoginPage(getApplicationContext());
    }

    @JavascriptInterface
    public void auth(String url) {
        Log.d("[JsCallback]", "auth");
        invokeAuthorizePage(getApplicationContext(), url);
    }

}
