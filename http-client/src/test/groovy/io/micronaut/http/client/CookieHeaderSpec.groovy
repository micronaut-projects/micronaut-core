package io.micronaut.http.client

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.cookie.Cookie
import spock.lang.Specification

class CookieHeaderSpec extends Specification {

    void "test multiple cookies are contained in a single header"() {
        when:
        HttpRequest request = HttpRequest.GET("/")
        request.cookie(Cookie.of("a", "1"))
        request.cookie(Cookie.of("b", "2"))
        request.cookie(Cookie.of("a", "3"))

        then:
        request.headers.get(HttpHeaders.COOKIE) == "a=3; b=2"
    }
}
