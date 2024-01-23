package io.micronaut.http.netty.cookies

import io.micronaut.http.cookie.CookieFactory
import spock.lang.Specification

class CookeFactorySpec extends Specification {

    void cookieFactoryResolvedViaSpi() {
        expect:
        CookieFactory.INSTANCE instanceof NettyCookieFactory
    }
}
