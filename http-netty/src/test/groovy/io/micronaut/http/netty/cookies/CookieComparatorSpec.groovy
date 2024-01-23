package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.Cookie
import org.junit.jupiter.api.Test

class CookieComparatorSpec {

    @Test
    void cookieComparator() {
        given:
        Cookie cookie1 = new NettyCookie("SID", "31d4d96e407aad42").path("/foo")
        Cookie cookie2 = new NettyCookie("SID", "31d4d96e407aad42").path("/foo/bar")
        expect:
        cookie1 < cookie2
    }
}
