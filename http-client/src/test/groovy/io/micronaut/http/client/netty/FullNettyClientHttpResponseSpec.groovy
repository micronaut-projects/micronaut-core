package io.micronaut.http.client.netty

import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.Cookies
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class FullNettyClientHttpResponseSpec extends Specification {

    void "test cookies"() {
        given:
          String cookieDef = "simple-cookie=avalue; max-age=60; path=/; domain=.micronaut.io";
          HttpHeaders httpHeaders = new DefaultHttpHeaders(false).add(HttpHeaderNames.SET_COOKIE, cookieDef);
          FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
          fullHttpResponse.headers().set(httpHeaders);

        when:
          FullNettyClientHttpResponse response = new FullNettyClientHttpResponse(fullHttpResponse, HttpStatus.OK, null, null, null, false);

        then:
            Cookies cookies = response.getCookies();
            cookies != null;
            cookies.size() == 4;
            cookies.contains("simple-cookie");
            Optional<Cookie> oCookie = response.getCookie("simple-cookie");
            oCookie.isPresent();
            oCookie.get().getValue().equals("avalue");

    }

}

