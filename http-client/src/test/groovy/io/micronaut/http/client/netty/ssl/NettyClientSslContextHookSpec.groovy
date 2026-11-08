package io.micronaut.http.client.netty.ssl

import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpVersion
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.ssl.SslConfiguration
import io.micronaut.http.ssl.SslConfigurationException
import io.netty.handler.ssl.SslContext
import spock.lang.Specification

import javax.net.ssl.SSLException

class NettyClientSslContextHookSpec extends Specification {
    def 'legacy final build dispatches to context hook and preserves exception translation'() {
        given:
        def ssl = new SslConfiguration()
        ssl.enabled = true
        def supplied = Mock(SslContext)
        boolean fail = false
        def builder = new NettyClientSslBuilder(new ResourceResolver()) {
            @Override
            protected SslContext createSslContext(SslConfiguration configuration, HttpVersionSelection version) throws SSLException {
                assert configuration.is(ssl)
                if (fail) { throw new SSLException('fixture') }
                supplied
            }
        }

        expect:
        builder.build(ssl, HttpVersion.HTTP_1_1).get().is(supplied)

        when:
        fail = true
        builder.build(ssl, HttpVersion.HTTP_2_0)

        then:
        def e = thrown(SslConfigurationException)
        e.cause instanceof SSLException
        e.cause.message == 'fixture'
    }
}
