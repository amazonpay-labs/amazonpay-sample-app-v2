# How to generate the SSL Self-certification

## Precondition
- openssl has installed.  
  Note: Those who are using Windows, perform the commans below on UNIX/Mac, or WSL/Gitbash, otherwise install it by yourself.  
- The current directory should be this directory when you perform the commands below.

## The commands for generating the certifications
```sh
# generate sample.key, sample.crt
openssl req -x509 -nodes -days 398 -newkey rsa:2048 \
  -keyout sample.key -out sample.crt \
  -subj "/C=JP/ST=Tokyo/L=Tokyo/O=Sample Inc./CN=localhost" \
  -reqexts v3_req -reqexts SAN -extensions SAN -config v3.ext

# confirm sample.crt
openssl x509 -in sample.crt -text -noout

# transform sample.crt to DER, so you get sample.der.crt (It is for installing the certification on Android Terminal.)
openssl x509 -in sample.crt -outform der -out sample.der.crt
```

## Copy the certifications generated to the directory for downloading.
```sh
cp sample.crt ../static/crt/
cp sample.der.crt ../static/crt/
```
