package io.micronaut.http.netty.cookies

import io.micronaut.core.order.OrderUtil
import io.micronaut.http.cookie.ClientCookieEncoder
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.DefaultClientCookieEncoder
import spock.lang.Specification

class NettyLaxClientCookieEncoderSpec extends Specification {

    void "netty client cookie encoding"() {
        given:
        ClientCookieEncoder cookieEncoder = new NettyLaxClientCookieEncoder()

        when:
        Cookie cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").domain("example.com")

        then:
        "SID=31d4d96e407aad42" == cookieEncoder.encode(cookie)
    }

    void "ClientCookieEncoder is NettyLaxClientCookieDecoder"() {
        expect:
        ClientCookieEncoder.INSTANCE instanceof NettyLaxClientCookieEncoder
    }

    void "NettyLaxServerCookieDecoder is loaded before Default"() {
        when:
        List<ClientCookieEncoder> l = [new NettyLaxClientCookieEncoder(), new DefaultClientCookieEncoder()]

        then:
        sortAndGetFirst(l) instanceof NettyLaxClientCookieEncoder

        when:
        l = [new DefaultClientCookieEncoder(), new NettyLaxClientCookieEncoder()]

        then:
        sortAndGetFirst(l) instanceof NettyLaxClientCookieEncoder
    }

    private static ClientCookieEncoder sortAndGetFirst(List<ClientCookieEncoder> l) {
        l.stream()
                .min(OrderUtil.COMPARATOR)
                .orElse(null)
    }
}
