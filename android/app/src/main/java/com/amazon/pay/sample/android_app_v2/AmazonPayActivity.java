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

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amazon_pay);

        Intent intent = getIntent();
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
            MainActivity.webviewUrl = "/sample/thanks";
        }

        // 本Activityのfinish. (この後、MainActivity#onResumeに処理が移る)
        this.finish();
    }

}
