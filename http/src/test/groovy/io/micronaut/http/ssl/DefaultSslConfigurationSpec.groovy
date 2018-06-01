package io.micronaut.http.ssl

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class DefaultSslConfigurationSpec extends Specification {

    void 'test default ssl configuration'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
            'micronaut.ssl.enabled':true,
            'micronaut.ssl.key.password':'blah',
            'micronaut.ssl.key-store.path':'blahpath',
            'micronaut.ssl.trust-store.path':'blahtrust',

        )

        SslConfiguration sslConfiguration = ctx.getBean(SslConfiguration)

        expect:
        sslConfiguration instanceof DefaultSslConfiguration
        sslConfiguration.isEnabled()
        sslConfiguration.getKey().getPassword().isPresent()
        sslConfiguration.getKey().getPassword().get() == 'blah'
        sslConfiguration.getKeyStore().getPath().get() == 'blahpath'
        sslConfiguration.getTrustStore().getPath().get() == 'blahtrust'

        cleanup:
        ctx.close()
    }

    void 'test default client ssl configuration'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.ssl.enabled':true,
                'micronaut.ssl.key.password':'blah',
                'micronaut.ssl.key-store.path':'blahpath',
                'micronaut.ssl.trust-store.path':'blahtrust',
                'micronaut.http.client.ssl.trust-store.path':'clienttrust',

        )

        SslConfiguration sslConfiguration = ctx.getBean(SslConfiguration)
        ClientSslConfiguration clientSslConfiguration = ctx.getBean(ClientSslConfiguration)

        expect:
        sslConfiguration instanceof DefaultSslConfiguration
        sslConfiguration.isEnabled()
        sslConfiguration.getTrustStore().getPath().get() == 'blahtrust'
        clientSslConfiguration.isEnabled()
        clientSslConfiguration.getKey().getPassword().isPresent()
        clientSslConfiguration.getKey().getPassword().get() == 'blah'
        clientSslConfiguration.getKeyStore().getPath().get() == 'blahpath'
        clientSslConfiguration.getTrustStore().getPath().get() == 'clienttrust'

        cleanup:
        ctx.close()
    }

    void 'test default server ssl configuration'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.ssl.enabled':true,
                'micronaut.ssl.key.password':'blah',
                'micronaut.ssl.key-store.path':'blahpath',
                'micronaut.ssl.trust-store.path':'blahtrust',
                'micronaut.server.ssl.trust-store.path':'servertrust',
                'micronaut.server.ssl.ciphers': 'foo'

        )

        SslConfiguration sslConfiguration = ctx.getBean(SslConfiguration)
        ServerSslConfiguration serverCOnfig = ctx.getBean(ServerSslConfiguration)

        expect:
        sslConfiguration instanceof DefaultSslConfiguration
        sslConfiguration.isEnabled()
        sslConfiguration.getTrustStore().getPath().get() == 'blahtrust'
        serverCOnfig.isEnabled()
        serverCOnfig.getCiphers().get() == ['foo'] as String[]
        serverCOnfig.getKey().getPassword().isPresent()
        serverCOnfig.getKey().getPassword().get() == 'blah'
        serverCOnfig.getKeyStore().getPath().get() == 'blahpath'
        serverCOnfig.getTrustStore().getPath().get() == 'servertrust'

        cleanup:
        ctx.close()
    }
}
