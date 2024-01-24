package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.ClientCookieEncoder
import io.micronaut.http.cookie.Cookie

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
}
