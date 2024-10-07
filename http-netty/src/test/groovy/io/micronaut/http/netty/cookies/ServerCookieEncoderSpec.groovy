package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.ServerCookieEncoder
import spock.lang.Specification

class ServerCookieEncoderSpec extends Specification {

    void "ServerCookieEncoder is resolved via Spi"() {
        expect:
        ServerCookieEncoder.INSTANCE instanceof NettyServerCookieEncoder
    }
}
