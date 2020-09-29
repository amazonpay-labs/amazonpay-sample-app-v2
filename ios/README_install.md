## インストール方法
先にWebアプリケーション側にあたる、[nodejs](../nodejs/README.md)側をインストールして下さい。

### プロジェクトのOpenとサンプルアプリの起動
本プロジェクトは、Mac上の[Xcode](https://developer.apple.com/jp/xcode/)で開きます。そのほかの環境での開き方は、ここでは扱いません。  
※ ここではversion 10.2.1を使用しています。  
まずはXcodeを立ち上げます。  
![androidstudio-welcome](docimg/xcode_open.png)
「Open another project」で、こちらのios/iOS-App-v2ディレクトリを選択して、「Open」  
プロジェクトが開いたら、Menuの「Product」→「Run」か、画面上部の「Run」ボタンより、applicationを起動してください。
![androidstudio-project](docimg/xcode_project.png)
Simulatorが立ち上がり、サンプルアプリが起動します。(1〜2分かかります。)  
<img src="docimg/simu_start.png" width="300">

### 自己証明書のインストール
今回のサンプルでは、server側のSSL証明書に自己証明書が使用されているため、サンプルアプリを正しく動作させるためにはその自己証明書をiOS側にInstallする必要があります。  
ここでは、起動したSimulatorへのInstall方法を説明します。
※ 以下はiOS12.2で実施しておりますが、iOSのバージョンによっては手順が若干違う場合があります。

1. SSL自己証明書のDownload  
Safariを立ち上げ、下記のURLにアクセスします。(Chrome等の他のブラウザだとうまくいかないことがあるので、必ずSafariをご使用ください。)  
https://localhost:3443/static/crt/sample.crt  
下記のように警告が出るので、「Show Details」  
<img src="docimg/simu_warn.png" width="300">  
「visit this website」のリンクをタップし、表示されたダイアログで再度「Visit Website」をタップ  
<img src="docimg/simu_warn-detail.png" width="300">  
「Allow」をタップし、で開いたダイアログで「Close」をタップ  
<img src="docimg/simu_allow-download.png" width="300">  

2. SSL自己証明書のInstall  
Safariを閉じて、「Settings」 →　「General」 → 「Profile」  
今ダウンロードされた「localhost」をタップ  
<img src="docimg/simu_profile.png" width="300">  
「Install」をタップし、開いたダイアログで再度「Install」をタップ  
<img src="docimg/simu_install-profile.png" width="300">  
Installが完了します。  
<img src="docimg/simu_success.png" width="300">  

3. SSL自己証明書の有効化  
「Settings」 →　「General」 → 「About」で下記を開いて、「Certificate Trust Settings」  
<img src="docimg/simu_about.png" width="300">  
先ほどInstallした「localhost」をONにし、表示されたダイアログで「Continue」をタップして有効化します。  
<img src="docimg/simu_trust.png" width="300">  

あとはSimulator上でサンプルアプリを立ち上げて動作をご確認ください。
