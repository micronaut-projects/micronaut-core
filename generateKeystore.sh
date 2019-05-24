#!/bin/bash

echo "Command openssl genrsa:"
openssl genrsa -des3 -out keystore.key -passout pass:foobar

echo "Command openssl req:"
openssl req -new -x509 -key keystore.key -out keystore.crt -passin pass:foobar -subj "/C=GB/ST=London/L=London/O=Global Security/OU=IT Department/CN=localhost"

echo "Command openssl pkcs12:"
openssl pkcs12 -inkey keystore.key -in keystore.crt -export -out keystore.p12 -password pass:foobar -passin pass:foobar

echo "Command keytool -importkeystore:"
keytool -importkeystore -srckeystore keystore.p12 -srcstoretype PKCS12 -srcstorepass foobar -destkeystore keystore -storepass foobar -noprompt

if [ "$1." == "." ]; then
  cp keystore.p12 ./http-client/src/test/resources/keystore.p12
  cp keystore.p12 ./http-client/build/resources/test/keystore.p12
else
  cp keystore.p12 ./src/test/resources/$1
  cp keystore.p12 ./build/resources/test/$1
fi

