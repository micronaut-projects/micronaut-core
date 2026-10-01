package io.micronaut.http.client.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpVersion
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpVersionSelection
import io.netty.handler.ssl.OpenSslContext
import io.netty.handler.ssl.ReferenceCountedOpenSslContext
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslProvider
import io.netty.util.ReferenceCountUtil
import spock.lang.Requires
import spock.lang.Specification

class NettyClientSslBuilderProviderSpec extends Specification {

    @Requires({ SslProvider.isAlpnSupported(SslProvider.OPENSSL_REFCNT) })
    def 'ALPN client context uses the reference-counted OpenSSL provider'() {
        given:
        def ctx = ApplicationContext.run()
        def builder = ctx.getBean(NettyClientSslBuilder)
        def ssl = ctx.getBean(DefaultHttpClientConfiguration).sslConfiguration

        when:
        SslContext sslContext = builder.build(ssl, HttpVersionSelection.forLegacyVersion(HttpVersion.HTTP_2_0))

        then:
        sslContext instanceof ReferenceCountedOpenSslContext
        !(sslContext instanceof OpenSslContext)
        sslContext.applicationProtocolNegotiator().protocols().contains(HttpVersionSelection.ALPN_HTTP_2)

        cleanup:
        ReferenceCountUtil.release(sslContext)
        ctx.close()
    }
}
