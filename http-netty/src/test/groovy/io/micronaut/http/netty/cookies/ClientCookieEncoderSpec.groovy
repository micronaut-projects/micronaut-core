package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.ClientCookieEncoder
import spock.lang.Specification

class ClientCookieEncoderSpec extends Specification {

    void "ClientCookieEncoder is resolved via Spi"() {
        expect:
        ClientCookieEncoder.INSTANCE instanceof NettyLaxClientCookieEncoder
    }
}
