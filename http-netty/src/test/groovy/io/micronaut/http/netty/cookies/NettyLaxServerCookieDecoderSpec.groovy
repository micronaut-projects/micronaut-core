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
}
