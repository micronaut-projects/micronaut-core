package io.micronaut.http.netty.cookies

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.cookie.Cookie
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames
import spock.lang.Specification

class NettyCookieOrderingSpec extends Specification {

    void "compareTo uses natural order by name then path"() {
        given:
        Cookie a = new NettyCookie("a", "1")
        Cookie b = new NettyCookie("b", "1")
        Cookie p1 = new NettyCookie("SID", "x").path("/foo")
        Cookie p2 = new NettyCookie("SID", "x").path("/foo/bar")

        expect:
        a.compareTo(b) < 0
        b.compareTo(a) > 0
        p1.compareTo(p2) < 0
        new TreeSet<Cookie>([b, a]).first().name == "a"
    }

    void "compareTo accepts other Cookie implementations"() {
        given:
        Cookie other = Cookie.of("b", "1")

        expect:
        new NettyCookie("a", "1").compareTo(other) < 0
        new NettyCookie("b", "2").compareTo(other) == 0
    }

    void "equals and hashCode are consistent with compareTo"() {
        given:
        Cookie c1 = new NettyCookie("SID", "1").path("/").domain("Example.com")
        Cookie c2 = new NettyCookie("SID", "2").path("/").domain("example.com")
        Cookie c3 = new NettyCookie("SID", "1").path("/other")

        expect:
        c1.compareTo(c2) == 0
        c1 == c2
        c1.hashCode() == c2.hashCode()
        c1 != c3
        new HashSet<Cookie>([c1, c2, c3]).size() == 2
    }

    void "request cookies are read from every Cookie header"() {
        given:
        def headers = new DefaultHttpHeaders()
                .add(HttpHeaderNames.COOKIE, "a=1; b=2")
                .add(HttpHeaderNames.COOKIE, "c=3")

        when:
        def cookies = new NettyCookies("/", headers, ConversionService.SHARED)

        then:
        cookies.get("a").value == "1"
        cookies.get("b").value == "2"
        cookies.get("c")?.value == "3"
    }
}
