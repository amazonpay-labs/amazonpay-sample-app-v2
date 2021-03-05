# Amazon Pay Mobile Sample App iOS App Implementation
This is the implementation of the iOS app side of this sample app. For instructions on how to install and run the app, please refer to [here](. /README_install.md).

# Operating environment
iOS version 11.2 or later: Safari Mobile 11 or later  
[Reference] https://pay.amazon.com/jp/help/202030010

# Other prerequisites
This sample app uses a technology called Universal Links, and the following conditions are required to use this technology.
 - You must be registered with the [Apple Developer Program](https://developer.apple.com/jp/programs/). 
 - Since the configuration file must be placed in a location on the Web that can be properly accessed using https, you must have a server with a different domain from the EC site, or an account with a cloud service such as AWS.  
   Note: In this sample application, [Amazon S3](https://aws.amazon.com/jp/s3/) is used. It is easy to get an account on the Internet, is widely used around the world, has a lot of information on how to use it, and has a free usage limit of 5GB for 12 months.  

# Overview
This sample application will work as shown in the video below.

<img src="docimg/ios-movie.gif" width="300">  

The details of the flow can be found in [flow-ios.xlsx](. /flow-ios.xlsx).  
Based on this flow, I will explain the detailed implementation in the following sections.

# How to implement Amazon Pay - WebView app version

## Cart page

<img src="docimg/cart.png" width="500">  

### Setting up Callback acceptance from the JavaScript side of the mobile app
In the mobile app, the Amazon Pay process needs to be executed on the Secure WebView, but since the Secure WebView cannot be launched directly from the WebView, it is necessary to configure it so that the Native code can be launched once from the WebView's JavaScript.  
The following code will do that.  

```swift
// Excerpt from ViewController.swift (Some parts have been modified for clarity.)

            // Set up callback acceptance from the JavaScript side
            let userContentController = WKUserContentController()
            userContentController.add(self, name: "iosApp")
            let webConfig = WKWebViewConfiguration();
            webConfig.userContentController = userContentController
            
            // Create WebView and load cart page
            webView = WKWebView(frame: rect, configuration: webConfig)
                :
                :
extension ViewController: WKScriptMessageHandler {
    // Callback from JavaScript side.
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        print("ViewController#userContentController")
                :
    }
}
````

With this configuration, it is possible to send a message from the JavaScript side to the Native side as shown below.
```js.
        webkit.messageHandlers.iosApp.postMessage(data);
```

### Client determination
This sample app supports all Android/iOS/normal Browser with the same HTML/JavaScript screen.  
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
                androidApp.login();
            } else {
                // → For iOS. 
                webkit.messageHandlers.iosApp.postMessage({op: 'login'});
            }
        });
        document.getElementById("AmazonPayButton").appendChild(node);
    }
```

In the first decision, if the browser is a normal browser, the Amazon Pay process can be implemented as is, so the Amazon Pay button is loaded as usual.  
In the case of iOS, we generate a node for the "Amazon Pay Button" image and add it under the "AmazonPayButton" node in the same screen.  
The "Amazon Pay Button" image to be specified at this time is ". /nodejs/static/img/button_images". Please be careful not to specify a file name that begins with "Sandbox_" for the production environment.  
Also, when the generated node is clicked, we add an Event Handler that calls the native Callback with the Object that specifies "login" as a parameter.  

### Start Secure WebView when the "Amazon Pay Button" image is clicked.
The following is the Native code that is called when the "Amazon Pay Button" image is clicked.  

```swift
// Excerpt from ViewController.swift (Some parts have been modified for clarity.)

extension ViewController: WKScriptMessageHandler {
    // Callback from JavaScript side.
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        print("ViewController#userContentController")
        switch message.name {
        case "IOSApp":
            print("IOSApp")
            
            if let data = message.body as? NSDictionary {
                let op = data["op"] as! String?
                switch op!{
                case "login":
                    invokeAppLoginPage() // ← In this case, "login" is specified, so this will be invoked
                case "auth":
                    invokeAuthorizePage(data["url"] as! String)
                default:
                    return
                }
            }
        default:
            return
        }
    }
}
````

The process of "invokeAppLoginPage()" is as follows.  
```swift
// Excerpt from ViewController.swift (Some parts have been modified for clarity.)

    var token: String?
        :
    func invokeAppLoginPage() {
        print("ViewController#invokeButtonPage")
        
        token = UUID().uuidString.lowercased()
        let safariView = SFSafariViewController(url: NSURL(string: "https://localhost:3443/appLogin?client=iosApp&token=\(token!)")! as URL)
        present(safariView, animated: true, completion: nil)
    }
```.

You can see that the URL is specified to launch SFSafariViewController (Secure WebView on iOS).  
In addition, we have generated a UUID (version 4) and named it "token", and set it as a parameter to the Field and URL on the Native side, but the reason for this is explained later.  

## Page that automatically transitions to the Amazon login screen

<img src="docimg/appLogin.png" width="500">  

This screen transitions to the Amazon login screen by using JavaScript to call the "initCheckout" method prepared by Amazon Pay.  

### Preparing to output the Amazon Pay button on the Server side
In preparation for outputting the Amazon Pay button, we will generate the payload and signature required for outputting the Amazon Pay button on the Server side, and pass in the other configuration values.  

``js
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
````

The specified URL "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/... in the specified URL will be the redirect destination after logging in to Amazon Pay & selecting the address and payment method.  
This URL is used to launch the Native code from Secure WebView with the "Universal Links" technology described below.  

These values are passed as parameters to "appLogin.ejs" to generate HTML & CSS & JavaScript.  

```html
<! -- Excerpt from nodejs/views/appLogin.ejs (Some parts have been modified for clarity.) --> :.

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
</script
```

This call to the "initCheckout" method automatically transitions to the Amazon Pay login screen.  
This file is created using Template Engine called [EJS](https://ejs.co/), but the syntax is common for Template Engine, so it should be relatively easy to understand.  

## Triggering Universal Links by redirecting from Amazon's screen

<img src="docimg/universallink.png" width="500">  

### About Universal Links
For more information about Universal Links, see [here](. /README_swv2app.md).

The basic condition for triggering Universal Links is "tapping the Link in Safari/SFSafariView etc.", but depending on the iOS version and other conditions, it may also be triggered by a Redirect from the Server.  
If Universal Links is not triggered, files that exist at the specified URL will be displayed as usual.  

### Triggering two-stage Universal Links with a rescue page
In this sample, we have specified a URL where Universal Links will be triggered by a redirect after the user logs in and selects an address and payment method on the Amazon page, but for the reasons mentioned above, it is possible that Universal Links will not be triggered here.  

However, for the reasons mentioned above, it is possible that Universal Links will not be triggered here. As a precaution, this sample is designed to automatically redirect the user to a relief page that has a link to a URL where Universal Links will be triggered again if it is not triggered.  
Here's how it works.  

The iOS version of the URL that triggers Universal Links, which appeared in "The page that automatically transitions to the Amazon login screen," is as follows  
https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-iosApp.html?token=XXXXXXXX

As mentioned above, if Universal Links is not triggered, the file that exists at the specified URL will be displayed.  
The following HTML file is placed at the end of this URL.  
```html
<! -- The same thing is placed under nodejs/links. -->

<html>
    <script>
        location.href = "https://localhost:3443/static/next.html" + location.search;
    </script>
</html>
````

This redirects the file to "next.html" with the URL parameter specified when the file was accessed.  
Note: The above is for a local environment, so the redirect is set to "https://localhost:3443/static/next.html", but you may need to change this depending on your environment, such as production or testing.  
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
````

The URL that triggers Universal Links with the URL parameter specified at the time of access is specified in the "id="nextButton"" link.  
With this mechanism, if Universal Links is not triggered, this screen will be displayed. When the user taps on this "next" link, the conditions are met and Universal Links are triggered without fail.


## Purchase page

<img src="docimg/purchase.png" width="650">  

### token check and setting up the URL for the transition to ViewController
The Naive code triggered by Universal Links is shown below.  

```swift
// Excerpt from AppDelegate (Some parts have been modified for clarity.)

    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([ UIUserActivityRestoring]?) -> Void) -> Bool {
        print("Universal Links!")
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb {
            print(userActivity.webpageURL!)
            
            // Parse the URL parameters
            var urlParams = Dictionary<String, String>.init()
            for param in userActivity.webpageURL!.query!.components(separatedBy: "&") {
                let kv = param.components(separatedBy: "=")
                urlParams[kv[0]] = kv[1].removingPercentEncoding
            }
            
            // get the current foremost SFSafariView and the ViewController behind it
            var sfsv = UIApplication.shared.keyWindow?.rootViewController
            var vc:ViewController? = nil
            while (sfsv!.presentedViewController) ! = nil {
                if let v = sfsv as? ViewController {
                    vc = v
                }
                sfsv = sfsv!.presentedViewController
            }
            
            if(vc?.token! == urlParams["token"]!) { // determine token match
                // If a match, set the URL of the purchase page to ViewController
                vc?.webviewUrl = "/sample/checkoutReview?amazonCheckoutSessionId=\(urlParams["amazonCheckoutSessionId"]!)"
            } else {
                // In case of a mismatch, set an error page because it is an invalid transition
                vc?.webviewUrl = "static/sample/error.html"
            }

            // close SFSafariView (after this, the process will be transferred to ViewController#viewDidLoad)
            (sfsv as? SFSafariViewController)? .dismiss(animated: false, completion: nil)
        }
        return true
    }
````
First, get the URL parameter that was specified in the URL that triggered Universal Links.  
Next, from the Application history hierarchy, get the SFSafariViewController that is displayed on the front page at this point, and the ViewController immediately below it.  

After that, we perform a match judgment between the token held in the ViewController in "Secure WebView startup processing when the 'Amazon Pay button' image is clicked" and the token passed from Secure WebView.  
By judging the token, if this process is invoked with an invalid transition, it will be detected and an error will be generated.  

For example, let's say a bad user reads the URL to the "page that automatically transitions to the Amazon login screen" when launching Secure WebView, and sends it to another user via email.  
If the user who was sent the URL clicks on the link in the email on their iOS device, Safari will launch and they may be redirected to the Amazon Pay login page.  
If the user logs into Amazon Pay and selects an address and payment method, Safari will also trigger Universal Links, which means that if the user has the app installed, they will be able to execute the purchase flow afterwards.  
Since this could be a big problem depending on the screen flow, this sample app performs a token check just in case.  

After the token check, set the URL of the purchase page to ViewController.  
The URL parameter "amazonCheckoutSessionId" is given to the URL of the purchase page, but this is the exact same URL and the exact same conditions as the transition to the purchase page in the PC and Mobile browsers.  
Therefore, there is no need to implement separate processes for "for mobile apps" and "for PC and Mobile browsers" when displaying the purchase page.  

Finally, close the SFSafariView (Secure WebView). This will move the process to ViewController#viewDidLoad immediately below.  

### Loading the purchase page

In ViewController, the following process in viewDidLoad will be invoked.  

```swift
// Excerpt from ViewController (Some parts have been modified to make it easier to read.)

                    :
            let url = webviewUrl
            if(url ! = nil) {
                webviewUrl = nil
                webView.evaluateJavaScript("loadUrl('\(url!)')", completionHandler: nil)
                    :
```

At this point, the cart page is displayed in WebView, and the following JavaScript is triggered above to start loading the purchase page.  

```js
    function loadUrl(url) {
        location.href = url;
    }
````

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
````

It calculates the amount of money by calculating the cart information, gets the address information from Amazon Pay API, and passes it to the template engine to generate and display the screen.

### Processing when a purchase button is clicked.

When you click the buy button, the following script will be executed.


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
Ajax will call the following Server-side Checkout Session Update API.  

``js
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

    // Note: Generally, order information is kept on the Server side using Session or DB, but in this sample, we use Cookie for simplicity.
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
```.

Using Amazon Pay's API, we update the checkoutSession with information such as the purchase amount and the order number of the business, which are required for payment, and the URL that will be automatically redirected on the payment processing page (see below).  
As for the "URL to be automatically redirected on the payment processing page," in the case of Browser, specify the URL of the Thanks page directly, and in the case of iOS and Android, specify the URL to the page for relay (see below).
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
                        coverScreen();
                        androidApp.auth(json.webCheckoutDetails.amazonPayRedirectUrl);
                    } else if(window.webkit && webkit.messageHandlers && webkit.messageHandlers.iosApp) {
                        // For iOS
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
```.

By checking the existence of the Callback Object passed to the WebView, the client environment is determined and the corresponding process is executed.  
In this case, since we are on iOS, the following will be executed.
``js
                        webkit.messageHandlers.iosApp.postMessage({op: 'auth', url: json.webCheckoutDetails.amazonPayRedirectUrl});
```

This will execute the following process on the Native side, using the string "auth" and the URL included in the Checkout Session Update API Response as parameters.  

```swift
// Excerpt from ViewController.swift

    // Callback from JavaScript side.
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        print("ViewController#userContentController")
        switch message.name {
        case "IOSApp":
            print("IOSApp")
            
            if let data = message.body as? NSDictionary {
                let op = data["op"] as! String?
                switch op!{
                case "login":
                    invokeAppLoginPage()
                case "auth":
                    invokeAuthorizePage(data["url"] as! String) // ← this is where it will be executed
                default:
                    return
                }
            }
        default:
            return
        }
    }
````

The "invokeAuthorizePage" is as follows.

```swift
    func invokeAuthorizePage(_ url: String) {
        print("ViewController#invokeAuthorizePage")
        let safariView = SFSafariViewController(url: NSURL(string: url)! as URL)
        present(safariView, animated: true, completion: nil)
    }
```
With the above, you can open the URL that was included in the return value of the Amazon Pay API checkoutSession update process in Secure WebView.  

## Payment processing page

<img src="docimg/payment.png" width="400">  

When you access the URL passed from the Amazon Pay API above, the payment processing page (also known as the spinner page) will be displayed.  
While this screen is being displayed, Amazon is processing the payment, including credit, on the Server side, and error handling is also being handled on this screen.  
When the payment process is complete, you will be automatically redirected to the URL for the relay page specified in "Processing when clicking the purchase button".  

### Relay page
The relay page looks like the following.  

```html
<! -- excerpt from nodejs/static/dispatcher.html -->
    :
<script type="text/javascript" charset="utf-8">
    function getURLParameter(name, source) {
        return decodeURIComponent((new RegExp('[? |&amp;|#]' + name + '=' +
                        '([^&;]+?)') (&|#|;|$)').exec(source) || [, ""])[1].replace(/\+/g, '%20')) || null;
    }

    const client = getURLParameter("client", location.search);
    location.href = client === 'IOSApp' 
        ? 'amazonpay-ios-v2://thanks'
        : 'intent://amazon_pay_android_v2#Intent;package=com.amazon.pay.sample.android_app_v2;scheme=amazon_pay_android_v2;end;';
</script

<body></body>
</html>
```

Here we are using CustomURLScheme to launch the app from JavaScript.  
For more information about CustomURLScheme, please refer to [here](. /README_swv2app.md).  
Unlike Universal Links, CustomURLScheme does not pass sensitive information such as "amazonCheckoutSessionId" because there is no possibility of accidentally launching a malicious app.  

## Thanks page

<img src="docimg/thanks.png" width="600">  

### Native processing triggered by CustomURLScheme

The native process invoked by the above CustomURLScheme is as follows.

```swift
// Excerpt from AppDelegate.swift

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        print("Custom URL Scheme!")
        
        var sfsv = UIApplication.shared.keyWindow?.rootViewController
        var vc:ViewController? = nil
        
        // Get the current foremost SFSafariView and the ViewController behind it
        while (sfsv!.presentedViewController) ! = nil {
            if let v = sfsv as? ViewController {
                vc = v
            }
            sfsv = sfsv!.presentedViewController
        }
        
        // Set the URL of the Thanks page to ViewController
        vc?.webviewUrl = "/sample/thanks"
        
        // close SFSafariView (after this, the process will be transferred to ViewController#viewDidLoad)
        (sfsv as? SFSafariViewController)? .dismiss(animated: false, completion: nil)
        
        return true
    }
````

From the Application history hierarchy, get the SFSafariViewController that is displayed on the topmost page at this point, and the ViewController just below it.  
Next, set the URL of the Thanks page to the ViewController.  
Finally, close the SFSafariView (Secure WebView). This will move the process to ViewController#viewDidLoad immediately below.  

### Loading the Thanks page

In ViewController, the following process in viewDidLoad will be invoked.  

```swift
// Excerpt from ViewController (Some parts have been modified to make it easier to read.)

                    :
            let url = webviewUrl
            if(url ! = nil) {
                webviewUrl = nil
                webView.evaluateJavaScript("loadUrl('\(url!)')", completionHandler: nil)
                    :
```

At this point, the purchase page is displayed in the WebView, and the following JavaScript is triggered above to start loading the Thanks page.  

```js
    function loadUrl(url) {
        location.href = url;
    }
````

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
````

If you don't call this function, your screen will look like the following when Secure WebView is closed.  
<img src="docimg/nocover-version.gif" width="300">  
Since the screen before Secure WebView is displayed until the WebView screen transition is completed, it looks unnatural.  

By calling "coverScreen" just before Secure WebView starts, you can make it look more natural as shown below.  
<img src="docimg/cover-version.gif" width="300">.  


If this is not done, when the user taps the "Done" button in the upper left corner of the Secure WebView and returns to the WebView, the screen will remain blank.  
In such a case, call "uncoverScreen" in the following code of ViewController#viewDidLoad to restore the white screen.   

```swift
// Excerpt from ViewController
                webView.evaluateJavaScript("if(window.uncoverScreen) {uncoverScreen();}", completionHandler: nil)
```

In this sample, "coverScreen" simply displays a blank screen, but we recommend that you display something that looks more natural here, depending on the design and policies of each mobile app.  


# How to implement Amazon Pay - Native App Version

## Cart Page or Product Page
<img src="docimg/cart.png" width="500">  

### Placement of the "Amazon Pay Button" image

An effective way to visually communicate to users that they can pay with Amamzon Pay is to display an Amazon Pay button on the screen.  
Since the Native app does not allow the placement of a real Amazon Pay button, we will place an image instead.

The "Amazon Pay button" image to be specified in this case is ". /nodejs/static/img/button_images". Please be careful not to specify a file name that begins with "Sandbox_" for production environments.  

### Start Secure WebView when the Amazon Pay button image is clicked.
When the "Amazon Pay Button" image is clicked, the following code will be called.  
```swift
// Excerpt from ViewController.swift (Some parts have been modified for clarity.)

    var token: String?
        :
    func invokeAppLoginPage() {
        print("ViewController#invokeButtonPage")
        
        token = UUID().uuidString.lowercased()
        let safariView = SFSafariViewController(url: NSURL(string: "https://localhost:3443/appLogin?client=iosApp&token=\(token!)")! as URL)
        present(safariView, animated: true, completion: nil)
    }
```.

You can see that the URL is specified to launch SFSafariViewController (Secure WebView on iOS).  
In addition, we have generated a UUID (version 4) and named it "token", and set it as a parameter to the Field and URL on the Native side, but the reason for this is explained later.  

## Page that automatically transitions to the Amazon login screen

<img src="docimg/appLogin.png" width="500">  

This page outputs the Amazon Pay button behind the scenes, and automatically transitions to the Amazon login screen by clicking this button with JavaScript.  

### Preparing to output the Amazon Pay button on the server side
In order to prepare for the output of the Amazon Pay button, we will generate the payload and signature necessary for the output of the Amazon Pay button on the server side, and pass in the other configuration values.  

``js
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
````

The specified URL "https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/... in the specified URL will be the redirect destination after logging in to Amazon Pay & selecting the address and payment method.  
This URL is used to launch the app from Secure WebView using the "Universal Links" technology described below.  

These values are passed as parameters to "appLogin.ejs" to generate the HTML, CSS, and JavaScript.  

```html
<! -- Excerpt from nodejs/views/appLogin.ejs (Some parts have been modified for clarity.) --> :.

    :
<div class="hidden">
    <div id="AmazonPayButton"></div>.
</div>.

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

## Triggering Universal Links by redirecting from Amazon's screen

<img src="docimg/universallink.png" width="500">  

### About Universal Links
For more information about Universal Links, see [here](. /README_swv2app.md).

The basic condition for triggering Universal Links is "tapping the Link in Safari/SFSafariView etc.", but depending on the iOS version and other conditions, it may also be triggered by a Redirect from the Server.  
If Universal Links is not triggered, files that exist at the specified URL will be displayed as usual.  

### Triggering two-stage Universal Links with a rescue page
In this sample, we have specified a URL where Universal Links will be triggered by a redirect after the user logs in and selects an address and payment method on the Amazon page, but for the reasons mentioned above, it is possible that Universal Links will not be triggered here.  

However, for the reasons mentioned above, it is possible that Universal Links will not be triggered here. As a precaution, this sample is designed to automatically redirect the user to a relief page that has a link to a URL where Universal Links will be triggered again if it is not triggered.  
Here's how it works.  

The iOS version of the URL that triggers Universal Links, which appeared in "The page that automatically transitions to the Amazon login screen," is as follows  
https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-iosApp.html?token=XXXXXXXX

As mentioned above, if Universal Links is not triggered, the file that exists at the specified URL will be displayed.  
The following HTML file is placed at the end of this URL.  
```html
<! -- The same thing is placed under nodejs/links. -->

As written above, if Universal Links is not triggered, the file that exists at the specified URL will be displayed.  
The following HTML file is placed at the end of this URL.  
```html
<! -- The same thing is placed under nodejs/links. -->

<html>
    <script>
        location.href = "https://localhost:3443/static/next.html" + location.search;
    </script>
</html>
````

This redirects the file to "next.html" with the URL parameter specified when the file was accessed.  
Note: The above is for a local environment, so the redirect is set to "https://localhost:3443/static/next.html", but you may need to change this depending on your environment, such as production or testing.  
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
````

The URL that triggers Universal Links with the URL parameter specified at the time of access is specified in the "id="nextButton"" link.  
With this mechanism, if Universal Links is not triggered, this screen will be displayed. By tapping on this "next" link, the user can ensure that the conditions are met and Universal Links are triggered.  

## Purchase page

<img src="docimg/purchase.png" width="650">  

### token check and setting the destination URL to ViewController
The following is the process triggered by Universal Links.  

```swift
// Excerpt from AppDelegate (Some parts have been modified for clarity.)

    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([ UIUserActivityRestoring]?) -> Void) -> Bool {
        print("Universal Links!")
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb {
            print(userActivity.webpageURL!)
            
            // Parse the URL parameters
            var urlParams = Dictionary<String, String>.init()
            for param in userActivity.webpageURL!.query!.components(separatedBy: "&") {
                let kv = param.components(separatedBy: "=")
                urlParams[kv[0]] = kv[1].removingPercentEncoding
            }
            
            // get the current foremost SFSafariView and the ViewController behind it
            var sfsv = UIApplication.shared.keyWindow?.rootViewController
            var vc:ViewController? = nil
            while (sfsv!.presentedViewController) ! = nil {
                if let v = sfsv as? ViewController {
                    vc = v
                }
                sfsv = sfsv!.presentedViewController
            }
            
            // close SFSafariView (after this, the process will be transferred to ViewController#viewDidLoad)
            (sfsv as? SFSafariViewController)? .dismiss(animated: false, completion: nil)

            if(vc?.token! == urlParams["token"]!) { // determine token match
                // If a match, build and display the purchase page
            } else {
                // if there is a mismatch, error handling because it is an invalid transition
            }
        }
        } return true
    }
````
First, get the URL parameter that was specified in the URL that triggered Universal Links.  
Next, from the history hierarchy of the Application, get the SFSafariViewController that is displayed at the top at this point, and the ViewController immediately below it.  
Then, close the SFSafariView (Secure WebView).  

After that, the matching judgment is made between the token held in the ViewController in "Processing the Startup of Secure WebView when the 'Amazon Pay Button' Image is Clicked" and the token passed from Secure WebView.  
By judging the token, if this process is invoked with an invalid transition, it will be detected and an error will be generated.  

For example, let's say a bad user reads the URL to the "page that automatically transitions to the Amazon login screen" when launching Secure WebView, and sends it to another user via email.  
If the user who was sent the URL clicks on the link in the email on their iOS device, Safari will launch and they may be redirected to the Amazon Pay login page.  
If the user logs into Amazon Pay and selects an address and payment method, Safari will also trigger Universal Links, which means that if the user has the app installed, they will be able to execute the purchase flow afterwards.  
Since this may become a big problem depending on the screen flow, we have checked the token in this sample app just in case.  

If there is no problem with the token check, a purchase page will be built and displayed.  
Since the purchase page needs information such as shipping address and amount, we need to call the following process on the server side to get these information.

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
    
    // TODO Modify the ↓ part to return data in a format that is easy for the app to receive, such as JSON.
    // res.render('sample/checkoutReview.ejs', order);
});
````
It calculates the amount of money by calculating the cart information, and also retrieves the address information from Amazon Pay API and returns it.

### Processing when a purchase button is clicked

When the purchase button is clicked, the Checkout Session Update API on the server side is called as shown below, and it is necessary to update the checkoutSession with information such as the purchase amount required for payment, the order number on the business side, and the URL that will be automatically redirected on the payment processing page (see below). CheckoutSession.

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
```.

This "URL to be automatically redirected on the payment processing page" is the URL to the relay page (see below), because the mobile app needs to launch the Native code.  
The return value from the Amazon Pay API is directly returned as a Response of the Checkout Session Update API in this sample app.  
The URLs that need to be redirected are as follows.
```.
$.webCheckoutDetails.amazonPayRedirectUrl
``` $.webCheckoutDetails.amazonPayRedirectUrl

When the Native app receives this Response, it will execute the following process using the above URL as a parameter.  

```swift
    func invokeAuthorizePage(_ url: String) {
        print("ViewController#invokeAuthorizePage")
        let safariView = SFSafariViewController(url: NSURL(string: url)! as URL)
        present(safariView, animated: true, completion: nil)
    }
````

With the above, you can open the URL included in the return value of the checkoutSession update process of Amazon Pay API with Secure WebView.  

## Payment processing page

<img src="docimg/payment.png" width="400">  

When you access the URL passed from the Amazon Pay API above, the payment processing page (also known as the spinner page) will be displayed.  
While this screen is being displayed, Amazon is processing the payment, including credit, on the Server side, and error handling is also being handled on this screen.  
When the payment process is complete, you will be automatically redirected to the URL for the relay page specified in "Processing when clicking the purchase button".  

### Relay page
The relay page looks like the following.  

```html
<! -- excerpt from nodejs/static/dispatcher.html -->
    :
<script type="text/javascript" charset="utf-8">
    function getURLParameter(name, source) {
        return decodeURIComponent((new RegExp('[? |&amp;|#]' + name + '=' +
                        '([^&;]+?)') (&|#|;|$)').exec(source) || [, ""])[1].replace(/\+/g, '%20')) || null;
    }

    const client = getURLParameter("client", location.search);
    location.href = client === 'IOSApp' 
        ? 'amazonpay-ios-v2://thanks'
        : 'intent://amazon_pay_android_v2#Intent;package=com.amazon.pay.sample.android_app_v2;scheme=amazon_pay_android_v2;end;';
</script

<body></body>
</html>
```

Here we are using CustomURLScheme to launch the app from JavaScript.  
For more information about CustomURLScheme, please refer to [here](. /README_swv2app.md).  
Unlike Universal Links, CustomURLScheme does not pass sensitive information such as "amazonCheckoutSessionId" because there is no possibility of accidentally launching a malicious app.  

## Thanks page

<img src="docimg/thanks.png" width="600">  

### Native processing triggered by CustomURLScheme

The Native process invoked by the above CustomURLScheme is as follows.

```swift
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        print("Custom URL Scheme!")
        
        var sfsv = UIApplication.shared.keyWindow?.rootViewController
        var vc:ViewController? = nil
        
        // Get the current foremost SFSafariView and the ViewController behind it
        while (sfsv!.presentedViewController) ! = nil {
            if let v = sfsv as? ViewController {
                vc = v
            }
            sfsv = sfsv!.presentedViewController
        }
        
        // close SFSafariView (after this, the process will be transferred to ViewController#viewDidLoad)
        (sfsv as? SFSafariViewController)? .dismiss(animated: false, completion: nil)

        // build and display the thanks page
        
        return true
    }
````
From the Application history hierarchy, retrieve the SFSafariViewController that is displayed on the front page at this point, and the ViewController immediately below it.  
Next, close the SFSafariView (Secure WebView). This will move the process to ViewController#viewDidLoad immediately below.  
Then, the process of constructing and displaying the Thanks page is performed.

At this point, we need to call "completeCheckoutSession" for the checkoutSession to complete it, so please call the Server side process and execute the following.

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
````

The above is a series of steps for a Native app based on this sample app.
