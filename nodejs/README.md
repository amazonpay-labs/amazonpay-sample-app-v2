# Amazon Pay Mobile Sample App Web Application Side Implementation
This is the implementation of the web application side of the entire sample application.

## Operating environment
nodejs: v12.16.1 or higher  
We have not tested it, though it will probably work with a lower version.  
(Reference) Installation using the installer & package manager:  
  - https://nodejs.org/ja/download/
  - https://nodejs.org/ja/download/package-manager/

## Overview
This web application accepts requests from WebView and Secure WebView, outputs screens, transitions screens, calls Amazon Pay API, and processes payments.  
For more details, please refer to [android](../android/README.md), [ios](../ios/README.md) for more details.

# Install the server side.

## clone the repository
If you haven't already, please clone this repository first.  

```
git clone https://github.com/amazonpay-labs/amazonpay-sample-app-v2.git
```

Under the cloned directory, under the "nodejs" directory is the project for this web application.  

## Create and configure the application in Seller Central
Under the nodejs/keys/template directory, add
  - keyinfo.js  
  - privateKey.pem

under the nodejs/keys/template directory to a directory directly under the nodejs/keys directory one level up.  

Prepare an application for this sample at [Seller Central](https://sellercentral-japan.amazon.com/) and [here](https://amazonpaycheckoutintegrationguide.s3.amazonaws.com/amazon-pay-checkout/get-set-up-for-integration.html#5-get-your-public-key-id) to obtain the Merchant ID, Public Key ID, Store ID, Store ID, and Private Key, respectively, and copy them to the following
  * Merchant ID: merchantId from nodejs/keys/keyinfo.js
  * Public Key ID: publicKeyId in nodejs/keys/keyinfo.js
  * Store ID: storeId in nodejs/keys/keyinfo.js
  * Private Key: nodejs/keys/privateKey.pem

## Configure https settings for web server
[here](./ssl/README.md) to create a key and certificate for https communication.

## Install the dependent modules
In this directory, execute the following command to install the dependent modules.
```sh
npm i
````

## Start the server
Execute the following command in this directory.

```sh
node app.js
```

### Test in browser
Go to [https://localhost:3443/sample/cart](https://localhost:3443/sample/cart). If you ignore the security warning and proceed, you will see the following screen.
![](docimg/browser.png)

This sample application will also work in a browser on PC/Mobile, so please use it to check the operation of the application and understand its behavior.
