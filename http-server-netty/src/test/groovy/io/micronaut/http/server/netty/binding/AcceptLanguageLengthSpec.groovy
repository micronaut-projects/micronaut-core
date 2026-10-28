package io.micronaut.http.server.netty.binding

import io.micronaut.core.convert.DefaultConversionService
import io.micronaut.http.HttpHeaders
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class AcceptLanguageLengthSpec extends Specification {

    void "an over-long accept-language tag resolves to the default locale"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/foo')
        nettyRequest.headers().add(HttpHeaders.ACCEPT_LANGUAGE, header)
        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), new DefaultConversionService(), new HttpServerConfiguration())

        expect:
        request.locale.get() == locale

        where:
        header                                                                            | locale
        'en-US-' + (1..100).collect { String.format('v%04d', it) }.join('-') + ';q=0.9' | Locale.default
        'de-CH-1996;q=0.9, en'                                                            | Locale.forLanguageTag('de-CH-1996')
    }
}
