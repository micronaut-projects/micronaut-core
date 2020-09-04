package io.micronaut.http.client.netty

import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.Cookies
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse
import io.micronaut.http.netty.stream.StreamedHttpResponse
import io.netty.handler.codec.http.*
import spock.lang.Specification

class NettyStreamedHttpResponseSpec extends Specification {


    void "test cookies"() {
        given:
        String cookieDef = "simple-cookie=avalue; max-age=60; path=/; domain=.micronaut.io";
        HttpHeaders httpHeaders = new DefaultHttpHeaders(false).add(HttpHeaderNames.SET_COOKIE, cookieDef);
        StreamedHttpResponse streamedHttpResponse = new DefaultStreamedHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, null);
        streamedHttpResponse.headers().set(httpHeaders);

        when:
        NettyStreamedHttpResponse response = new NettyStreamedHttpResponse(streamedHttpResponse, HttpStatus.OK);

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
