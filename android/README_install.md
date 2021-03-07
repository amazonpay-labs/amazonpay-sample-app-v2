# Installation instructions

## Notes 1
First, install the [nodejs](. /nodejs/README.md) side first.

## Notes 2
The Android version of this sample application requires the configuration file generated in the following steps to be placed in a location that satisfies the following conditions, so it will not run properly unless you have a server or cloud service account that satisfies the conditions.  
  * The file must be accessible via https (using a valid certificate recognized by the Android OS, not a self-certificate).  
  * The Content-Type of the file must be "application/json".  
  * The file should be placed under "domain root/.well-known/".  

In this sample application, we use [Amazon S3](https://aws.amazon.com/jp/s3/) for this purpose. We recommend this service because it is easy to get an account on the Internet, it is widely used around the world, there is a lot of information on how to use it, and there is a free usage limit of 5GB for 12 months.

If you are not able to prepare it immediately, we recommend you to try the iOS version first, as it works almost the same and you can run this sample without the above.  

## Open the project and launch the sample app
This project can be opened with [Android Studio (free)](https://developer.android.com/studio/). You can also open it with other IDEs, but here we will explain how to open it with the above IDE.  
First, launch Android Studio.  
*If you do not see the following screen, please close all the projects that are open in Android Studio. *  
![androidstudio-welcome](docimg/android_welcome.png)
Import Project" → select the project you cloned → "Open".  
The project will open and the Gradle build will start, please wait a few minutes until it finishes.  
When it is finished, start the application by clicking "Run" → "Run app" in the menu or the "Run app" button at the top of the screen.
![androidstudio-project](docimg/android_project.png)
The following screen will open to select the Android device or Virtual Device (a virtual Android device that will be launched by the Emulator) to run the application. In this tutorial, we will explain how to run the application on the Emulator.  
Click "Create New Virtual Device".  
Click "Create New Virtual Device. [androidstudio-select-emu](docimg/android_select_emu.png)
This sample will work with API Level 24 or higher, so if there is a Virtual Device with the appropriate version, select that one.
If not, click on "Create New Virtual Device" to create a Virtual Device.  
![androidstudio-select-hard](docimg/android_select_hard.png)
Select "Phone" in "Category" on the left and select the device you want to use for development.  
*If you are not particular about it, you can use the default selection. *  
Click "Next".
![androidstudio-select-version](docimg/android_select_ver.png)
Select the version you like from API Level 24 and above, and click "Next".  
*If you have not downloaded the file yet, click "Download" and follow the instructions on the screen to download the file. *!
![androidstudio-select-finish](docimg/android_select_fin.png)
After "Finish", the Virtual Device will start to be generated.  
After the generation is complete, you will be able to select the generated Virtual Device, so select this one and click "OK".
![androidstudio-select-emu](docimg/android_select_emu.png)
The Emulator will start up and the sample application will run. (This will take a minute or two.)  
<img src="docimg/emu_start.png" width="300">

## Install the self-certificate
In this sample, a self-certificate is used as the SSL certificate on the server side, so the self-certificate must be installed on the Android side in order for the sample application to work properly.  
In this section, we will explain how to install the self-certificate on the Virtual Device started by the Emulator. 1.

Setting PIN lock  
For security reasons, the SSL certificate cannot be installed on Android without setting a PIN.  
To do this, open the settings screen and select "Screen lock" from the security settings to set the PIN.  
*How to open the settings screen and the various settings will vary depending on the device and OS version, so if you are not sure, please search on Google or other search engines.  
For reference, typical ways to open the settings screen include clicking on the app list icon and selecting it, or swiping from the bottom of the home screen to bring up the app list and selecting it. *  
<img src="docimg/emu_pin.png" width="300"> 2.  

Download & Install the SSL self-certificate  
Launch Chrome and access the following URL  
https://10.0.2.2:3443/static/crt/sample.crt  
When you get the warning as below, click "ADVANCED" -> "PROCEED TO 10.0.2.2(UNSAFE)  
<img src="docimg/emu_warn.png" width="300">  
"CONTINUE"  
<img src="docimg/emu_accept-download.png" width="300">  
"ALLOW"  
<img src="docimg/emu_allow-chrome.png" width="300">   
"DOWNLOAD"  
<img src="docimg/emu_download-crt.png" width="300">  
You will be asked for your PIN, enter the value you just set.  
On the Install Certificate screen that appears, enter a suitable name in the Name field, make sure "VPN and apps" is selected, and click "OK" to complete the installation.  
<img src="docimg/emu_install.png" width="300">  

## Setting up Applinks
In this sample application, we use a technology called Applinks to launch Secure WebView → Native application.  
To use Applinks, you need to generate a special configuration file (assetlinks.json) and place it in the following condition.
  * The file must be accessible via https (using a correct certificate that iOS can recognize, not a self-certificate).  
  * Content-Type must be "application/json" when retrieving the file.  
  * The file must be placed under "domain root/.well-known/".  

Assuming that the above is ready, we will now explain how to generate and deploy the configuration file.  

### Generating the configuration file
Start "Tool" -> "App Links Assistant".  
![androidstudio-welcome](docimg/applinks-1.png)

Click "Open URL Mapping Editor" in ① of the launched App Links Assistant.  
![androidstudio-welcome](docimg/applinks-2.png)

One of the URLs is registered in the "URL Mapping" table, so double-click this one.  
![androidstudio-welcome](docimg/applinks-show-existing.png)

Replace the "amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com" part of Host with the domain of the server you prepared, and click "OK".  
![androidstudio-welcome](docimg/applinks-update-host.png)

Next, click on (3), "Open Digital Asset Links File Generator" to open the following, and then click on "Generate Digital Asset Links file".  
Click "Generate Digital Asset Links file". [androidstudio-welcome](docimg/applinks-10.png)

Click on the "Save File" button to save the generated definition file "assetlinks.json" to any folder.  
Click on the ![androidstudio-welcome](docimg/applinks-11.png)

### Deploy and verify the configuration files
Create a directory named ".well-known" in the Domain root of the server you prepared, and place the definition file "assetlinks.json" there.  
It is necessary to set the Content-Type to "application/json".  

Click "Test App Links" in ④ to verify on the Emulator.  
Click the "Run Test" button when the following dialog box opens.  
![androidstudio-welcome](docimg/applinks-13.png)

If the verification on the Emulator is OK, the verification OK message will be output as shown below.
![androidstudio-welcome](docimg/applinks-14.png)

Open ".../nodejs/app.js" and rewrite "App Login Screen" as shown below.  
Put the domain of the server above in "[YOUR-SERVER-DOMAIN]".  

```js
//-------------------
//App Login Screen
//-------------------
app.get('/appLogin', async (req, res) => {
    if(req.query.client === 'androidApp') {
        res.render('appLogin.ejs', calcConfigs(`https://[YOUR-SERVER-DOMAIN]/redirector_local-${req.query.client}.html?token=${req.query. token}`));
    } else {
        res.render('appLogin.ejs', calcConfigs(`https://amazon-pay-links-v2.s3-ap-northeast-1.amazonaws.com/redirector_local-${req.query. client}.html?token=${req.query.token}`));
    }
});
```.

Now, please restart nodejs and launch the sample application on Emulator to see how it works.
