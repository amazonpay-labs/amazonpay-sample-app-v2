# Amazon Pay Mobile Sample App Android App Implementation
This is the implementation of the Android application side of this sample app. For instructions on how to install and run the app, please refer to [here](./README_install.md).

## Operating environment
Android 6 or later: Google Chrome 64 or later  

# Other prerequisites
This sample application uses a technology called Applinks, and the following conditions are required to use this technology.
 - Because the configuration file must be placed in a location on the Web that can be properly accessed via https, you must have a server with a different domain from the EC site, or an account with a cloud service such as AWS.  
   Note: In this sample application, [Amazon S3](https://aws.amazon.com/jp/s3/) is used. It is easy to get an account on the Internet, is widely used around the world, has a lot of information on how to use it, and has a free usage limit of 5GB for 12 months, so it is recommended.  

## Overview
This sample application will work as shown in the video below.

<img src="docimg/android-movie.gif" width="300">  

For details of the flow, please refer to [flow-android.xlsx](./flow-android.xlsx).  
Based on this flow, we will explain the detailed implementation in the following sections.

# How to implement Amazon Pay - WebView app version

## Cart page

<img src="docimg/cart.png" width="500">  

### Setting up Callback acceptance from the JavaScript side of the mobile app
In the mobile app, the Amazon Pay process needs to be executed on the Secure WebView, but since the Secure WebView cannot be launched directly from the WebView, it is necessary to configure it so that the Native code can be launched from the WebView's JavaScript.  
The following code will do that.  

```java
// Excerpt from MainActivity.java (Some parts have been modified for clarity.)

    protected void onCreate(Bundle savedInstanceState) {
                :
        webView.addJavascriptInterface(this, "androidApp");
                :
    }
                :
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
```

With this configuration, it is possible to call Native methods from the JavaScript side, as shown below.
```js
                androidApp.login();
```

### Client Determination
This sample app supports all of Android/iOS/normal Browser with the same HTML/JavaScript screen.  
Therefore, if you need to switch the process according to the operating environment, you need to judge the client and do a conditional branch.  
The JavaScript code below does just that.

```js
// Excerpt from nodejs/views/sample/cart.ejs

    let client = "browser";
    if(window.androidApp) {
        client = "androidApp";
    } else if(window.webkit && webkit.messageHandlers && webkit.messageHandlers.iosApp) {
        client = "iosApp";
    }
    document.cookie = "client=" + client + ";path=/;secure";
```

By checking the existence of the Object for Callback set in "Setting up Callback acceptance from the JavaScript side of the mobile app" above, we can determine what environment it is for each.  
The judgment result is set in a cookie so that it can be referred to on the Server side.  


### Placement of the "Amazon Pay Button" image

Displaying an Amazon Pay button on the screen is an effective way to visually communicate to users that they can pay with Amazon Pay.  
Since we cannot place a real Amazon Pay button on the WebView, we place an image instead.

This is done in the following JavaScript.
```js
// Excerpt from nodejs/views/sample/cart.ejs (Some parts have been modified for clarity.)

    if(client === 'browser') {
        Amazon.Pay.renderButton('#AmazonPayButton', {
            :
        });
    } else {
        let node = document.createElement("input");
        node.type = "image";
        node.src = "/static/img/button_images/Sandbox-live-en_jp-amazonpay-gold-large-button_T2.png";
        node.addEventListener('click', (e) => {
            coverScreen();
            if(client === 'androidApp') {
                // -> Android.
                androidApp.login();
            } else {
                webkit.messageHandlers.iosApp.postMessage({op: 'login'});
            }
        });
        document.getElementById("AmazonPayButton").appendChild(node);
    }
```

In the first judgment, if the browser is a normal browser, the Amazon Pay process can be implemented as is, so the Amazon Pay button is loaded as usual.  
In the case of Android, we generate a node for the "Amazon Pay Button" image and add it under the "AmazonPayButton" node in the same screen.  
The "Amazon Pay Button" image to be specified at this time should be selected from the images under "./nodejs/static/img/button_images". Please be careful not to specify a file name that begins with "Sandbox_" for the production environment.  
Also, when the generated node is clicked, we add an Event Handler that calls the native Callback with the Object that specifies "login" as a parameter.  

### Start Secure WebView when the "Amazon Pay Button" image is clicked.
The following is the Native code that is called when the "Amazon Pay Button" image is clicked.  

```java
// Excerpt from MainActivity.java (Some parts have been modified for clarity.)

    @JavascriptInterface
    public void login() {
        Log.d("[JsCallback]", "login");
        invokeAppLoginPage(getApplicationContext());
    }
```

The process of "invokeAppLoginPage()" is shown below.  
```java
// Excerpt from MainActivity.java (Some parts have been modified for easier viewing.)

        :
    static volatile String token = null;
        :
    void invokeAppLoginPage(Context context) {
        token = UUID.randomUUID().toString();
        invokeSecureWebview(context, "https://10.0.2.2:3443/appLogin?client=androidApp&token=" + token);
    }
        :
    private void invokeSecureWebview(Context context, String url) {
        CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder().build();

        // Specify Chrome as the Browser to launch
        // Note: Chrome is specified here because Amazon Pay does not support other browsers.
        // [Reference] https://pay.amazon.com/jp/help/202030010
        // If you need to launch other browsers that support Chrome Custom Tabs, please refer to the following source code for implementation.
        // [Reference] https://github.com/GoogleChrome/custom-tabs-client/blob/master/shared/src/main/java/org/chromium/customtabsclient/shared/CustomTabsHelper.java#L64
        tabsIntent.intent.setPackage("com.android.chrome");

        // Set the flag to automatically terminate Chrome Custom Tabs when transitioning to another Activity.
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Set the flag so that it does not remain as History when Chrome Custom Tabs is closed.
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        // Launch Chrome Custom Tabs.
        tabsIntent.launchUrl(context, Uri.parse(url));
    }
```

You can see that we are launching Chrome Custom Tabs (Secure WebView on the Android side) by specifying the URL.  
In addition, we have generated a UUID (version 4) and named it "token" and set it as a parameter to the Field and URL on the Native side, but the reason for this is explained later.  

## Page that automatically transitions to the Amazon login screen

<img src="docimg/appLogin.png" width="500">  

This screen transitions to the Amazon login screen by using JavaScript to call the "initCheckout" method prepared by Amazon Pay.  

### Preparing to output the Amazon Pay button on the Server side
In preparation for outputting the Amazon Pay button, we will generate the payload and signature required for outputting the Amazon Pay button on the Server side, and pass them with the other configuration values.  

```js
// Excerpt from nodejs/app.js (Some parts have been modified for clarity.)

//-------------------
// App Login Screen
//-------------------

app.get('/appLogin', async (req, res) => {
    // * req.query will contain the URL parameter specified in ViewController above.
    res.render('appLogin.ejs', calcConfigs(`https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-${req.query. client}.html?token=${req.query.token}`));
});

function calcConfigs(url) {
    const payload = createPayload(url);
    const signature = apClient.generateButtonSignature(payload);
    return {payload: payload, signature: signature, merchantId: keyinfo.merchantId, publicKeyId: keyinfo.publicKeyId}
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

The specified URL "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/..." in the specified URL will be the redirect after logging in to Amazon Pay & selecting the address and payment method.  
This URL is used to launch the native code from Secure WebView with the "Applinks" technology described later.  

These values are passed as parameters to "appLogin.ejs" to generate the HTML, CSS & JavaScript.  

```html
<! -- Excerpt from nodejs/views/appLogin.ejs (Some parts have been modified for clarity.) --> 

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
            payloadJSON: '<%- JSON.stringify(payload) %>', // string generated in step 2 (* output without HTML Escape)
            signature: '<%= signature %>', // signature generated in step 3
            publicKeyId: '<%= publicKeyId %>'
        }
    });    
</script>
```

This call to the "initCheckout" method automatically transitions to the Amazon Pay login screen.  
This file is created using Template Engine called [EJS](https://ejs.co/), and the syntax is common for Template Engine, so it should be relatively easy to understand.  

## Triggering Applinks by redirecting from Amazon's screen

<img src="docimg/applinks.png" width="500">  

### About Applinks
For more information about Applinks, see [here](./README_swv2app.md).

The basic condition for triggering Applinks is "tapping the Link in Chrome/Chrome Custom Tabs, etc.", but it may also be triggered by a Redirect from the Server.  
If Applinks is not triggered, the files existing at the specified URL will be displayed as usual.  

### Triggering two-stage Applinks with a rescue page
In this sample, we have specified a URL where Applinks will be triggered by a redirect after logging in and selecting an address and payment method on the Amazon page, but for the reasons mentioned above, there is a possibility that Applinks will not be triggered here.  

However, for the reasons mentioned above, there is a possibility that Applinks will not be triggered here. As a precaution, this sample is designed to automatically redirect the user to a rescue page with a link to the URL where Applinks will be triggered again if it is not triggered.  
Here's how it works.  

The Android version of the URL that triggers Applinks, which appeared in "Page that automatically transitions to the Amazon login screen" is as follows  
https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-androidApp.html?token=XXXXXXXX

As mentioned above, if Applinks is not triggered, the file that exists at the specified URL will be displayed.  
An HTML file with the following contents is placed at the end of this URL.  
```html
<! -- The same thing is placed under nodejs/links. -->

<html>
    <script>
        location.href = "https://10.0.2.2:3443/static/next.html" + location.search;
    </script>
</html>
```

This redirects the file to "next.html" with the URL parameter specified when the file was accessed.  
Note: The above is for a local environment, so the redirect is set to "https://10.0.2.2:3443/static/next.html", but you may need to change this depending on your environment, such as production or testing.  
The content of "next.html" is as follows.  
```html
<! -- excerpt from nodejs/static/next.html -->

<body data-gr-c-s-loaded="true">
<div class="container">
    <h3 class="my-4">Amazon Login processing completed</h3>.
    Please tap the "Next" button. <br>
    <br>
    <a id="nextButton" href="#" class="btn btn-info btn-lg btn-block">
        Next
    </a>
</div>
<script>
    document.getElementById("nextButton").href =
        "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/index.html" + location.search;
</script>
</body>
```

The URL that triggers Applinks with the URL parameter specified at the time of access is specified in the "nextButton" link.  
With this mechanism, if Applinks is not triggered, this screen will be displayed. By tapping on the "next" link, you can ensure that the conditions are met and the Applinks are triggered.  

## Purchase page

<img src="docimg/purchase.png" width="650">  

### token check and setting the destination URL to ViewController
The Naive code invoked by Applinks is as follows.  


```java
// Excerpt from AmazonPayActivity (Some parts have been modified for clarity.)

    protected void onCreate(Bundle savedInstanceState) {
                :
        Intent intent = getIntent();
        if (intent.getScheme().equals("https")) {
            String appLinkAction = intent.getAction();
            Uri appLinkData = intent.getData();
            Log.d("[AppLink]", appLinkAction);
            Log.d("[AppLink]", "" + appLinkData);

            // Parse the URL parameter
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
        // This Activity's finish. (After this, the process will be moved to MainActivity#onResume)
        This.finish();
    }
```

In this sample, Secure WebView (Chrome Custom Tabs) is set to automatically close when another Activity starts, so it is already closed when this Activity starts.

First, get the URL parameter that was specified in the URL that triggered the Applinks.  

After that, it judges whether the token passed from Secure WebView matches the token stored in MainActivity in "Start Secure WebView when the 'Amazon Pay Button' image is clicked".  
By judging the token, we can detect and raise an error if this process is launched with an invalid transition.

For example, let's say a bad user reads the URL to the "Page that automatically transitions to the Amazon login screen" when launching Secure WebView, and sends it to another user via email.  
If the user who was sent the URL clicks on the link in the email on their Android device, Chrome may be launched and take them to the Amazon Pay login screen.  
If the user logs in to Amazon Pay and selects an address and payment method, Chrome will also trigger Applinks, which means that if the user has installed the app, they will be able to execute the subsequent purchase flow.  
Since this may become a big problem depending on the implementation of the EC site, we check the token in this sample app just in case.  

After the token check, set the URL of the purchase page to MainActivity.  
The URL parameter "amazonCheckoutSessionId" is given to the URL of the purchase page, which is the exact same URL and the exact same conditions as the transition to the purchase page in the PC and Mobile browsers.  
Therefore, there is no need to implement separate processes for "for mobile apps" and "for PC and mobile browsers" when displaying the purchase page after this.  

Finally, we finish the AmazonPayActivity. This will move the MainActivity#onResume process immediately below.  

### Loading the purchase page

In MainActivity, the following process in onResume will be invoked.  

```java
// Excerpt from MainActivity.java (Some parts have been modified for clarity.)

                    :
        String url = webviewUrl;
        if (url ! = null) {
            webviewUrl = null;
            webView.loadUrl("javascript:loadUrl('" + url + "')");
                    :
```

At this point, the cart page is displayed in WebView, and the following JavaScript is triggered to start loading the purchase page.  

```js
    function loadUrl(url) {
        location.href = url;
    }
```

On the Server side, the following will be executed.

```js
// Excerpt from nodejs/app.js (Some parts have been modified for clarity.)

//-------------------------
// Checkout Review Screen
//-------------------------
app.get('/sample/checkoutReview', async (req, res) => {
    // Order information
    let order = {host: req.headers.host, amazonCheckoutSessionId: req.query.amazonCheckoutSessionId,
        client: req.cookies.client, hd8: req.cookies.hd8, hd10: req.cookies.hd10, items: []};
    order.items.push({id: 'item0008', name: 'Fire HD8', price: 8980, num: parseInt(order.hd8)});
    order.items.push({id: 'item0010', name: 'Fire HD10', price: 15980, num: parseInt(order.hd10)});
    order.items.forEach(item => item.summary = item.price * item.num); // Subtotal
    order.price = order.items.map(item => item.summary).reduce((pre, cur) => pre + cur); // total amount
    order.chargeAmount = Math.floor(order.price * 1.1); // amount including tax

    // Amazon Pay order information
    const payload = await apClient.getCheckoutSession(req.query.amazonCheckoutSessionId,
        {'x-amz-pay-idempotency-key': uuid.v4().toString().replace(/-/g, '')});
    order.checkoutSession = JSON.parse(payload.body);

    // Note: In general, order information is kept on the Server side using Session or DB, but this sample uses Cookie for simplicity.
    res.cookie('session', JSON.stringify(order), {secure: true});

    res.render('sample/checkoutReview.ejs', order);
});
```

It calculates the amount of money by calculating the cart information, gets the address information from Amazon Pay API, and passes it to the template engine to generate and display the screen.

### Processing when a purchase button is clicked.

When you click the buy button, the following script will be executed.

```js
// Excerpt from nodejs/views/sample/checkoutReview.ejs (Some parts have been modified for clarity.)

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

Ajax will call the following Server-side Checkout Session Update API.  

```js
// Excerpt from nodejs/app.js (Some parts have been modified for clarity.)

//-----------------------------
// Checkout Session Update API
//-----------------------------

// Numbering of the order number on the business side
const newMerchantReferenceId = function() {
    let currentNumber = 1;
    return function() {
        return "MY-ORDER-" + currentNumber++;
    }
} ();


app.post('/sample/checkoutSession', async (req, res) => {
    let order = JSON.parse(req.cookies.session);
    const payload = await updateCheckoutSession({merchantReferenceId: newMerchantReferenceId(),
        merchantStoreName: "MY-SHOP", noteToBuyer: "Thank you!", customInformation: "This isn't shared with Buyer", . .order});    
    order.checkoutSession = JSON.parse(payload.body);

    // Note: In general, order information is kept on the Server side using Session or DB, but this sample uses Cookie for simplicity.
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
            paymentIntent: 'Authorize', paymentIntent: 'Authorize', canHandlePendingAuthorization: false,
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

Using Amazon Pay's API, we update the checkoutSession with information such as the purchase amount and the order number of the business, which are required for payment, and the URL that will be automatically redirected on the payment processing page (see below).  
As for the "URL to be automatically redirected on the payment processing page" in the case of normal browser, specify the URL of the Thanks page directly, and in the case of Mobile App for iOS and Android, specify the URL to the "page for relay" (descriped later).
The return value from the Amazon Pay API is directly returned as a Response of the Checkout Session Update API.  

When the Ajax Response is returned, the following will be executed.

```js
// Excerpt from nodejs/views/sample/checkoutReview.ejs (Some parts have been modified for clarity.)

            :
    document.getElementById("purchaseButton").addEventListener('click', (e) => {
        $.ajax({
            :
        })
        .then(
            function(json) { //success
                if(json.webCheckoutDetails.amazonPayRedirectUrl) {
                    if(window.androidApp) {
                        //for Android
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

By checking the existence of the Callback Object passed to the WebView, the client environment is determined and the corresponding process is executed.  
In this case, since we are using Android, the following will be executed.

```js
                        androidApp.auth(json.webCheckoutDetails.amazonPayRedirectUrl);
```

This will execute the following process on the Native side, using the string "auth" and the URL included in the Checkout Session Update API Response as parameters.  

```java
// Excerpt from MainActivity.java

    @JavascriptInterface
    public void auth(String url) {
        Log.d("[JsCallback]", "auth");
        invokeAuthorizePage(getApplicationContext(), url);
    }
```

The "invokeAuthorizePage" is as follows.

```java
// Excerpt from MainActivity.java

    void invokeAuthorizePage(Context context, String url) {
        invokeSecureWebview(context, url);
    }
```

With the above, you can open the URL included in the return value of the Amazon Pay API checkoutSession update process with Secure WebView.  

## Payment processing page

<img src="docimg/payment.png" width="400">  

When you access the URL passed from the Amazon Pay API above, the payment processing page (also called the spinner page) will be displayed.  
While this screen is being displayed, Amazon is processing the payment, including authorization, on the Server side, and error handling is also being handled on this screen.  
When the payment process is complete, you will be automatically redirected to the URL for the relay page specified in "Processing when clicking the purchase button".  

### Relay page
The relay page is following.  

```html
<! -- excerpt from nodejs/static/dispatcher.html -->
    :
<script type="text/javascript" charset="utf-8">
    function getURLParameter(name, source) {
        return decodeURIComponent((new RegExp('[? |&amp;|#]' + name + '=' +
                        '([^&;]+?)') (&|#|;|$)').exec(source) || [, ""])[1].replace(/\+/g, '%20')) || null;
    }

    const client = getURLParameter("client", location.search);
    location.href = client === 'iOSApp'
        ? 'amazonpay-ios-v2://thanks'
        : 'intent://amazon_pay_android_v2#Intent;package=com.amazon.pay.sample.android_app_v2;scheme=amazon_pay_android_v2;end;';
</script

<body></body>
</html>
```



Here, we use Intent to launch the application from JavaScript.  
For more information about Intent, please refer to [here](./README_swv2app.md).  
Unlike Applinks, there is no possibility of accidentally launching a malicious app with Intent, so we do not pass sensitive information such as "amazonCheckoutSessionId" here.  

## Thanks page

<img src="docimg/thanks.png" width="600">  

### Native processing triggered by Intent

The following is the Native process invoked by the above Intent.

```java
// Excerpt from AmazonPayActivity.java

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amazon_pay);

        Intent intent = getIntent();
        if (intent.getScheme().equals("https")) {
                :
                :
        } else {
            Log.d("[Intent]", "intent received!");
            MainActivity.webviewUrl = "/sample/thanks";
        }

        // This Activity's finish. (After this, the process will be moved to MainActivity#onResume)
        This.finish();
    }
```

Set the URL of the Thanks page to MainActivity.  
Then, we finish the AmazonPayActivity. This will move the process to MainActivity#onResume just below.  

### Load the Thanks page.

In MainActivity, the following process in onResume will be invoked.  

```java
// Excerpt from MainActivity.java (Some parts have been modified for clarity.)

    protected void onResume() {
        super.onResume();

        String url = webviewUrl;
        if (url ! = null) {
            webviewUrl = null;
            webView.loadUrl("javascript:loadUrl('" + url + "')");
        } else {
                    :
    }
```

At this point, the purchase page is displayed in WebView, and the following JavaScript is triggered above to start loading the Thanks page.  

```js
    function loadUrl(url) {
        location.href = url;
    }
```

On the Server side, the following will be executed.

```js
// Excerpt from nodejs/app.js (Some parts have been modified for clarity.)

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

The checkoutSession is completed using the Amazon Pay API, and the thanks screen is displayed.  
This is the end of the series of steps for this sample application.

## Other.

### What to do when starting Secure WebView
When calling the Secure WebView startup process in JavaScript from WebView, a function called "coverScreen" is called immediately before as shown below to make the screen blank.  

```html
<! -- Excerpt from nodejs/views/sample/cart.ejs (Some parts have been modified for clarity.) --> <!
                :
<body data-gr-c-s-loaded="true">
<div id="white_cover" style="width:100%; height:100vh; background-color:#fff; position:relative; z-index:1000; display:none;"></div>
                :
<script type="text/javascript" charset="utf-8">
                :
        node.addEventListener('click', (e) => {
            coverScreen(); // ← we call it here
            if(client === 'androidApp') {
                androidApp.login();
            } else {
                webkit.messageHandlers.iosApp.postMessage({op: 'login'}); // ← Secure WebView startup process
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

If you don't call this function, your screen will look like the following when Secure WebView is closed.  
<img src="docimg/nocover-version.gif" width="300">  
Since the screen before Secure WebView is displayed until the WebView screen transition is completed, it looks unnatural.  

By calling "coverScreen" just before Secure WebView starts, you can make it look more natural as shown below.  
<img src="docimg/cover-version.gif" width="300">.  

If this is not done, when the user returns to the WebView by tapping the "Done" button in the upper left corner of the Secure WebView, the screen will remain blank.  
In that case, we call "uncoverScreen" in the following code of MainActivity#onResume to restore the white screen.  

```swift
// Excerpt from MainActivity

    @Override
    protected void onResume() {
            :
        } else {
            webView.loadUrl("javascript:if(window.uncoverScreen) {uncoverScreen();}");
        }
    }
```

In this sample, "coverScreen" simply displays a blank screen, but we recommend that you display something that looks more natural here, depending on the design and policies of each mobile application.  


### Setting up WebView to work on Android
Android's WebView has many limitations, and this sample app cannot be run in its default state.  
This section will explain the customization we are doing to make it work.

First, let's look at the customization done in the process of creating the WebView and loading the page.  

```java
// Extracted from MainActivity. I've added Japanese explanation.

        // enable JavaScript - This is a setting to enable JavaScript.
        webView.getSettings().setJavaScriptEnabled(true);

        // enable Web Storage - This is the setting to enable Web Storage.
        webView.getSettings().setDomStorageEnabled(true);

        webView.getSettings().setDomStorageEnabled(true); // allow redirect by JavaScript - This is the setting to enable screen transition by JavaScript.
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // redirect console log into AndroidStudio's Run console. - This is a setting to forward the log output by JavaScript to the Run console for debugging.
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

Also, the self-certificate installed in [Installation of this sample application](./README_install.md) will also not be recognized by default.  
So, create a directory named xml under the "res" directory, and create a configuration file there to recognize the user-installed certificate only in the development environment.  

```xml
<! -- excerpt from network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"? >
<network-security-config>
    <debug-overrides> <! -- enabled when android:debuggable = true. Reference: https://developer.android.com/training/articles/security-config#debug-overrides -->
        <trust-anchors>
            <certificates src="user"/> <! -- Configure to trust user-installed certificates. Reference: https://developer.android.com/training/articles/security-config#certificates -->
        </trust-anchors> <certificates src="user"/> <!
    </debug-overrides>.
</network-security-config>
```

This is loaded in AndroidManifest.xml with the following specification.
```xml
    <uses-permission android:name="android.permission.INTERNET" /> ← Note: If you don't specify this, WebView won't load the page from Internet!

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config" ← here!
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
    </application>
```

# How to implement Amazon Pay - Native App.

## Cart Page or Product Page
<img src="docimg/cart.png" width="500">  

### Placement of the "Amazon Pay Button" image

An effective way to visually communicate to users that they can pay with Amamzon Pay is to display an Amazon Pay button on the screen.  
Since the Native app does not allow the placement of a real Amazon Pay button, we will place an image instead.

The "Amazon Pay button" image to be specified in this case is ". /nodejs/static/img/button_images". Please be careful not to specify a file name that begins with "Sandbox_" for production environments.  

### Start Secure WebView when the Amazon Pay button image is clicked.
When the "Amazon Pay Button" image above is clicked, the following code is called.  

```java
// Excerpt from MainActivity.java (Some parts have been modified for clarity.)

    static volatile String token = null;
        :
    void invokeAppLoginPage(Context context) {
        token = UUID.randomUUID().toString();
        invokeSecureWebview(context, "https://10.0.2.2:3443/appLogin?client=androidApp&token=" + token);
    }
        :
    private void invokeSecureWebview(Context context, String url) {
        CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder().build();

        // Specify Chrome as the Browser to launch
        Build(); // Specify Chrome as the Browser to launch // Note: Chrome is specified here because Amazon Pay does not support other browsers.
        // [Reference] https://pay.amazon.com/jp/help/202030010
        // If you need to launch other browsers that support Chrome Custom Tabs, please refer to the following source code for implementation.
        // [Reference] https://github.com/GoogleChrome/custom-tabs-client/blob/master/shared/src/main/java/org/chromium/customtabsclient/shared/ CustomTabsHelper.java#L64
        tabsIntent.intent.setPackage("com.android.chrome");

        // Set the flag to automatically exit Chrome Custom Tabs when transitioning to another Activity.
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Set the flag so that it does not remain as History when Chrome Custom Tabs is closed.
        Intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        // Launch Chrome Custom Tabs.
        tabsIntent.launchUrl(context, Uri.parse(url));
    }
```

You can see that we are launching Chrome Custom Tabs (Secure WebView on the Android side) by specifying the URL.  
In addition, we have generated a UUID (version 4) and named it "token" and set it as a parameter to the Field and URL on the Native side, but the reason for this is explained later.  

## Page that automatically transitions to the Amazon login screen

<img src="docimg/appLogin.png" width="500">  

This page outputs the Amazon Pay button behind the scenes, and automatically transitions to the Amazon login screen by clicking this button with JavaScript.  

### Preparing to output the Amazon Pay button on the server side
In order to prepare for the output of the Amazon Pay button, we will generate the payload and signature necessary for the output of the Amazon Pay button on the server side, and pass in the other configuration values.  

```js
// Excerpt from nodejs/app.js (Some parts have been modified for clarity.)

//-------------------
// App Login Screen
//-------------------

app.get('/appLogin', async (req, res) => {
    // * req.query will contain the URL parameter specified in ViewController above.
    res.render('appLogin.ejs', calcConfigs(`https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-${req.query. client}.html?token=${req.query.token}`));
});

function calcConfigs(url) {
    const payload = createPayload(url);
    const signature = apClient.generateButtonSignature(payload);
    return {payload: payload, signature: signature, merchantId: keyinfo.merchantId, publicKeyId: keyinfo.publicKeyId}
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

The specified URL "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/... in the specified URL will be the redirect destination after logging in to Amazon Pay & selecting the address and payment method.  
This URL is used to launch the app from Secure WebView with the "Applinks" technology described below.  

These values are passed as parameters to "appLogin.ejs" to generate the HTML, CSS & JavaScript.  

```html
<! -- Excerpt from nodejs/views/appLogin.ejs (Some parts have been modified for clarity.) --> :.

    :
<div class="hidden">
    <div id="AmazonPayButton"></div>
</div>

<script src="https://static-fe.payments-amazon.com/checkout.js"></script>
<script type="text/javascript" charset="utf-8">
    amazon.Pay.renderButton('#AmazonPayButton', {
        merchantId: '<%= merchantId %>',
        ledgerCurrency: 'JPY', // Amazon Pay account ledger currency
        sandbox: true, // dev environment
        checkoutLanguage: 'ja_JP', // render language
        productType: 'PayAndShip', // checkout type
        placement: 'Cart', // button placement
        buttonColor: 'Gold',
        createCheckoutSessionConfig: {
            payloadJSON: '<%- JSON.stringify(payload) %>', // string generated in step 2 (※ HTML Escapeをしないで出力する)
            signature: '<%= signature %>', // signature generated in step 3
            publicKeyId: '<%= publicKeyId %>'
        }
    });

    setTimeout(() => {
        document.getElementById("AmazonPayButton").click();
    }, 0);
</script>
```

:
<div class="hidden">
<div id="AmazonPayButton"></div>.
</div>

<script src="https://static-fe.payments-amazon.com/checkout.js"></script>
<script type="text/javascript" charset="utf-8">
amazon.Pay.renderButton('#AmazonPayButton', {
    merchantId: '<%= merchantId %>',
    ledgerCurrency: 'JPY', // Amazon Pay account ledger currency
    sandbox: true, // dev environment
    checkoutLanguage: 'ja_JP', // render language
    productType: 'PayAndShip', // checkout type
    placement: 'Cart', // button placement
    buttonColor: 'Gold',
    createCheckoutSessionConfig: {
        payloadJSON: '<%- JSON.stringify(payload) %>', // string generated in step 2 (* output without HTML Escape)
        signature: '<%= signature %>', // signature generated in step 3
        publicKeyId: '<%= publicKeyId %>'
    }
});

setTimeout(() => {
    document.getElementById("AmazonPayButton").click();
}, 0);
</script>
```

By generating an Amazon Pay button and having it click in JavaScript as shown above, we automatically transition to the Amazon Pay login screen.  
This file is created using Template Engine called [EJS](https://ejs.co/), but the syntax is common for Template Engine, so it should be relatively easy to understand.

## Triggering Applinks by redirecting from Amazon's screen

<img src="docimg/applinks.png" width="500">  

### About Applinks
For more information about Applinks, see [here](. /README_swv2app.md).

The basic condition for triggering Applinks is "tapping the Link in Chrome/Chrome Custom Tabs, etc.", but it may also be triggered by a Redirect from the Server.  
If Applinks is not triggered, the files existing at the specified URL will be displayed as usual.  

### Triggering two-stage Applinks with a rescue page
In this sample, we have specified a URL where Applinks will be triggered by a redirect after logging in and selecting an address and payment method on the Amazon page, but for the reasons mentioned above, there is a possibility that Applinks will not be triggered here.  

However, for the reasons mentioned above, there is a possibility that Applinks will not be triggered here. As a precaution, this sample is designed to automatically redirect the user to a relief page with a link to the URL where Applinks will be triggered again if it is not triggered.  
Here's how it works.  

The Android version of the URL that triggers Applinks, which appeared in "The page that automatically transitions to the Amazon login screen," is as follows  
https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-androidApp.html?token=XXXXXXXX

As mentioned above, if Applinks is not triggered, the file that exists at the specified URL will be displayed.  
An HTML file with the following contents is placed at the end of this URL.  

```html
<! -- The same thing is placed under nodejs/links. -->

<html>
<script>
    location.href = "https://10.0.2.2:3443/static/next.html" + location.search;
</script>
</html>
```

This redirects the file to "next.html" with the URL parameter specified when the file was accessed.  
Note: The above is for a local environment, so the redirect is set to "https://10.0.2.2:3443/static/next.html", but you may need to change this depending on your environment, such as production or testing.  
The content of "next.html" is as follows.  
```html
<! -- excerpt from nodejs/static/next.html -->

<body data-gr-c-s-loaded="true">
<div class="container">
<h3 class="my-4">Amazon Login processing completed</h3>.
Please tap the "Next" button. <br>
<br>
<a id="nextButton" href="#" class="btn btn-info btn-lg btn-block">
    Next
</a>
</div>
<script>
document.getElementById("nextButton").href =
    "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/index.html" + location.search;
</script>
</body>
```

The URL that triggers Applinks with the URL parameter specified at the time of access is specified in the "id="nextButton"" link.  
With this mechanism, if Applinks is not triggered, this screen will be displayed. By tapping on the "next" link, the user can ensure that the conditions are met and the Applinks are triggered.  

## Purchase page

<img src="docimg/purchase.png" width="650">  

### token check and setting the destination URL to ViewController
The process invoked by Applinks is as follows.  

```java
// Excerpt from AmazonPayActivity.java (Some parts have been modified for clarity.)

protected void onCreate(Bundle savedInstanceState) {
        :
    Intent intent = getIntent();
    if (intent.getScheme().equals("https")) {
        String appLinkAction = intent.getAction();
        Uri appLinkData = intent.getData();
        Log.d("[AppLink]", appLinkAction);
        Log.d("[AppLink]", "" + appLinkData);

        // Parse the URL parameter
        Map<String, String> map = new HashMap<>();
        for (String kEqV : appLinkData.getEncodedQuery().split("&")) {
            String[] kv = kEqV.split("=");
            map.put(kv[0], kv[1]);
        }

        if (MainActivity.token.equals(map.get("token"))) { // determine token match
            // If a match, build and display the purchase page
        } else {
            // if there is a mismatch, the transition is invalid and an error will be handled
        }

    } else {
        :
    }

    // This Activity's finish. (After this, the process will be moved to MainActivity#onResume)
    This.finish();
}
```

In this sample, Secure WebView (Chrome Custom Tabs) is already closed when this Activity is launched, because it is set to automatically close when another Activity is launched.

First, get the URL parameter that was specified in the URL that triggered the Applinks.  

After that, it judges whether the token passed from Secure WebView matches the token stored in MainActivity in "Processing the start of Secure WebView when the 'Amazon Pay Button' image is clicked".  
By judging the token, we can detect and raise an error if this process is launched with an invalid transition.

For example, let's say a bad user reads the URL to the "page that automatically transitions to the Amazon login screen" when launching Secure WebView, and sends it to another user via email.  
If the user who was sent the URL clicks on the link in the email on their Android device, Chrome may launch and take them to the Amazon Pay login.  
If the user logs in to Amazon Pay and selects an address and payment method, Chrome will also trigger Applinks, which means that if the user has installed the app, they will be able to execute the subsequent purchase flow.  
Since this may become a big problem depending on the screen flow, we have performed a token check in this sample app just in case.  

If there is no problem with the token check, a purchase page will be built and displayed.  
Since the purchase page needs information such as shipping address and amount, we need to call the following process on the server side to get these information.


```js
// Excerpt from nodejs/app.js (Some parts have been modified to make it easier to read.)

//-------------------------
// Checkout Review Screen
//-------------------------
app.get('/sample/checkoutReview', async (req, res) => {
    // Order information
    let order = {host: req.headers.host, amazonCheckoutSessionId: req.query.amazonCheckoutSessionId,
        client: req.cookies.client, hd8: req.cookies.hd8, hd10: req.cookies.hd10, items: []};
    order.items.push({id: 'item0008', name: 'Fire HD8', price: 8980, num: parseInt(order.hd8)});
    order.items.push({id: 'item0010', name: 'Fire HD10', price: 15980, num: parseInt(order.hd10)});
    order.items.forEach(item => item.summary = item.price * item.num); // Subtotal
    order.price = order.items.map(item => item.summary).reduce((pre, cur) => pre + cur); // total amount
    order.chargeAmount = Math.floor(order.price * 1.1); // amount including tax

    // Amazon Pay order information
    const payload = await apClient.getCheckoutSession(req.query.amazonCheckoutSessionId,
        {'x-amz-pay-idempotency-key': uuid.v4().toString().replace(/-/g, '')});
    order.checkoutSession = JSON.parse(payload.body);

    // Note: In general, order information is kept on the Server side using Session or DB, but this sample uses Cookie for simplicity.
    res.cookie('session', JSON.stringify(order), {secure: true});

    // TODO Modify the ↓ part to return data in a format that is easy for the app to receive, such as JSON.
    // res.render('sample/checkoutReview.ejs', order);
});
```


Calculate the cart information to get the amount, and also get the address information etc. from Amazon Pay API and return it.

### Processing when a purchase button is clicked

When the purchase button is clicked, the Checkout Session Update API on the server side is called as shown below, and the necessary information for payment, such as the purchase amount and the order number of the business, and the URL that will be automatically redirected on the payment processing page (see below) are specified. CheckoutSession.

```js
// Excerpt from nodejs/app.js (Some parts have been modified for clarity.)

//-----------------------------
// Checkout Session Update API
//-----------------------------

// Numbering of the order number on the business side
const newMerchantReferenceId = function() {
    let currentNumber = 1;
    return function() {
        return "MY-ORDER-" + currentNumber++;
    }
} ();

app.post('/sample/checkoutSession', async (req, res) => {
    let order = JSON.parse(req.cookies.session);
    const payload = await updateCheckoutSession({merchantReferenceId: newMerchantReferenceId(),
        merchantStoreName: "MY-SHOP", noteToBuyer: "Thank you!", customInformation: "This isn't shared with Buyer", . .order});    
    order.checkoutSession = JSON.parse(payload.body);

    // Note: In general, order information is kept on the Server side using Session or DB, but this sample uses Cookie for simplicity.
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
            paymentIntent: 'Authorize', paymentIntent: 'Authorize', canHandlePendingAuthorization: false,
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

This "URL to be automatically redirected on the payment processing page" is the URL to the relay page (see below), because the mobile app needs to launch the Native code.  
The return value from the Amazon Pay API is directly returned as a Response of the Checkout Session Update API in this sample app.  
The URLs that need to be redirected are as follows.

```
$.webCheckoutDetails.amazonPayRedirectUrl
```

When the Native app receives this Response, it will execute the following process using the above URL as a parameter.  

```java
// Excerpt from MainActivity.java

    void invokeAuthorizePage(Context context, String url) {
        invokeSecureWebview(context, url);
    }
```

With the above, you can open the URL included in the return value of the Amazon Pay API checkoutSession update process with Secure WebView.  

## Payment processing page

<img src="docimg/payment.png" width="400">  

When you access the URL passed from the Amazon Pay API above, the payment processing page (also called the spinner page) will be displayed.  
While this screen is displayed, Amazon is processing the payment, including credit, on the Server side, and error handling is also being handled on this screen.  
When the payment process is complete, you will be automatically redirected to the URL for the relay page specified in "Processing when clicking the purchase button".  

### Relay page
The relay page looks like the following.  

```html
<! -- excerpt from nodejs/static/dispatcher.html -->
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

Here, we use Intent to launch the application from JavaScript.  
For more information about Intent, please refer to [here](. /README_swv2app.md).  
Unlike Applinks, there is no possibility of accidentally launching a malicious app with Intent, so we do not pass sensitive information such as "amazonCheckoutSessionId" here.  

## Thanks page

<img src="docimg/thanks.png" width="600">  

### Native processing triggered by CustomURLScheme

The Native process invoked by the above Intent is as follows.

```java
// Excerpt from MainActivity.java

    protected void onCreate(Bundle savedInstanceState) {
            :
        Intent intent = getIntent();
        if (intent.getScheme().equals("https")) {
            :
            :
        } else {
            Log.d("[Intent]", "intent received!");
            // Build and display the Thanks page.
        }

        // finish of this Activity. (After this, the process will be moved to MainActivity#onResume)
        This.finish();
    }
```

Once we receive the Intent, we will build and display the Thanks page.
At this time, we need to call "completeCheckoutSession" for checkoutSession to complete, so please call the Server side process and execute the following.

```js
// Excerpt from nodejs/app.js (Some parts have been modified for easier viewing.)

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
    // res.render('sample/thanks.ejs', order); // Modify this part to return the data in a format that is easy for the app to receive, such as JSON.
});
```

The above is a series of steps for a Native app based on this sample app.
