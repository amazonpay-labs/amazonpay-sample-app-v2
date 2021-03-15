## How to install
Install the [nodejs](.../nodejs/README.md) side of the web application first.

## Open the project and launch the sample application
This project will be opened in [Xcode](https://developer.apple.com/jp/xcode/) on a Mac. We will not discuss how to open it in other environments here.  
Here, we are using version 10.2.1.  
First, launch Xcode.  
![androidstudio-welcome](docimg/xcode_open.png)
Under "Open another project", select this iOS/iOS-App-v2 directory and click "Open".  
After the project is opened, start the application by clicking "Product" -> "Run" in the menu or the "Run" button at the top of the screen.
![androidstudio-project](docimg/xcode_project.png)
The Simulator will start up and the sample application will be launched. (This will take a minute or two.)  
<img src="docimg/simu_start.png" width="300">

### Install self-certificate
In this sample, a self-certificate is used as the SSL certificate on the server side, so it is necessary to install the self-certificate on the iOS side in order for the sample app to work properly.  
This section explains how to install the self-certificate on the launched Simulator.
The following steps are performed on iOS 12.2, but the procedure may differ slightly depending on the version of iOS. 

1. Download the SSL self-certificate  
Launch Safari, and access the following URL. (Be sure to use Safari, as it may not work with other browsers such as Chrome.)  
https://localhost:3443/static/crt/sample.crt  
You will get a warning as below, click "Show Details"  
<img src="docimg/simu_warn.png" width="300">  
Tap the "visit this website" link, and tap "Visit Website" again in the dialog that appears.  
<img src="docimg/simu_warn-detail.png" width="300">  
Tap "Allow" and then tap "Close" in the dialog that opens.  
<img src="docimg/simu_allow-download.png" width="300">  

2. Install the SSL self-certificate  
Close Safari and go to Settings > General > Profile.  
Tap on "localhost" that was just downloaded.  
<img src="docimg/simu_profile.png" width="300">  
Tap "Install", and then tap "Install" again in the dialog that opens.  
<img src="docimg/simu_install-profile.png" width="300">  
Install will be completed.  
<img src="docimg/simu_success.png" width="300">

3. Activate the SSL self-certificate.  
Go to Settings > General > About, open the following and select Certificate Trust Settings  
<img src="docimg/simu_about.png" width="300">  
Turn on "localhost" that you just installed, and tap "Continue" on the dialog that appears to enable it.  
<img src="docimg/simu_trust.png" width="300">  

Now, launch the sample application on the Simulator and see how it works.
