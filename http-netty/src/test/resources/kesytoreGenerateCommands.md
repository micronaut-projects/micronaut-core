## Commands used to generate keystore for testing

**keystoreWithMultipleAlias.jks**

```sh
keytool -genkeypair -v -keystore keystoreWithMultipleAlias.jks -storetype JKS -alias alias1 -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=localhost1, OU=Micronaut1, O=My Company, L=City, ST=State, C=BR" -storepass password -keypass passwordAlias1
keytool -genkeypair -v -keystore keystoreWithMultipleAlias.jks -storetype JKS -alias alias2 -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=localhost2, OU=Micronaut2, O=My Company, L=City, ST=State, C=BR" -storepass password -keypass passwordAlias2
keytool -genkeypair -v -keystore keystoreWithMultipleAlias.jks -storetype JKS -alias alias3 -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=localhost3, OU=Micronaut3, O=My Company, L=City, ST=State, C=BR" -storepass password -keypass passwordAlias3
```
