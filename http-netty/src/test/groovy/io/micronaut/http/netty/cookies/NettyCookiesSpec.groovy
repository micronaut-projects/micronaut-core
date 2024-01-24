package io.micronaut.http.netty.cookies

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.cookie.Cookie
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import spock.lang.Specification

class NettyCookiesSpec extends Specification {


    void "test ctor creates cookies from headers and ConversionSpecification"() {
        given:
        String cookieDef = "simple-cookie=avalue; max-age=60; path=/; domain=.micronaut.io";
        HttpHeaders httpHeaders = new DefaultHttpHeaders(false).add(HttpHeaderNames.SET_COOKIE, cookieDef);

        when:
            NettyCookies nettyCookies = new NettyCookies(httpHeaders, ConversionService.SHARED);

        then:
          Cookie cookie = nettyCookies.get("simple-cookie");
          cookie != null;
          cookie.getValue().equals("avalue");
    }

    void "test using an invalid cookie"() {
        given:
        String cookieDef = "cookie_name=cookie value; expires=Mon, 17-May-2021 09:32:59 GMT; path=/; secure; HttpOnly; samesite=none"
        HttpHeaders httpHeaders = new DefaultHttpHeaders(false).add(HttpHeaderNames.SET_COOKIE, cookieDef)

        when:
        NettyCookies nettyCookies = new NettyCookies(httpHeaders, ConversionService.SHARED)

        then:
        noExceptionThrown()
        Cookie cookie = nettyCookies.get("cookie_name")
        cookie == null
    }

}
