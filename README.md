# About this sample application
This is a sample implementation of a mobile application for purchasing products using Amazon Pay on a SmartPhone.  
In this sample app, Amazon Pay is processed using a secure browser technologies below that can be used from mobile apps:  
  * Android: Chrome Custom Tabs  
  * iOS: SFSafariViewController  

And when the purchase flow on Amazon pay is finished, it is returned to the app side.  
In this document and in the code of the sample app, both "Chrome Custom Tabs" and "SFSafariViewController" are referred to together as "*Secure WebView*".  

The rest of the code is almost the same as the normal Amazon Pay implementation.  
Therefore, you can implement it by referring to the Amazon Pay developer's page below, and you can also share much of the source code with the page for regular PC/Mobile browsers.  
https://amazon-pay-v2.s3-ap-northeast-1.amazonaws.com/V2_Documents.html  

This sample app also has the general Amazon Pay implementation for regular PC/Mobile browsers and the implementation for mobile apps (Android/iOS) in the same code.

The other browser technology that can be used from mobile apps is WebView, in addition to Secure WebView.  
WebView is not supported by Amazon Pay for security reasons, so you cannot implement Amazon pay safely with WebView but Secure WebView.
Reference: https://developer.amazon.com/ja/docs/amazon-pay-onetime/webview.html  
Even if your app is built with WebView, it can be implemented in a safe and supported manner if you follow the instructions in this sample app.  

This sample application consists of three projects: [nodejs](nodejs/README.md) (server-side implementations), [android](android/README.md), and [ios](ios/README.md). Please refer to the respective READMEs for instructions on how to set up each project, and for explanations of the technical elements used.  

# System Requirements
Android 6 or later: Google Chrome 64 or later  
iOS version 11 or later

# Overview
This sample application works on both Android and iOS as shown in the movie below.

<img src="android/docimg/android-movie.gif" width="300">  


As shown in the following figure, it works by communicating like below:

* WebView ←→ Native ←→ Secure WebView  


![](nodejs/docimg/flow.png)

This sample application is running on WebView, however, as you can see in the figure, WebView always goes through the Native processing first before interacting with Secure WebView.
Therefore, even in the case of Native applications, you can implement Amazon Pay by referring to this sample application.  

# Overview of the tasks required to implement Amazon Pay
## [Reference] Tasks required for implementation in Browser versions for PC and Mobile
When Amazon Pay is implemented in the Browser of PC and Mobile, the Flow is generally as follows.  

![](nodejs/docimg/browser-flow.png)  

The required tasks are as follows: 

1. place the Amazon Pay button on the cart page or product page.
    - At this time, set the URL of the purchase page, which will be redirected in ②.
2. "amazonCheckoutSessionId" will be passed to the URL at the time of the redirect in ②.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as a parameter, acquire the purchaser's name and address information, reflect it on the review page, and display it. 
3. Perform the following processing when the purchase button is clicked.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as the parameter, and set the information necessary for payment, such as the amount of money, and the URL of the Thanks page, the redirect destination in ④.
    - The return value of this API contains the URL to the "payment processing page" in ③, so redirect to that page. 
4. When the payment process, such as authorization, is completed on the Amazon side screen, the redirect in ④ will occur automatically.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as the parameter to complete the Amazon Pay payment session, and display the Thanks page.

## Tasks required for implementation in the mobile app version
When implementing Amazon Pay in a mobile app, the basic Flow is the same, and is generally as follows.  

![](nodejs/docimg/app-flow.png)  

Many of the necessary tasks are basically the same as in the Browser, but there are some additional tasks because the Amazon side processing must be executed on Secure WebView.  
The parts that differ from the Browser are indicated with ***bold type***.  

### For WebView application
As shown below, many parts are the same as Browser. 

1. Place an ***"Amazon Pay Button" image*** on the cart page or product page.
    - ***When this image is tapped, Secure WebView will display "the page that automatically transitions to the Amazon login screen" (described later in [android](android/README.md) and [ios](ios/README.md)).***
    - At this time, set ***the URL (iOS: Universal Links, Android: Applinks) to launch the native code*** that will be redirected in ②.
2. "amazonCheckoutSessionId" is passed to the URL at the time of the redirect in ②.
    - ***Native code is started, so get "amazonCheckoutSessionId" included in the URL, and give it to redirect WebView to the purchase page***.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as a parameter, obtain the purchaser's name and address information, and reflect it on the purchase page for display. 
3. Perform the following processing when the purchase button is clicked.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as the parameter, and provide the information necessary for payment, such as the amount of money, and the URL of ***the "page for relay" (descriped later in [android](android/README.md) and [ios](ios/README.md)) to launch the native code***.
    - The return value of this API contains the URL to the "payment processing page" in ③, display it ***with Secure WebView***. 
4. When the payment process, such as authorization, is completed on the Amazon screen, the redirection in ④ will occur automatically.
    - ***Native code is invoked by the "page for relay", so redirect the WebView to the Thanks page***.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as a parameter to complete the Amazon Pay payment session, and display the Thanks page.

### For Native apps
As shown below, many parts are common to Browser.  
In the case of a Native app, the screen display and so on need to be implemented separately from the Browser, but the processing on the Secure WebView and the Server side are common. 

1.  Place a ***"Amazon Pay Button" image*** on the cart page or product page.
    - ***When this image is tapped, the Secure WebView will display "the page that automatically transitions to the Amazon login screen" (described later in [android](android/README.md) and [ios](ios/README.md)).***
    - At this time, set ***the URL (iOS: Universal Links, Android: Applinks) to launch the native code*** that will be redirected in ②.
2. "amazonCheckoutSessionId" is passed to the URL at the time of the redirect in ②.
    - ***Native code is started, so get "amazonCheckoutSessionId" included in the URL.***
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as a parameter, acquire the purchaser's name and address information, and reflect it on the review page for display. 
3. Perform the following processing when the purchase button is clicked.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as the parameter, and provide the information necessary for payment, such as the amount of money, and the URL of ***the "page for relay" (descriped later in [android](android/README.md) and [ios](ios/README.md)) to launch the native code***.
    - The return value of this API contains the URL to the "payment processing page" shown in ③, so display it ***with Secure WebView***. 
4. When the payment process, such as authorization, is completed on the Amazon screen, the redirection in ④ will occur automatically.
    - ***Native code is invoked by the "page for relay"***.
    - On the Server side, call the Amazon Pay API with "amazonCheckoutSessionId" as a parameter to complete the Amazon Pay payment session, and display the Thanks page.

# Check the details of this sample application and how to run it.
First, please refer to [nodejs](nodejs/README.md) to run the web application side. You can check the operation of this application from a normal browser.  
After that, please refer to [android](android/README.md) and [ios](ios/README.md) to run the mobile application.  
