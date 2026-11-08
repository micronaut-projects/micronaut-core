package io.micronaut.http.server.netty.ssl

import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpVersion
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.ssl.ServerSslConfiguration
import io.micronaut.http.ssl.SslConfiguration
import io.micronaut.http.ssl.SslConfigurationException
import io.netty.handler.ssl.SslContext
import spock.lang.Specification

import javax.net.ssl.SSLException

class ServerSslContextHookSpec extends Specification {
    def 'legacy final builds dispatch to context hook and preserve exception translation'() {
        given:
        def ssl = new ServerSslConfiguration()
        def supplied = Mock(SslContext)
        boolean fail = false
        def builder = new AbstractServerSslBuilder(new ResourceResolver(), new HttpServerConfiguration()) {
            @Override
            ServerSslConfiguration getSslConfiguration() { ssl }

            @Override
            protected SslContext createSslContext(SslConfiguration configuration, HttpVersion version) throws SSLException {
                assert configuration.is(ssl)
                if (fail) { throw new SSLException('fixture') }
                supplied
            }
        }

        expect:
        builder.build().get().is(supplied)
        builder.build(ssl, HttpVersion.HTTP_2_0).get().is(supplied)

        when:
        fail = true
        builder.build()

        then:
        def e = thrown(SslConfigurationException)
        e.cause instanceof SSLException
        e.cause.message == 'fixture'
    }
}
