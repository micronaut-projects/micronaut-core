package io.micronaut.http.netty

import io.micronaut.http.ssl.SslConfiguration
import spock.lang.Specification

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509KeyManager
import java.security.KeyStore
import java.security.cert.Certificate

class NettyTlsUtilsSpec extends Specification {
    def "storeToFactory should return a KeyManagerFactory with only the selected alias"() {
        given:

        SslConfiguration sslConfig = Mock(SslConfiguration);
        SslConfiguration.KeyConfiguration keyConfiguration = Mock(SslConfiguration.KeyConfiguration);
        SslConfiguration.KeyStoreConfiguration keyStoreConfiguration = Mock(SslConfiguration.KeyStoreConfiguration);
        sslConfig.isPreferOpenssl() >> false
        sslConfig.getKey() >> keyConfiguration
        sslConfig.getKeyStore() >> keyStoreConfiguration
        keyConfiguration.getAlias() >> Optional.of("alias2")
        keyConfiguration.getPassword() >> Optional.of("passwordAlias2")

        String keystorePath = "src/test/resources/keystoreWithMultipleAlias.jks";
        char[] keystorePassword = "password".toCharArray();

        KeyStore rootKeyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            rootKeyStore.load(fis, keystorePassword);
        }

        when:
        KeyManagerFactory resultKeyStore = NettyTlsUtils.storeToFactory(sslConfig, rootKeyStore)

        then:
        resultKeyStore.getKeyManagers().size() == 1
        def manager = (X509KeyManager) resultKeyStore.getKeyManagers().first()
        def certificate = manager.getCertificateChain("alias2")
        certificate[0].getSubjectX500Principal().toString() == "CN=localhost2, OU=Micronaut2, O=My Company, L=City, ST=State, C=BR";
    }

    def "storeToFactory should throw a exception if selected alias not exists"() {
        given:

        SslConfiguration sslConfig = Mock(SslConfiguration);
        SslConfiguration.KeyConfiguration keyConfiguration = Mock(SslConfiguration.KeyConfiguration);
        SslConfiguration.KeyStoreConfiguration keyStoreConfiguration = Mock(SslConfiguration.KeyStoreConfiguration);
        sslConfig.isPreferOpenssl() >> false
        sslConfig.getKey() >> keyConfiguration
        sslConfig.getKeyStore() >> keyStoreConfiguration
        keyConfiguration.getAlias() >> Optional.of("alias5")
        keyConfiguration.getPassword() >> Optional.of("passwordAlias2")
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null, null)
        keystore.containsAlias("any") >> false;

        when:
        NettyTlsUtils.storeToFactory(sslConfig, keystore)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Alias alias5 not found in keystore"
    }

    def "storeToFactory should throw a exception if key of alias is null"() {
        given:

        SslConfiguration sslConfig = Mock(SslConfiguration);
        SslConfiguration.KeyConfiguration keyConfiguration = Mock(SslConfiguration.KeyConfiguration);
        SslConfiguration.KeyStoreConfiguration keyStoreConfiguration = Mock(SslConfiguration.KeyStoreConfiguration);
        sslConfig.isPreferOpenssl() >> false
        sslConfig.getKey() >> keyConfiguration
        sslConfig.getKeyStore() >> keyStoreConfiguration
        keyConfiguration.getAlias() >> Optional.of("any")
        keyConfiguration.getPassword() >> Optional.of("any")

        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null, null)
        keystore.containsAlias("any") >> false;
        keystore.setCertificateEntry("any", Mock(Certificate))

        when:
        NettyTlsUtils.storeToFactory(sslConfig, keystore)

        then:
        def e = thrown(IllegalStateException)
        e.message == "There are no keys associated with the alias any"
    }

    def "storeToFactory should not extract alias if Keystore is null"() {
        given:

        SslConfiguration sslConfig = Mock(SslConfiguration);
        SslConfiguration.KeyConfiguration keyConfiguration = Mock(SslConfiguration.KeyConfiguration);
        SslConfiguration.KeyStoreConfiguration keyStoreConfiguration = Mock(SslConfiguration.KeyStoreConfiguration);
        sslConfig.isPreferOpenssl() >> false
        sslConfig.getKey() >> keyConfiguration
        sslConfig.getKeyStore() >> keyStoreConfiguration
        keyConfiguration.getAlias() >> Optional.of("any")
        keyConfiguration.getPassword() >> Optional.of("any")

        when:
        NettyTlsUtils.storeToFactory(sslConfig, null)

        then:
        0 * NettyTlsUtils.extractKeystoreAlias(_, _, _)
    }

    def "storeToFactory should not extract alias if alias is not defined"() {
        given:

        SslConfiguration sslConfig = Mock(SslConfiguration);
        SslConfiguration.KeyConfiguration keyConfiguration = Mock(SslConfiguration.KeyConfiguration);
        SslConfiguration.KeyStoreConfiguration keyStoreConfiguration = Mock(SslConfiguration.KeyStoreConfiguration);
        sslConfig.isPreferOpenssl() >> false
        sslConfig.getKey() >> keyConfiguration
        sslConfig.getKeyStore() >> keyStoreConfiguration
        keyConfiguration.getAlias() >> Optional.empty()
        keyConfiguration.getPassword() >> Optional.empty()
        keyStoreConfiguration.getPassword() >> Optional.of("any")

        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null, null)

        when:
        NettyTlsUtils.storeToFactory(sslConfig, keystore)

        then:
        0 * NettyTlsUtils.extractKeystoreAlias(_, _, _)
    }
}
