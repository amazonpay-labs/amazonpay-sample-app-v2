/**
 * 注意: こちらのプログラムはJavaScriptで書かれていますが、Server側で動作します。
 * Note: The program written in this file runs on server side even it is written in JavaScript.
 */
'use strict';

// Config
const fs = require('fs');
const options = {
    key: fs.readFileSync('ssl/sample.key'),
    cert: fs.readFileSync('ssl/sample.crt')
};
const {keyinfo} = require('./keys/keyinfo');

// Web application
const express = require('express');
const app = express();
const ejs = require('ejs');
const bodyParser = require('body-parser')
const cookieParser = require('cookie-parser')
const https = require('https');
app.set('ejs', ejs.renderFile)
app.use(bodyParser.urlencoded({extended: true}))
app.use(bodyParser.json())
app.use(cookieParser());
const appServer = https.createServer(options, app);

// Amazon Pay SDK
const Client = require('@amazonpay/amazon-pay-api-sdk-nodejs');
const apClient = new Client.WebStoreClient({
    publicKeyId: keyinfo.publicKeyId,
    privateKey: fs.readFileSync('keys/privateKey.pem'),
    region: 'jp',
    sandbox: true
});

// Other
const uuid = require('uuid');

// html, css, png等の静的ファイルを配置するstaticディレクトリの読み込み設定
app.use('/static', express.static('static'));

//-------------------
// Cart Screen
//-------------------
app.get('/sample/cart', async (req, res) => {
    res.render (
        'sample/cart.ejs', 
        newConfig (
            newPayload ("https://localhost:3443/sample/checkoutReview")
        )
    );
});

//-------------------
// App Login Screen
//-------------------
app.get('/appLogin', async (req, res) => {
    res.render (
        'appLogin.ejs', 
        newConfig (
            newFullPayload (
                `https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-${req.query.client}.html?token=${req.query.token}`,
                `https://${req.headers.host}/static/cancel.html`
            )
        )
    );
});

function newConfig(payload) {
    return {
        payload: payload, 
        signature: apClient.generateButtonSignature(payload), 
        merchantId: keyinfo.merchantId, 
        publicKeyId: keyinfo.publicKeyId
    };
}

function newPayload(url) {
    return {
        webCheckoutDetails: {
            checkoutReviewReturnUrl: url
        },
        storeId: keyinfo.storeId
    };
}

function newFullPayload(url, cancelUrl) {
    return {
        webCheckoutDetails: {
            checkoutReviewReturnUrl: url,
            checkoutCancelUrl: cancelUrl
        },
        storeId: keyinfo.storeId
    };
}

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

//---------------------
// Start App server
//---------------------
const APP_PORT = process.env.APP_PORT || 3443;
appServer.listen(APP_PORT);
console.log(`App listening on port ${APP_PORT}`);
