package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.ServerCookieDecoder
import spock.lang.Specification

class ServerCookieDecoderSpec extends Specification {

    void serverCookieDecoderResolvedViaSpi() {
        expect:
        ServerCookieDecoder.INSTANCE instanceof NettyLaxServerCookieDecoder
    }
}
