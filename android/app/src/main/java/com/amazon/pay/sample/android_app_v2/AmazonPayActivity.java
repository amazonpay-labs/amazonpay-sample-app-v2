package com.amazon.pay.sample.android_app_v2;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AmazonPayActivity extends AppCompatActivity {

    private boolean isKicked = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amazon_pay);
        this.isKicked = true;

        Intent intent = getIntent();
        Log.d("[Intent]", "intent received!");
        Log.d("[Intent]", intent.getStringExtra("url"));
        invokeSecureWebview(this, intent.getStringExtra("url"));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        this.isKicked = true;

        if (intent.getScheme().equals("https")) {
            String appLinkAction = intent.getAction();
            Uri appLinkData = intent.getData();
            Log.d("[AppLink]", appLinkAction);
            Log.d("[AppLink]", "" + appLinkData);

            // URLパラメタのパース
            Map<String, String> map = new HashMap<>();
            for (String kEqV : appLinkData.getEncodedQuery().split("&")) {
                String[] kv = kEqV.split("=");
                map.put(kv[0], kv[1]);
            }

            if (MainActivity.token.equals(map.get("token"))) { // tokenの一致判定
                // 一致した場合には、購入ページのURLをMainActivityに設定
                MainActivity.webviewUrl = "/sample/checkoutReview?amazonCheckoutSessionId=" + map.get("amazonCheckoutSessionId");
            } else {
                // 不一致の場合には不正な遷移であるため、エラーページを設定
                MainActivity.webviewUrl = "/static/sample/error.html";
            }

        } else {
            Log.d("[Intent]", "intent received!");
            Log.d("[Intent]", intent.getStringExtra("mode"));
            if(intent.getStringExtra("mode").equals("thanks")) {
                MainActivity.webviewUrl = "/sample/thanks";
            } else {
                MainActivity.webviewUrl = "/sample/cart";
            }
        }

        // 本Activityのfinish. (この後、MainActivity#onResumeに処理が移る)
        this.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Secure WebViewを左上の「X」ボタンで閉じられた場合、元の画面に戻すために本Activityを自動でfinishさせる
        if(!isKicked) {
            this.finish();
        }
        isKicked = false;
    }

    private void invokeSecureWebview(Context context, String url) {
        CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder().build();

        // 起動するBrowserにChromeを指定
        // Note: Amazon Payでは他のブラウザがサポート対象に入っていないため、ここではChromeを指定している.
        // [参考] https://pay.amazon.com/jp/help/202030010
        // もしその他のChrome Custom Tabs対応のブラウザを起動する必要がある場合には、下記リンク先ソースなどを参考に実装する.
        // [参考] https://github.com/GoogleChrome/custom-tabs-client/blob/master/shared/src/main/java/org/chromium/customtabsclient/shared/CustomTabsHelper.java#L64
        tabsIntent.intent.setPackage("com.android.chrome");

        // Chrome Custom Tabsの起動
        tabsIntent.launchUrl(context, Uri.parse(url));
    }
}
