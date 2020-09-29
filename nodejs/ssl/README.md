# SSL自己証明書の生成方法

## 前提条件
- opensslがインストールされていること (されていない場合には、各環境に合わせてインストール)  
  Note: Windowsの方はUNIXやMacで実施するか、WSLやgitbashをご使用されるか、下記を参考にインストールすることをご検討ください.  
  https://www.atmarkit.co.jp/ait/articles/1601/29/news043.html
- 本ディレクトリでコマンドを実行すること

## 生成コマンド
```sh
# sample.key, sample.crtの生成
openssl req -x509 -nodes -days 398 -newkey rsa:2048 \
  -keyout sample.key -out sample.crt \
  -subj "/C=JP/ST=Tokyo/L=Tokyo/O=Sample Inc./CN=localhost" \
  -reqexts v3_req -reqexts SAN -extensions SAN -config v3.ext

# sample.crtの確認
openssl x509 -in sample.crt -text -noout

# sample.crtをDER形式へ変換してsample.der.crtを生成(Android端末へのインストール用)
openssl x509 -in sample.crt -outform der -out sample.der.crt
```

## 生成した証明書ファイルをDownload用ディレクトリへコピー
```sh
cp sample.crt ../static/crt/
cp sample.der.crt ../static/crt/
```
