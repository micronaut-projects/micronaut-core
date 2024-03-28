package io.micronaut.http.netty.cookies

import io.micronaut.core.order.OrderUtil
import io.micronaut.http.cookie.DefaultServerCookieDecoder
import io.micronaut.http.cookie.ServerCookieDecoder
import spock.lang.Specification

class NettyLaxServerCookieDecoderSpec extends Specification {

    void "ServerCookieDecoder is NettyLaxServerCookieDecoder"() {
        expect:
        ServerCookieDecoder.INSTANCE instanceof NettyLaxServerCookieDecoder
    }

    void "NettyLaxServerCookieDecoder is loaded before Default"() {
        when:
        List<ServerCookieDecoder> l = [new NettyLaxServerCookieDecoder(), new DefaultServerCookieDecoder()]

        then:
        sortAndGetFirst(l) instanceof NettyLaxServerCookieDecoder

        when:
        l = [new DefaultServerCookieDecoder(), new NettyLaxServerCookieDecoder()]

        then:
        sortAndGetFirst(l) instanceof NettyLaxServerCookieDecoder
    }

    private static ServerCookieDecoder sortAndGetFirst(List<ServerCookieDecoder> l) {
        l.stream()
                .min(OrderUtil.COMPARATOR)
                .orElse(null)
    }
}
