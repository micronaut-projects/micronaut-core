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

}
