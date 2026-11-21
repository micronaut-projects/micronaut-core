package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.ClientCookieEncoder
import io.micronaut.http.cookie.Cookie
import spock.lang.Specification

class NettyLaxClientCookieEncoderInjectionSpec extends Specification {

    void "a ';' in a cookie value does not inject a second cookie pair"() {
        given:
        String encoded
        try {
            encoded = ClientCookieEncoder.INSTANCE.encode(Cookie.of("currency", "EUR; role=staff"))
        } catch (IllegalArgumentException ignored) {
            encoded = null
        }

        expect:
        ClientCookieEncoder.INSTANCE instanceof NettyLaxClientCookieEncoder
        encoded == null || !encoded.contains("; role=staff")
    }
}
