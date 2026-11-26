package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.ssl.SslConfigurationException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class KeyExchangeGroupsSpec extends Specification {

    private EmbeddedServer start(Map<String, Object> extra) {
        ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                         : 'KeyExchangeGroupsSpec',
                'micronaut.server.ssl.enabled'                      : true,
                'micronaut.server.ssl.port'                         : -1,
                'micronaut.server.ssl.build-self-signed'            : true,
                'micronaut.server.ssl.protocols'                    : ['TLSv1.3'],
                'micronaut.server.ssl.key-exchange-groups'          : ['secp384r1'],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.ssl.protocols'               : ['TLSv1.3'],
        ] + extra)
    }

    void "the JDK client applies the configured key exchange groups"() {
        given:
        EmbeddedServer server = start(['micronaut.http.client.ssl.key-exchange-groups': ['secp384r1', 'x25519']])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        def result = client.toBlocking().retrieve('/key-exchange')

        then:
        result == 'ok'

        cleanup:
        client?.close()
        server?.close()
    }

    void "the JDK client fails the handshake with disjoint groups"() {
        given:
        EmbeddedServer server = start(['micronaut.http.client.ssl.key-exchange-groups': ['x25519']])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        client.toBlocking().retrieve('/key-exchange')

        then:
        thrown(Exception)

        cleanup:
        client?.close()
        server?.close()
    }

    void "the JDK client rejects unsupported groups"() {
        given:
        EmbeddedServer server = start([:])

        when:
        server.applicationContext.createBean(HttpClient, server.URL, new DefaultHttpClientConfiguration().tap {
            sslConfiguration.keyExchangeGroups = ['no-such-group'] as String[]
        })

        then:
        def e = thrown(Exception)
        def cause = e
        while (cause != null && !(cause instanceof SslConfigurationException)) {
            cause = cause.cause
        }
        cause?.message == 'The JDK TLS implementation does not support the configured key exchange groups [no-such-group]'

        cleanup:
        server?.close()
    }

    void "the JDK client ignores the groups when its SSL is disabled"() {
        given:
        EmbeddedServer server = start([:])

        when:
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL, new DefaultHttpClientConfiguration().tap {
            sslConfiguration.enabled = false
            sslConfiguration.keyExchangeGroups = ['no-such-group'] as String[]
        })

        then:
        noExceptionThrown()

        cleanup:
        client?.close()
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'KeyExchangeGroupsSpec')
    @Controller('/key-exchange')
    static class KeyExchangeController {
        @Get
        String index() {
            'ok'
        }
    }
}
