package io.micronaut.http.client.netty

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.cookie.Cookie
import spock.lang.Specification

class NettyClientHttpRequestCookieInjectionSpec extends Specification {

    void "a ';' in a cookie value does not inject a second cookie pair"() {
        given:
        def request = new NettyClientHttpRequest<Object>(HttpMethod.GET, "/")

        when:
        try {
            request.cookie(Cookie.of("currency", "EUR; role=staff"))
        } catch (IllegalArgumentException ignored) {
        }

        then:
        !request.headers.get(HttpHeaders.COOKIE)?.contains("role=staff")
    }

    void "a ';' in one of several cookies does not inject a cookie pair"() {
        given:
        def request = new NettyClientHttpRequest<Object>(HttpMethod.GET, "/")

        when:
        try {
            request.cookies([Cookie.of("a", "1"), Cookie.of("currency", "EUR;role=staff")] as Set)
        } catch (IllegalArgumentException ignored) {
        }

        then:
        !request.headers.get(HttpHeaders.COOKIE)?.contains("role=staff")
    }
}
