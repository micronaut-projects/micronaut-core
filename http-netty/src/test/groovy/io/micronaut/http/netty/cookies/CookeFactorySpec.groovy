package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.CookieFactory
import spock.lang.Specification

class CookieFactorySpec extends Specification {

    void cookieFactoryResolvedViaSpi() {
        expect:
        CookieFactory.INSTANCE instanceof NettyCookieFactory
    }

    void "default cookie is a netty cookie with max age undefined"() {
        expect:
        Cookie.of("SID", "31d4d96e407aad42").getMaxAge() == Cookie.UNDEFINED_MAX_AGE
    }
}
