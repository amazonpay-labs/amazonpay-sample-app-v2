# Amazon Pay モバイル サンプルアプリ Androidアプリの実装について
本サンプルアプリの、Androidアプリ側の実装です。インストールして動作させる方法については、[こちら](./README_install.md)をご参照下さい。

## 動作環境
Android 7以降: Google Chrome 64以降  
[参考] https://pay.amazon.com/jp/help/202030010

# その他の前提条件
本サンプルアプリではApplinksという技術を使っており、こちらを利用するためには下記の条件が必要です。
 - Web上のhttpsで正しくアクセスできる場所に設定ファイルを配置する必要があるので、ECサイトとは別ドメインのサーバーか、AWS等のクラウドサービスのアカウントを保有していること  
   Note: 本サンプルアプリでは、[Amazon S3](https://aws.amazon.com/jp/s3/)を利用しています。こちらはアカウントをInternet上で簡単に取得でき、世界中で広く使われており、利用方法などの情報も多く、12ヶ月間 5GBの無料利用枠もあるため、お勧めです。  

## 概要
本サンプルアプリは、下記動画のように動作いたします。

<img src="docimg/android-movie.gif" width="300">  

フローの詳細は、[flow-android.xlsx](./flow-android.xlsx) をご参照ください。  
こちらのフローをベースに、以後詳細な実装方法について解説します。

# Amazon Payの実装方法 - WebViewアプリ編

## AndroidManifest.xmlの設定

Secure WebView(Chrome Custom Tabs)のOpenとClose処理を担当する、AmazonPayActivityでは、下記のようにlaunchModeに「singleTask」を指定します。  

```xml
        <activity android:name=".AmazonPayActivity"
            android:launchMode="singleTask">
```

理由や詳細については、[こちら](./README_fixSwitchApp.md)をご参照下さい。  

## カートページ

<img src="docimg/cart.png" width="500">  

### モバイルアプリのJavaScript側からのCallback受付の設定
モバイルアプリではAmazon Payの処理はSecure WebView上で実行する必要がありますが、WebViewから直接Secure WebViewは起動できないため、WebViewのJavaScriptから一旦Nativeコードを起動できるよう設定する必要があります。  
それを行うのが下記のコードです。  

```java
// MainActivity.javaから抜粋 (見やすくするため、一部加工しています。)

    protected void onCreate(Bundle savedInstanceState) {
                :
        webView.addJavascriptInterface(this, "androidApp");
                :
    }
                :
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
```

このように設定すると、下記のようにJavaScript側からNative側のメソッドを呼び出すことが可能になります。
```js
                androidApp.login();
```

### クライアント判定
本サンプルアプリでは、同一のHTML/JavaScriptの画面でAndroid/iOS/通常のBrowserの全てに対応しております。  
そのため、動作環境に応じて処理を切り替える必要がある場合には、クライアントを判定して条件分岐を行う必要があります。  
それを行っているのが、下記のJavaScriptのコードです。

```js
// nodejs/views/sample/cart.ejsより抜粋

    let client = "browser";
    if(window.androidApp) {
        client = "androidApp";
    } else if(window.webkit && webkit.messageHandlers && webkit.messageHandlers.iosApp) {
        client = "iosApp";
    }
    document.cookie = "client=" + client + ";path=/;secure";
```

上記「モバイルアプリのJavaScript側からのCallback受付の設定」で設定されたCallback用のObjectの存在確認を行うことで、それぞれ何の環境なのかを判定しています。  
判定結果はServer側でも参照できるよう、Cookieに設定しています。  

### 「Amazon Payボタン」画像の配置

Amamzon Payで支払いができることをユーザに視覚的に伝えるのには、Amazon Payボタンを画面に表示するのが効果的です。  
WebView上では本物のAmazon Payボタンを配置できないので、ここでは画像を代わりに配置しています。

それを行っているのが、下記のJavaScriptです。
```js
// nodejs/views/sample/cart.ejsより抜粋 (見やすくするため、一部加工しています。)

    if(client === 'browser') {
        amazon.Pay.renderButton('#AmazonPayButton', {
            :
        });
    } else {
        let node = document.createElement("input");
        node.type = "image";
        node.src = "/static/img/button_images/Sandbox-live-ja_jp-amazonpay-gold-large-button_T2.png";
        node.addEventListener('click', (e) => {
            coverScreen();
            if(client === 'androidApp') {
                // → Androidの場合. 
                androidApp.login();
            } else {
                webkit.messageHandlers.iosApp.postMessage({op: 'login'});
            }
        });
        document.getElementById("AmazonPayButton").appendChild(node);
    }
```

最初の判定で、通常のBrowserだった場合にはそのままAmazon Payの処理が実施できるので、通常通りAmazon Payボタンを読み込んでいます。  
Androidの場合は、「Amazon Payボタン」画像のnodeを生成して同画面内の「AmazonPayButton」ノードの下に追加しています。  
この時指定する「Amazon Payボタン」画像は「./nodejs/static/img/button_images」の下にあるものから選ぶようにして下さい。なお、本番環境向けにファイル名が「Sandbox_」で始まるものを指定しないよう、ご注意下さい。  
また、この生成したnodeがclickされたとき、「login」を指定したObjectをパラメタとして、Native側のCallbackを呼び出すEvent Handlerをaddしています。  

### 「Amazon Payボタン」画像クリック時の、Secure WebViewの起動処理
上記、「Amazon Payボタン」画像がクリックされたときに呼び出されるNative側のコードが、下記になります。  

```java
// MainActivity.javaから抜粋 (見やすくするため、一部加工しています。)

    @JavascriptInterface
    public void login() {
        Log.d("[JsCallback]", "login");
        invokeAppLoginPage(this);
    }
```

「invokeAppLoginPage()」の処理が、下記になります。これにより、AmazonPayActivityが起動されます。  
※ なお、UUID(version 4)を生成して「token」という名前で、Native側のFieldとURLのパラメタとして設定していますが、こちらの理由については後述します。  

```java
// MainActivity.javaから抜粋 (見やすくするため、一部加工しています。)

        :
    static volatile String token = null;
        :
    void invokeAppLoginPage(Context context) {
        token = UUID.randomUUID().toString();
        invokeSecureWebview(context, "https://10.0.2.2:3443/appLogin?client=androidApp&token=" + token);
    }
        :
    private void invokeSecureWebview(Context context, String url) {
        Intent intent = new Intent(context, AmazonPayActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }
```

起動されたAmazonPayActivity側の処理が下記です。  
URLを指定して、Chrome Custom Tabs(Android側のSecure WebView)を起動しているのが分かると思います。  

```java
public class AmazonPayActivity extends AppCompatActivity {
        :
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amazon_pay);
        this.isKicked = true;

        Intent intent = getIntent();
        Log.d("[Intent]", "intent received!");
        Log.d("[Intent]", intent.getStringExtra("url"));
        invokeSecureWebview(this, intent.getStringExtra("url"));
    }
        :
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
```

## 自動的にAmazonログイン画面に遷移させるページ

<img src="docimg/appLogin.png" width="500">  

こちらの画面ではAmazon Payが用意した「initCheckout」というメソッドをJavaScriptでcallすることで、Amazonログイン画面に遷移させています。  

### Server側のAmazon Payボタン出力準備
Amazon Payボタンを出力するための準備として、Server側にてAmazon Payボタンの出力に必要なpayloadと signatureの生成、その他の設定値の受け渡しを行います。  

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-------------------
// App Login Screen
//-------------------

app.get('/appLogin', async (req, res) => {
    // ※ req.queryには、上記ViewControllerで指定されたURLパラメタが入る
    res.render('appLogin.ejs', calcConfigs(`https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-${req.query.client}.html?token=${req.query.token}`));
});

function calcConfigs(url) {
    const payload = createPayload(url);
    const signature = apClient.generateButtonSignature(payload);
    return {payload: payload, signature: signature, merchantId: keyinfo.merchantId, publicKeyId: keyinfo.publicKeyId};
}

function createPayload(url) {
    return {
        webCheckoutDetails: {
            checkoutReviewReturnUrl: url
        },
        storeId: keyinfo.storeId
    };
}
```

指定されているURLの「https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/...」 はAmazon Payへのログイン & 住所・支払い方法の選択後のリダイレクト先になります。  
このURLは後述の「Applinks」という技術でSecure WebViewからNativeコードを起動するために使用されます。  

これらの値が「appLogin.ejs」にパラメタとして渡され、HTML & CSS & JavaScriptが生成されます。  

```html
<!-- nodejs/views/appLogin.ejsより抜粋 (見やすくするため、一部加工しています。) -->

    :
<script src="https://static-fe.payments-amazon.com/checkout.js"></script>
<script type="text/javascript" charset="utf-8">
    amazon.Pay.initCheckout({
        merchantId: '<%= merchantId %>',
        ledgerCurrency: 'JPY', // Amazon Pay account ledger currency
        sandbox: true, // dev environment
        checkoutLanguage: 'ja_JP', // render language
        productType: 'PayAndShip', // checkout type
        placement: 'Cart', // button placement
        createCheckoutSessionConfig: {
            payloadJSON: '<%- JSON.stringify(payload) %>', // string generated in step 2 (※ HTML Escapeをしないで出力する)
            signature: '<%= signature %>', // signature generated in step 3
            publicKeyId: '<%= publicKeyId %>' 
        }
    });    
</script>
```

この「initCheckout」メソッドの呼出により、自動的にAmazon Payのログイン画面に遷移させています。  
こちらのファイルは[EJS](https://ejs.co/)というTemplate Engineを使って作成されていますが、構文はTemplate Engineとしては一般的なものであるため、比較的簡単に理解できるかと思います。  

## Amazon側の画面からのリダイレクトによる、Applinksの発動

<img src="docimg/applinks.png" width="500">  

### Applinksについて
Applinksについての詳細については、[こちら](./README_swv2app.md)に記載しております。

Applinksの基本的な発動条件は「Chrome/Chrome Custom Tabs等でLinkをタップする」ことですが、ServerからのRedirectでも発動することがあります。  
Applinksが発動しなかった場合には、指定されたURLに存在するファイルが通常通りに表示されます。  

### 救済ページを使った2段構えのApplinksの発動
本サンプルでは、Amazon側のページでログイン＆住所・支払い方法の選択をしたあとのリダイレクトでApplinksが発動するURLを指定していますが、上記の理由により、ここでは発動しない可能性もあります。  

本サンプルではその場合の備えとして、発動しなかった場合には再度Applinksが発動するURLへのリンクを持つ、救済ページに自動的に遷移するように作られています。  
ここではその仕組を説明します。  

「自動的にAmazonログイン画面に遷移させるページ」で登場した、Applinksを発動させるURLのAndroid版は、下記になります。  
https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-androidApp.html?token=XXXXXXXX

上記でも書いたとおり、Applinksが発動しなかった場合には、指定されたURLに存在するファイルが表示されます。  
このURLの先には下記の内容のHTMLファイルが置かれております。  
```html
<!-- nodejs/linksの下にも同じものが置かれています。 -->

<html>
    <script>
        location.href = "https://10.0.2.2:3443/static/next.html" + location.search;
    </script>
</html>
```

こちらはファイルにアクセス時に指定されたURLパラメタを付与した上で、「next.html」にリダイレクトしています。  
Note: ↑はlocal環境用なのでリダイレクト先が「https://10.0.2.2:3443/static/next.html 」になっていますが、こちらは本番・各テスト等の環境に応じて変更する必要があります。  
「next.html」の中身が下記です。  
```html
<!-- nodejs/static/next.htmlより抜粋 -->

<body data-gr-c-s-loaded="true">
<div class="container">
    <h3 class="my-4">Amazon Login 処理完了</h3>
    「次へ」ボタンをタップして下さい。<br>
    <br>
    <a id="nextButton" href="#" class="btn btn-info btn-lg btn-block">
        次　へ
    </a>
</div>
<script>
    document.getElementById("nextButton").href = 
        "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/index.html" + location.search;
</script>
</body>
```

アクセス時に指定されたURLパラメタを付与したApplinksを発動するURLを「id="nextButton"」のリンクに指定しております。  
この仕組みにより、Applinksが発動しなかった場合にはこちらの画面が表示されます。この「次へ」のLinkをユーザがタップすることで、確実に条件を満たしてApplinksを発動させることができます。  

## 購入ページ

<img src="docimg/purchase.png" width="650">  

### tokenチェックとViewControllerへの遷移先URLの設定
Applinksにより起動されるNaiveコードは、下記になります。  

```java
// AmazonPayActivityより抜粋　(見やすくするため、一部加工しています。)

    protected void onNewIntent(Intent intent) {
                :
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

            if (MainActivity.token.equals(map.get("token"))) { // token
                MainActivity.webviewUrl = "/sample/checkoutReview?amazonCheckoutSessionId=" + map.get("amazonCheckoutSessionId");
            } else {
                MainActivity.webviewUrl = "/static/sample/error.html";
            }

        } else {
                :
        // 本Activityのfinish. (この後、MainActivity#onResumeに処理が移る)
        this.finish();
    }
```
※ 本サンプルではAmazonPayActivityが「singleTask」に設定されており、それによりSecure WebView(Chrome Custom Tabs)はAmazonPayActivityの起動により自動的にCloseされます。  

最初に、Applinks発動のURLに指定されていたURLパラメタを取得します。  

その後、「『Amazon Payボタン』画像クリック時の、Secure WebViewの起動処理」でMainActivityに保持したtokenと、Secure WebViewから受け渡されたtokenの一致判定を行っています。  
このtokenの判定を行うことで、不正な遷移でこの処理が起動された場合に、それを検出してエラーとできるようになります。

例えば、悪いユーザがSecure WebViewを起動する時の「自動的にAmazonログイン画面に遷移させるページ」へのURLを読み取って、メールなどで他のユーザに送ったとします。  
送りつけられたユーザがAndroid端末でメールのURLのリンクをクリックした場合、Chromeが立ち上がってAmazon Payログインに遷移してしまう可能性があります。  
もしそのままAmazon Payにログインして、住所・支払い方法選択も実施した場合、ChromeならApplinksも発動してしまいますので、同アプリをインストールしていればその後の購入フローも実行できることになってしまいます。  
画面のFlowによってはこれが大きな問題になる可能性もあるため、本サンプルアプリでは念のためにtokenチェックを行っております。  

tokenチェックの後は、購入ページのURLをMainActivityに設定します。  
購入ページのURLには「amazonCheckoutSessionId」をURLパラメタを付与しますが、これはPC・Mobileのブラウザでの購入ページへの遷移と全く同じURL・全く同じ条件になります。  
よって、この後の購入ページの表示では「モバイルアプリ向け」「PC・Mobileのブラウザ向け」で別々の処理を実装する必要はありません。  

最後に、AmazonPayActivityをfinishします。これにより、すぐ下のMainActivity#onResume処理が移ります。  

### 購入ページの読み込み

MainActivityでは、onResumeの中の下記の処理が起動します。  

```java
// MainActivity.javaより抜粋　(見やすくするため、一部加工しています。)

                    :
        String url = webviewUrl;
        if (url != null) {
            webviewUrl = null;
            webView.loadUrl("javascript:loadUrl('" + url + "')");
                    :
```

WebViewではこの時点でカートページが表示されており、上記にて下記のJavaScriptが起動して購入ページの読み込みが開始します。  

```js
    function loadUrl(url) {
        location.href = url;
    }
```

Server側では下記が実行されます。

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-------------------------
// Checkout Review Screen
//-------------------------
app.get('/sample/checkoutReview', async (req, res) => {
    // 受注情報
    let order = {host: req.headers.host, amazonCheckoutSessionId: req.query.amazonCheckoutSessionId,
        client: req.cookies.client, hd8: req.cookies.hd8, hd10: req.cookies.hd10, items: []};
    order.items.push({id: 'item0008', name: 'Fire HD8', price: 8980, num: parseInt(order.hd8)});
    order.items.push({id: 'item0010', name: 'Fire HD10', price: 15980, num: parseInt(order.hd10)});
    order.items.forEach(item => item.summary = item.price * item.num); // 小計
    order.price = order.items.map(item => item.summary).reduce((pre, cur) => pre + cur); // 合計金額
    order.chargeAmount = Math.floor(order.price * 1.1); // 税込金額

    // Amazon Pay受注情報
    const payload = await apClient.getCheckoutSession(req.query.amazonCheckoutSessionId, 
        {'x-amz-pay-idempotency-key': uuid.v4().toString().replace(/-/g, '')});
    order.checkoutSession = JSON.parse(payload.body);

    // Note: 一般的には受注情報はSessionやDBなどを使ってServer側に保持しますが、本サンプルではシンプルにするためにCookieを使用しています
    res.cookie('session', JSON.stringify(order), {secure: true});
    
    res.render('sample/checkoutReview.ejs', order);
});
```

cartの情報を計算して金額を出し、またAmazon Pay APIより住所情報等を取得し、template engineに渡して画面を生成して表示します。

### 購入ボタンクリック時の処理

購入ボタンをクリックすると、下記のScriptが実行されます。

```js
// nodejs/views/sample/checkoutReview.ejsより抜粋 (見やすくするため、一部加工しています。)

            :
    document.getElementById("purchaseButton").addEventListener('click', (e) => {
        $.ajax({
            type: 'POST',
            url: '/sample/checkoutSession',
            data: {},
        })
        .then(
            :
```

Ajaxにより、下記のServer側のCheckout Session Update APIが呼び出されます。  

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-----------------------------
// Checkout Session Update API
//-----------------------------

// 事業者側の受注番号の採番
const newMerchantReferenceId = function() {
    let currentNumber = 1;
    return function() {
        return "MY-ORDER-" + currentNumber++;
    }
} ();

app.post('/sample/checkoutSession', async (req, res) => {
    let order = JSON.parse(req.cookies.session);
    const payload = await updateCheckoutSession({merchantReferenceId: newMerchantReferenceId(),
        merchantStoreName: "MY-SHOP", noteToBuyer: "Thank you!", customInformation: "This isn't shared with Buyer", ...order});    
    order.checkoutSession = JSON.parse(payload.body);

    // Note: 一般的には受注情報はSessionやDBなどを使ってServer側に保持しますが、本サンプルではシンプルにするためにCookieを使用しています
    res.cookie('session', JSON.stringify(order), {secure: true});

    res.writeHead(200, {'Content-Type': 'application/json; charset=UTF-8'});
    res.write(payload.body);
    res.end()
});

async function updateCheckoutSession(data) {
    const url = data.client === 'browser' ? "https://localhost:3443/sample/thanks" :
        `https://${data.host}/static/dispatcher.html?client=${data.client}`;
    return await apClient.updateCheckoutSession(data.amazonCheckoutSessionId, {
        webCheckoutDetails: {
            checkoutResultReturnUrl: url
        },
        paymentDetails: {
            paymentIntent: 'Authorize',
            canHandlePendingAuthorization: false,
            chargeAmount: {
                amount: '' + data.chargeAmount,
                currencyCode: "JPY"
            }
        },
        merchantMetadata: {
            merchantReferenceId: data.merchantReferenceId,
            merchantStoreName: data.merchantStoreName,
            noteToBuyer: data.noteToBuyer,
            customInformation: data.customInformation
        }
    }, {
        'x-amz-pay-idempotency-key': uuid.v4().toString().replace(/-/g, '')
    });
}
```

Amazon PayのAPIを使って、決済に必要な購入金額や事業者側の受注番号等の情報と、支払い処理ページ(後述)で自動的にリダイレクトされるURLを指定して、checkoutSessionに対してupdateしています。  
この、「支払い処理ページで自動的にリダイレクトされるURL」ですが、Browserの場合は直接ThanksページのURLを、iOS及びAndroidの場合は中継用ページ(後述)へのURLを、それぞれ指定します。
Amazon PayのAPIからの戻り値は、そのままCheckout Session Update APIのResponseとして返却します。  

AjaxのResponseが返ってくると、下記が実行されます。

```js
// nodejs/views/sample/checkoutReview.ejsより抜粋 (見やすくするため、一部加工しています。)

            :
    document.getElementById("purchaseButton").addEventListener('click', (e) => {
        $.ajax({
            :
        })
        .then(
            function(json) { //success
                if(json.webCheckoutDetails.amazonPayRedirectUrl) {
                    if(window.androidApp) {
                        // Androidの場合
                        coverScreen();
                        androidApp.auth(json.webCheckoutDetails.amazonPayRedirectUrl);
                    } else if(window.webkit && webkit.messageHandlers && webkit.messageHandlers.iosApp) {
                        coverScreen();
                        webkit.messageHandlers.iosApp.postMessage({op: 'auth', url: json.webCheckoutDetails.amazonPayRedirectUrl});            
                    } else {
                        window.location.href = json.webCheckoutDetails.amazonPayRedirectUrl;
                    }
                } else {
                    location.href = "/static/sample/error.html";
                }
            },
            function() { //failure
                console.log("error");
                location.href = "/static/sample/error.html";
            }
        );
    });
```

WebViewに渡されたCallback Objectの存在チェックにより、クライアントの環境を判定して対応する処理を実行します。  
今回はAndroidなので、下記が実行されます。
```js
                        androidApp.auth(json.webCheckoutDetails.amazonPayRedirectUrl);
```

これにより、文字列「auth」とCheckout Session Update APIのResponseに含まれていたURLをパラメタとして、Native側の下記の処理が実行されます。  

```java
// MainActivity.java より抜粋

    @JavascriptInterface
    public void auth(String url) {
        Log.d("[JsCallback]", "auth");
        invokeAuthorizePage(this, url);
    }
```

「invokeAuthorizePage」は下記です。

```java
// MainActivity.java より抜粋

    void invokeAuthorizePage(Context context, String url) {
        invokeSecureWebview(context, url);
    }
```

このあとの流れは『「Amazon Payボタン」画像クリック時の、Secure WebViewの起動処理』と同じで、AmazonPayActivity経由でSecure WebViewが起動されます。  
以上により、Amazon Pay APIのcheckoutSession更新処理の戻り値に含まれていたURLを、Secure WebViewで開くことができます。  

## 支払い処理ページ

<img src="docimg/payment.png" width="400">  

上記Amazon Pay APIより渡されたURLに対してアクセスすると、支払い処理ページ(スピナーページとも呼ばれます)が表示されます。  
この画面が表示されている間、Amazon側ではServer側で与信を含む支払いの処理が行われており、エラーハンドリングも含めてこちらの画面で処理されています。  
支払いの処理が終わると、「購入ボタンクリック時の処理」で指定した中継用ページへのURLに自動的にリダイレクトされます。  

### 中継用ページ
中継用ページは下記のようになっています。  
※ 正確には、Androidでアプリ切替があった場合に備えた処理も併せて実装されています。詳細は[こちら](./README_fixSwitchApp.md#3-中継用ページのamazonpayactivityの自動起動が発動しなかった場合の対処)をご確認ください。  

```html
<!-- nodejs/static/dispatcher.html より抜粋 -->
    :
<script type="text/javascript" charset="utf-8">
    function getURLParameter(name, source) {
        return decodeURIComponent((new RegExp('[?|&amp;|#]' + name + '=' +
                        '([^&;]+?)(&|#|;|$)').exec(source) || [, ""])[1].replace(/\+/g, '%20')) || null;
    }

    const client = getURLParameter("client", location.search);
    location.href = client === 'iosApp' 
        ? 'amazonpay-ios-v2://thanks'
        : 'intent://amazon_pay_android_v2#Intent;package=com.amazon.pay.sample.android_app_v2;scheme=amazon_pay_android_v2;end;';
</script>

<body></body>
</html>
```

ここではIntentを使って、JavaScriptよりアプリを起動しています。  
Intentについての詳細については、[こちら](./README_swv2app.md)をご参照下さい。  
Applinksとは違い、Intentでは間違って悪意のあるアプリが起動してしまう可能性がゼロではないため、ここでは「amazonCheckoutSessionId」のようなセンシティブな情報は渡さないようにします。  

## Thanksページ

<img src="docimg/thanks.png" width="600">  

### Intentにより起動されるNativeの処理

上記Intentにより起動されるNativeの処理は、下記になります。

```java
// AmazonPayActivity.java より抜粋

    protected void onNewIntent(Intent intent) {
                :
        if (intent.getScheme().equals("https")) {
                :
                :
        } else {
            Log.d("[Intent]", "intent received!");
            MainActivity.webviewUrl = "/sample/thanks";
        }

        // 本Activityのfinish. (この後、MainActivity#onResumeに処理が移る)
        this.finish();
    }
```

ThanksページのURLをMainActivityに設定します。  
そして、AmazonPayActivityをfinishします。これにより、すぐ下のMainActivity#onResumeに処理が移ります。  

### Thanksページの読み込み

MainActivityでは、onResumeの中の下記の処理が起動します。  

```java
// MainActivity.javaより抜粋　(見やすくするため、一部加工しています。)

    protected void onResume() {
        super.onResume();

        String url = webviewUrl;
        if (url != null) {
            webviewUrl = null;
            webView.loadUrl("javascript:loadUrl('" + url + "')");
        } else {
                    :
    }
```

WebViewではこの時点で購入ページが表示されており、上記にて下記のJavaScriptが起動してThanksページの読み込みが開始します。  

```js
    function loadUrl(url) {
        location.href = url;
    }
```

Server側では下記が実行されます。

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-------------------
// Thanks Screen
//-------------------
app.get('/sample/thanks', async (req, res) => {
    const order = JSON.parse(req.cookies.session);
    await apClient.completeCheckoutSession(order.amazonCheckoutSessionId, {
        chargeAmount: {
            amount: '' + order.chargeAmount,
            currencyCode: "JPY"
        }
    });
    res.render('sample/thanks.ejs', order);
});
```

Amazon Pay APIを使ってcheckoutSessionを完了し、thanks画面を表示しています。  
本サンプルアプリの一連の流れとしては、以上となります。

## その他

### Secure WebView上で「X」ボタンで閉じられたときの対処
[こちら](./README_fixSwitchApp.md#4-secure-webview%E3%81%8C%E5%B7%A6%E4%B8%8A%E3%81%AEx%E3%83%9C%E3%82%BF%E3%83%B3%E3%81%AB%E3%82%88%E3%82%8Aclose%E3%81%95%E3%82%8C%E3%81%9F%E5%A0%B4%E5%90%88%E3%81%AE%E5%AF%BE%E5%87%A6)を参照。

### Secure WebView起動時の対処
WebViewからSecure WebView起動処理をJavaScriptで呼び出すとき、下記のように直前で「coverScreen」という、画面を真っ白にする関数を呼んでいます。  

```html
<!-- nodejs/views/sample/cart.ejsより抜粋　(見やすくするため、一部加工しています。) -->
                :
<body data-gr-c-s-loaded="true">
<div id="white_cover" style="width:100%; height:100vh; background-color:#fff; position:relative; z-index:1000; display:none;"></div>
                :
<script type="text/javascript" charset="utf-8">
                :
        node.addEventListener('click', (e) => {
            coverScreen(); // ← ここで呼んでいる
            if(client === 'androidApp') {
                androidApp.login();
            } else {
                webkit.messageHandlers.iosApp.postMessage({op: 'login'}); // ← Secure WebView起動処理
            }
        });
                :
    function coverScreen() {
        document.getElementById('white_cover').style.display = 'block';
    }

    function uncoverScreen() {
        document.getElementById('white_cover').style.display = 'none';
    }
</script>
                :
```

もしこの関数を呼ばなかった場合、Secure WebViewがCloseされるときの画面は、下記のような動きになります。  
<img src="docimg/nocover-version.gif" width="300">  
WebViewの画面の遷移が終わるまでの間、Secure WebView起動前の画面が表示されるため、不自然に見えてしまいます。  

Secure WebView起動直前に「coverScreen」を呼び出しておくことで、下記のように自然な見え方にすることができます。  
<img src="docimg/cover-version.gif" width="300">  

なおこのままだと、ユーザがSecure WebViewの左上の「Done」をタップしてWebViewに戻ってきた場合には、画面が真っ白なままになってしまいます。  
そこでその場合には、MainActivity#onResumeの下記コードにて「uncoverScreen」を呼んで、白い画面を元に戻しています。  

```swift
// MainActivityより抜粋

    @Override
    protected void onResume() {
            :
        } else {
            webView.loadUrl("javascript:if(window.uncoverScreen) {uncoverScreen();}");
        }
    }
```

本サンプルでは「coverScreen」は単に真っ白な画面を表示していますが、こちらは各モバイルアプリのデザインや方針などに応じて、より自然に見えるものを表示することをお勧めいたします。  


### AndroidでWebViewを動作させるための設定
AndroidのWebViewは制限がかなり多く、デフォルトの状態では本サンプルアプリを動かすことができません。  
動作させるために行っているカスタマイズについて、説明します。

まずは、WebViewを生成してページを読み込む処理で行っているカスタマイズです。  

```java
// MainActivityから抜粋。日本語の説明を追加しています。

        // enable JavaScript - これは、JavaScriptを有効にする設定です。
        webView.getSettings().setJavaScriptEnabled(true);

        // enable Web Storage - これは、Web Storageを有効にする設定です。
        webView.getSettings().setDomStorageEnabled(true);

        // allow redirect by JavaScript - これは、JavaScriptによる画面遷移を有効にする設定です。
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // redirect console log into AndroidStudio's Run console. - こちらは、JavaScriptで出力したログをRunコンソールに転送する設定で、デバッグ用です。
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cslMsg) {
                Log.d("MyApp", cslMsg.message() + " at line "
                        + cslMsg.lineNumber() + " of "
                        + cslMsg.sourceId());
                return super.onConsoleMessage(cslMsg);
            }
        });
```

また、[本サンプルアプリのインストール](./README_install.md)の「自己証明書のインストール」でインストールした自己証明書も、デフォルトでは認識しません。  
なので、resディレクトリの下にxmlというディレクトリを作成し、そちらに開発環境でのみユーザがインストールした証明書を認識させる設定ファイルを作成します。  

```xml
<!-- network_security_config.xmlより抜粋 -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <debug-overrides> <!-- android:debuggable = trueの時に有効. 参考: https://developer.android.com/training/articles/security-config#debug-overrides -->
        <trust-anchors>
            <certificates src="user"/> <!-- ユーザがインストールした証明書を信用させる設定. 参考: https://developer.android.com/training/articles/security-config#certificates -->
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

こちらを、AndroidManifest.xmlにて下記のように指定して読み込ませています。
```xml
    <uses-permission android:name="android.permission.INTERNET" /> ← ※ こちらの指定もないと、WebViewがInternetからページを読み込まない！

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config" ← ここ！
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
    </application>
```

# Amazon Payの実装方法 - Nativeアプリ編

## AndroidManifest.xmlの設定

Secure WebView(Chrome Custom Tabs)のOpenとClose処理を担当する、AmazonPayActivityでは、下記のようにlaunchModeに「singleTask」を指定します。  

```xml
        <activity android:name=".AmazonPayActivity"
            android:launchMode="singleTask">
```

理由や詳細については、[こちら](./README_fixSwitchApp.md)をご参照下さい。  

## カートページ or 商品ページ
<img src="docimg/cart.png" width="500">  

### 「Amazon Payボタン」画像の配置

Amamzon Payで支払いができることをユーザに視覚的に伝えるのには、Amazon Payボタンを画面に表示するのが効果的です。  
Nativeアプリでは本物のAmazon Payボタンを配置できないので、画像を代わりに配置します。  

この時指定する「Amazon Payボタン」画像は「./nodejs/static/img/button_images」の下にあるものから選ぶようにして下さい。なお、本番環境向けにファイル名が「Sandbox_」で始まるものを指定しないよう、ご注意下さい。  

### 「Amazon Payボタン」画像クリック時の、Secure WebViewの起動処理

上記、「Amazon Payボタン」画像がクリックされたときには、下記のようなコードを呼びます。これにより、AmazonPayActivityが起動されます。  
※ なお、UUID(version 4)を生成して「token」という名前で、Native側のFieldとURLのパラメタとして設定していますが、こちらの理由については後述します。  

```java
// MainActivity.javaから抜粋 (見やすくするため、一部加工しています。)

        :
    static volatile String token = null;
        :
    void invokeAppLoginPage(Context context) {
        token = UUID.randomUUID().toString();
        invokeSecureWebview(context, "https://10.0.2.2:3443/appLogin?client=androidApp&token=" + token);
    }
        :
    private void invokeSecureWebview(Context context, String url) {
        Intent intent = new Intent(context, AmazonPayActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }
```

起動されたAmazonPayActivity側の処理が下記です。  
URLを指定して、Chrome Custom Tabs(Android側のSecure WebView)を起動しているのが分かると思います。  

```java
public class AmazonPayActivity extends AppCompatActivity {
        :
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amazon_pay);
        this.isKicked = true;

        Intent intent = getIntent();
        Log.d("[Intent]", "intent received!");
        Log.d("[Intent]", intent.getStringExtra("url"));
        invokeSecureWebview(this, intent.getStringExtra("url"));
    }
        :
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
```

## 自動的にAmazonログイン画面に遷移させるページ

<img src="docimg/appLogin.png" width="500">  

こちらの画面ではAmazon Payが用意した「initCheckout」というメソッドをJavaScriptでcallすることで、Amazonログイン画面に遷移させています。  

### Server側のAmazon Payボタン出力準備
Amazon Payボタンを出力するための準備として、Server側にてAmazon Payボタンの出力に必要なpayloadと signatureの生成、その他の設定値の受け渡しを行います。  

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-------------------
// App Login Screen
//-------------------

app.get('/appLogin', async (req, res) => {
    // ※ req.queryには、上記ViewControllerで指定されたURLパラメタが入る
    res.render('appLogin.ejs', calcConfigs(`https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-${req.query.client}.html?token=${req.query.token}`));
});

function calcConfigs(url) {
    const payload = createPayload(url);
    const signature = apClient.generateButtonSignature(payload);
    return {payload: payload, signature: signature, merchantId: keyinfo.merchantId, publicKeyId: keyinfo.publicKeyId};
}

function createPayload(url) {
    return {
        webCheckoutDetails: {
            checkoutReviewReturnUrl: url
        },
        storeId: keyinfo.storeId
    };
}
```

指定されているURLの「https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/...」 はAmazon Payへのログイン & 住所・支払い方法の選択後のリダイレクト先になります。  
このURLは後述の「Applinks」という技術でSecure WebViewからアプリを起動するために使用されます。  

これらの値が「appLogin.ejs」にパラメタとして渡され、HTML & CSS & JavaScriptが生成されます。  

```html
<!-- nodejs/views/appLogin.ejsより抜粋 (見やすくするため、一部加工しています。) -->

    :
<script src="https://static-fe.payments-amazon.com/checkout.js"></script>
<script type="text/javascript" charset="utf-8">
    amazon.Pay.initCheckout({
        merchantId: '<%= merchantId %>',
        ledgerCurrency: 'JPY', // Amazon Pay account ledger currency
        sandbox: true, // dev environment
        checkoutLanguage: 'ja_JP', // render language
        productType: 'PayAndShip', // checkout type
        placement: 'Cart', // button placement
        createCheckoutSessionConfig: {
            payloadJSON: '<%- JSON.stringify(payload) %>', // string generated in step 2 (※ HTML Escapeをしないで出力する)
            signature: '<%= signature %>', // signature generated in step 3
            publicKeyId: '<%= publicKeyId %>' 
        }
    });    
</script>
```

この「initCheckout」メソッドの呼出により、自動的にAmazon Payのログイン画面に遷移させています。  
こちらのファイルは[EJS](https://ejs.co/)というTemplate Engineを使って作成されていますが、構文はTemplate Engineとしては一般的なものであるため、比較的簡単に理解できるかと思います。

## Amazon側の画面からのリダイレクトによる、Applinksの発動

<img src="docimg/applinks.png" width="500">  

### Applinksについて
Applinksについての詳細については、[こちら](./README_swv2app.md)に記載しております。

Applinksの基本的な発動条件は「Chrome/Chrome Custom Tabs等でLinkをタップする」ことですが、ServerからのRedirectでも発動することがあります。  
Applinksが発動しなかった場合には、指定されたURLに存在するファイルが通常通りに表示されます。  

### 救済ページを使った2段構えのApplinksの発動
本サンプルでは、Amazon側のページでログイン＆住所・支払い方法の選択をしたあとのリダイレクトでApplinksが発動するURLを指定していますが、上記の理由により、ここでは発動しない可能性もあります。  

本サンプルではその場合の備えとして、発動しなかった場合には再度Applinksが発動するURLへのリンクを持つ、救済ページに自動的に遷移するように作られています。  
ここではその仕組を説明します。  

「自動的にAmazonログイン画面に遷移させるページ」で登場した、Applinksを発動させるURLのAndroid版は、下記になります。  
https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-androidApp.html?token=XXXXXXXX

上記でも書いたとおり、Applinksが発動しなかった場合には、指定されたURLに存在するファイルが表示されます。  
このURLの先には下記の内容のHTMLファイルが置かれております。  
```html
<!-- nodejs/linksの下にも同じものが置かれています。 -->

<html>
    <script>
        location.href = "https://10.0.2.2:3443/static/next.html" + location.search;
    </script>
</html>
```

こちらはファイルにアクセス時に指定されたURLパラメタを付与した上で、「next.html」にリダイレクトしています。  
Note: ↑はlocal環境用なのでリダイレクト先が「https://10.0.2.2:3443/static/next.html 」になっていますが、こちらは本番・各テスト等の環境に応じて変更する必要があります。  
「next.html」の中身が下記です。  
```html
<!-- nodejs/static/next.htmlより抜粋 -->

<body data-gr-c-s-loaded="true">
<div class="container">
    <h3 class="my-4">Amazon Login 処理完了</h3>
    「次へ」ボタンをタップして下さい。<br>
    <br>
    <a id="nextButton" href="#" class="btn btn-info btn-lg btn-block">
        次　へ
    </a>
</div>
<script>
    document.getElementById("nextButton").href = 
        "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/index.html" + location.search;
</script>
</body>
```

アクセス時に指定されたURLパラメタを付与したApplinksを発動するURLを「id="nextButton"」のリンクに指定しております。  
この仕組みにより、Applinksが発動しなかった場合にはこちらの画面が表示されます。この「次へ」のLinkをユーザがタップすることで、確実に条件を満たしてApplinksを発動させることができます。  

## 購入ページ

<img src="docimg/purchase.png" width="650">  

### tokenチェックとViewControllerへの遷移先URLの設定
Applinksにより起動される処理は、下記になります。  

```java
// AmazonPayActivity.javaより抜粋　(見やすくするため、一部加工しています。)

    protected void onNewIntent(Intent intent) {
            :
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
                // 一致した場合には、購入ページを構築して表示
            } else {
                // 不一致の場合には不正な遷移であるため、エラー処理
            }

        } else {
            :
        }

        // 本Activityのfinish. (この後、MainActivity#onResumeに処理が移る)
        this.finish();
    }
```
※ 本サンプルではAmazonPayActivityが「singleTask」に設定されており、それによりSecure WebView(Chrome Custom Tabs)はAmazonPayActivityの起動により自動的にCloseされます。  

最初に、Applinks発動のURLに指定されていたURLパラメタを取得します。  

その後、「『Amazon Payボタン』画像クリック時の、Secure WebViewの起動処理」でMainActivityに保持したtokenと、Secure WebViewから受け渡されたtokenの一致判定を行っています。  
このtokenの判定を行うことで、不正な遷移でこの処理が起動された場合に、それを検出してエラーとできるようになります。

例えば、悪いユーザがSecure WebViewを起動する時の「自動的にAmazonログイン画面に遷移させるページ」へのURLを読み取って、メールなどで他のユーザに送ったとします。  
送りつけられたユーザがAndroid端末でメールのURLのリンクをクリックした場合、Chromeが立ち上がってAmazon Payログインに遷移してしまう可能性があります。  
もしそのままAmazon Payにログインして、住所・支払い方法選択も実施した場合、ChromeならApplinksも発動してしまいますので、同アプリをインストールしていればその後の購入フローも実行できることになってしまいます。  
画面のFlowによってはこれが大きな問題になる可能性もあるため、本サンプルアプリでは念のためにtokenチェックを行っております。  

tokenチェックにて問題がなかった場合には、購入ページを構築して表示します。  
購入ページには配送先や金額などの情報が必要であるため、Server側の下記のような処理を呼び出してこれらを取得する必要があります。

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-------------------------
// Checkout Review Screen
//-------------------------
app.get('/sample/checkoutReview', async (req, res) => {
    // 受注情報
    let order = {host: req.headers.host, amazonCheckoutSessionId: req.query.amazonCheckoutSessionId,
        client: req.cookies.client, hd8: req.cookies.hd8, hd10: req.cookies.hd10, items: []};
    order.items.push({id: 'item0008', name: 'Fire HD8', price: 8980, num: parseInt(order.hd8)});
    order.items.push({id: 'item0010', name: 'Fire HD10', price: 15980, num: parseInt(order.hd10)});
    order.items.forEach(item => item.summary = item.price * item.num); // 小計
    order.price = order.items.map(item => item.summary).reduce((pre, cur) => pre + cur); // 合計金額
    order.chargeAmount = Math.floor(order.price * 1.1); // 税込金額

    // Amazon Pay受注情報
    const payload = await apClient.getCheckoutSession(req.query.amazonCheckoutSessionId, 
        {'x-amz-pay-idempotency-key': uuid.v4().toString().replace(/-/g, '')});
    order.checkoutSession = JSON.parse(payload.body);

    // Note: 一般的には受注情報はSessionやDBなどを使ってServer側に保持しますが、本サンプルではシンプルにするためにCookieを使用しています
    res.cookie('session', JSON.stringify(order), {secure: true});
    
    // TODO ↓の部分はJSONなど、アプリで受け取りやすい形式のデータを返却するよう、修正する。
    // res.render('sample/checkoutReview.ejs', order);
});
```

cartの情報を計算して金額を出し、またAmazon Pay APIより住所情報等を取得し、返却します。

### 購入ボタンクリック時の処理

購入ボタンをクリック時には、下記のようにServer側のCheckout Session Update APIが呼び出して、決済に必要な購入金額や事業者側の受注番号等の情報と、支払い処理ページ(後述)で自動的にリダイレクトされるURLを指定して、checkoutSessionに対してupdateする必要があります。

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-----------------------------
// Checkout Session Update API
//-----------------------------

// 事業者側の受注番号の採番
const newMerchantReferenceId = function() {
    let currentNumber = 1;
    return function() {
        return "MY-ORDER-" + currentNumber++;
    }
} ();

app.post('/sample/checkoutSession', async (req, res) => {
    let order = JSON.parse(req.cookies.session);
    const payload = await updateCheckoutSession({merchantReferenceId: newMerchantReferenceId(),
        merchantStoreName: "MY-SHOP", noteToBuyer: "Thank you!", customInformation: "This isn't shared with Buyer", ...order});    
    order.checkoutSession = JSON.parse(payload.body);

    // Note: 一般的には受注情報はSessionやDBなどを使ってServer側に保持しますが、本サンプルではシンプルにするためにCookieを使用しています
    res.cookie('session', JSON.stringify(order), {secure: true});

    res.writeHead(200, {'Content-Type': 'application/json; charset=UTF-8'});
    res.write(payload.body);
    res.end()
});

async function updateCheckoutSession(data) {
    const url = data.client === 'browser' ? "https://localhost:3443/sample/thanks" :
        `https://${data.host}/static/dispatcher.html?client=${data.client}`;
    return await apClient.updateCheckoutSession(data.amazonCheckoutSessionId, {
        webCheckoutDetails: {
            checkoutResultReturnUrl: url
        },
        paymentDetails: {
            paymentIntent: 'Authorize',
            canHandlePendingAuthorization: false,
            chargeAmount: {
                amount: '' + data.chargeAmount,
                currencyCode: "JPY"
            }
        },
        merchantMetadata: {
            merchantReferenceId: data.merchantReferenceId,
            merchantStoreName: data.merchantStoreName,
            noteToBuyer: data.noteToBuyer,
            customInformation: data.customInformation
        }
    }, {
        'x-amz-pay-idempotency-key': uuid.v4().toString().replace(/-/g, '')
    });
}
```

この、「支払い処理ページで自動的にリダイレクトされるURL」ですが、モバイルアプリではNativeコードを起動させる必要があるため、中継用ページ(後述)へのURLを指定します。  
Amazon PayのAPIからの戻り値は、本サンプルアプリではそのままCheckout Session Update APIのResponseとして返却しています。  
リダイレクトする必要があるURLは下記になります。
```
$.webCheckoutDetails.amazonPayRedirectUrl
```

Nativeアプリ側でこのResponseを受け取ったら、上記のURLをパラメタとして下記の処理を実行します。  

```java
// MainActivity.javaより抜粋

    void invokeAuthorizePage(Context context, String url) {
        invokeSecureWebview(context, url);
    }
```

このあとの流れは『「Amazon Payボタン」画像クリック時の、Secure WebViewの起動処理』と同じで、AmazonPayActivity経由でSecure WebViewが起動されます。  
以上により、Amazon Pay APIのcheckoutSession更新処理の戻り値に含まれていたURLを、Secure WebViewで開くことができます。  

## 支払い処理ページ

<img src="docimg/payment.png" width="400">  

上記Amazon Pay APIより渡されたURLに対してアクセスすると、支払い処理ページ(スピナーページとも呼ばれます)が表示されます。  
この画面が表示されている間、Amazon側ではServer側で与信を含む支払いの処理が行われており、エラーハンドリングも含めてこちらの画面で処理されています。  
支払いの処理が終わると、「購入ボタンクリック時の処理」で指定した中継用ページへのURLに自動的にリダイレクトされます。  

### 中継用ページ
中継用ページは下記のようになっています。  
※ 正確には、Androidでアプリ切替があった場合に備えた処理も併せて実装されています。詳細は[こちら](./README_fixSwitchApp.md#3-中継用ページのamazonpayactivityの自動起動が発動しなかった場合の対処)をご確認ください。  

```html
<!-- nodejs/static/dispatcher.html より抜粋 -->
    :
<script type="text/javascript" charset="utf-8">
    function getURLParameter(name, source) {
        return decodeURIComponent((new RegExp('[?|&amp;|#]' + name + '=' +
                        '([^&;]+?)(&|#|;|$)').exec(source) || [, ""])[1].replace(/\+/g, '%20')) || null;
    }

    const client = getURLParameter("client", location.search);
    location.href = client === 'iosApp' 
        ? 'amazonpay-ios-v2://thanks'
        : 'intent://amazon_pay_android_v2#Intent;package=com.amazon.pay.sample.android_app_v2;scheme=amazon_pay_android_v2;end;';
</script>

<body></body>
</html>
```

ここではIntentを使って、JavaScriptよりアプリを起動しています。  
Intentについての詳細については、[こちら](./README_swv2app.md)をご参照下さい。  
Applinksとは違い、Intentでは間違って悪意のあるアプリが起動してしまう可能性がゼロではないため、ここでは「amazonCheckoutSessionId」のようなセンシティブな情報は渡さないようにします。  

## Thanksページ

<img src="docimg/thanks.png" width="600">  

### CustomURLSchemeにより起動されるNativeの処理

上記Intentにより起動されるNativeの処理は、下記になります。

```java
// AmazonPayActivity.java より抜粋

    protected void onNewIntent(Intent intent) {
            :
        if (intent.getScheme().equals("https")) {
            :
            :
        } else {
            Log.d("[Intent]", "intent received!");
            // Thanksページを構築して表示 (※ MainActivityに戻った後に処理するのがお勧め)
        }

        // 本Activityのfinish. (この後、MainActivity#onResumeに処理が移る)
        this.finish();
    }
```

Intentを受信したら、Thanksページを構築して表示する処理を行います。
このとき、checkoutSessionに対して「completeCheckoutSession」を呼び出して完了させる必要があるため、Server側の処理を呼び出して下記を実行して下さい。

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-------------------
// Thanks Screen
//-------------------
app.get('/sample/thanks', async (req, res) => {
    const order = JSON.parse(req.cookies.session);
    await apClient.completeCheckoutSession(order.amazonCheckoutSessionId, {
        chargeAmount: {
            amount: '' + order.chargeAmount,
            currencyCode: "JPY"
        }
    });
    // res.render('sample/thanks.ejs', order); // この部分はJSONなど、アプリで受け取りやすい形式のデータを返却するよう、修正する。
});
```

本サンプルアプリをベースとしたNativeアプリの一連の流れとしては、以上となります。

## その他

### Secure WebView上で「X」ボタンで閉じられたときの対処
[こちら](./README_fixSwitchApp.md#4-secure-webview%E3%81%8C%E5%B7%A6%E4%B8%8A%E3%81%AEx%E3%83%9C%E3%82%BF%E3%83%B3%E3%81%AB%E3%82%88%E3%82%8Aclose%E3%81%95%E3%82%8C%E3%81%9F%E5%A0%B4%E5%90%88%E3%81%AE%E5%AF%BE%E5%87%A6)を参照。


# 付録

## 固定Signatureでの実装方針

*※ iOS, Android共に同じ内容です。*  

一般的なブラウザの実装では[こちら](https://amazonpaycheckoutintegrationguide.s3.amazonaws.com/amazon-pay-checkout/add-the-amazon-pay-button.html#3-sign-the-payload)を見ると分かる通り、Amazon Payボタンに渡すPayloadのSignatureは、一回だけ計算して後はその値を固定値として使いまわす実装が一般的です。  
ところが、上記「Server側のAmazon Payボタン出力準備」で解説した下記のコードを見ると分かる通り、本サンプルアプリでは ```apClient.generateButtonSignature(payload)``` にてPayloadのSignatureを毎回計算しています。  

```js
// nodejs/app.jsより抜粋 (見やすくするため、一部加工しています。)

//-------------------
// App Login Screen
//-------------------

app.get('/appLogin', async (req, res) => {
    res.render('appLogin.ejs', calcConfigs(`https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-${req.query.client}.html?token=${req.query.token}`));
});

function calcConfigs(url) {
    const payload = createPayload(url);
    const signature = apClient.generateButtonSignature(payload);
    return {payload: payload, signature: signature, merchantId: keyinfo.merchantId, publicKeyId: keyinfo.publicKeyId};
}

function createPayload(url) {
    return {
        webCheckoutDetails: {
            checkoutReviewReturnUrl: url
        },
        storeId: keyinfo.storeId
    };
}
```

これはアプリのSecurityを向上するために、「checkoutReviewReturnUrl」として指定するURLにアプリ側で生成されたtokenをURLパラメタとして付与する必要があるためで、このtokenの値が毎回変わるものであるためSignatureも毎回計算する必要があります。  
しかし、利用しているサービスや環境の都合で、Signatureの計算を毎回行うのが難しい場合も考えられます。  
こういった場合には、画面の遷移数は一画面増えてしまいますが、ここで説明する方針に則ってご実装いただくことでSecurityも担保しつつ、Signatureも使い回すことが可能です。  

### 概要

![](docimg/flow-fixed-signature.png)

上記のように、
- checkoutReviewReturnUrlには固定のURLを指定
- アプリから渡されたtokenはSecure WebViewのCookieに保存
- Amazon側の画面からcheckoutReviewReturnUrlにリダイレクトされた画面でCookieからtokenを取り出し、Applinks/Universal Linksを起動するURLにURLパラメタとして付与する。ユーザがそのLinkをタップすることで、Nativeアプリのコードが起動する。

といった流れになります。  
本サンプルに沿った、具体的なコード例を下記に示します。  

### checkoutReviewReturnUrlには固定のURLを指定
```js
// nodejs/app.jsより

//-------------------
// App Login Screen
//-------------------

app.get('/appLogin', async (req, res) => {
    res.render('appLogin.ejs', {
        payload: {
            webCheckoutDetails: {
                checkoutReviewReturnUrl: `https://10.0.2.2:3443/static/next.html` // 固定URL
            },
            storeId: keyinfo.storeId
        },
        signature: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', // 事前に計算したSignature
        merchantId: keyinfo.merchantId,
        publicKeyId: keyinfo.publicKeyId
    });
});
```

### アプリから渡されたtokenはSecure WebViewのCookieに保存
```html
<!-- nodejs/views/appLogin.ejsより -->

<script src="https://static-fe.payments-amazon.com/checkout.js"></script>
<script type="text/javascript" charset="utf-8">
    function getURLParameter(name, source) {
        return decodeURIComponent((new RegExp('[?|&amp;|#]' + name + '=' +
                        '([^&;]+?)(&|#|;|$)').exec(source) || [, ""])[1].replace(/\+/g, '%20')) || null;
    }
    
    document.cookie = `token=${getURLParameter('token', location.search)}; path=/;` // Cookieにtokenを保存
    
    amazon.Pay.initCheckout({
            :
    });
</script>
```

### Cookieからtokenを取り出しURLパラメタとして付与
```html
<!-- nodejs/static/next.htmlより -->

<script>
    document.getElementById("nextButton").href = 
        "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/index.html" 
            + location.search + '&'
            + document.cookie.split('; ').find(function(kv) {return kv.startsWith('token=')}); // Cookieよりtokenを取得
</script>
```
