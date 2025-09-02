package com.example.myapp2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    static volatile String token = null;
    static volatile String webviewUrl = null;
    private WebView myWebView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        myWebView = (WebView) findViewById(R.id.webview);

        myWebView.getSettings().setJavaScriptEnabled(true);
        myWebView.getSettings().setDomStorageEnabled(true);
        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cslMsg) {
                Log.d("WebView", cslMsg.message() + " - at line " + cslMsg.lineNumber() + " of " + cslMsg.sourceId());
                return true;
            }
        });
        myWebView.addJavascriptInterface(this, "androidApp");
        myWebView.loadUrl("http://10.0.2.2:3080/sample/cart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        String url = webviewUrl;
        if (url != null) {
            webviewUrl = null;
            myWebView.loadUrl("javascript:loadUrl('" + url + "')");
        } else {
            myWebView.loadUrl("javascript:if(window.uncoverScreen) {uncoverScreen();}");
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
        Intent intent = new Intent(context, AmazonPayActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }

    @JavascriptInterface
    public void login() {
        Log.d("[JsCallback]", "login");
        invokeAppLoginPage(this);
    }

    @JavascriptInterface
    public void auth(String url) {
        Log.d("[JsCallback]", "auth");
        invokeAuthorizePage(this, url);
    }
}