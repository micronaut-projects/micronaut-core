package io.micronaut.http.client.netty

import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.Cookies
import io.netty.handler.codec.http.*
import spock.lang.Specification

class FullNettyClientHttpResponseSpec extends Specification {

    void "test cookies"() {
        given:
          String cookieDef = "simple-cookie=avalue; max-age=60; path=/; domain=.micronaut.io"
          HttpHeaders httpHeaders = new DefaultHttpHeaders(false).add(HttpHeaderNames.SET_COOKIE, cookieDef)
          FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
          fullHttpResponse.headers().set(httpHeaders)

        when:
          FullNettyClientHttpResponse response = new FullNettyClientHttpResponse(fullHttpResponse, HttpStatus.OK, null, null, null, false)

        then:
            Cookies cookies = response.getCookies()
            cookies != null
            cookies.size() == 1
            cookies.contains("simple-cookie")
            Optional<Cookie> oCookie = response.getCookie("simple-cookie")
            oCookie.isPresent()
            oCookie.get().getValue().equals("avalue")
    }

    void "test multiple cookie headers"() {
        HttpHeaders httpHeaders = new DefaultHttpHeaders(false)
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "enctp=1;Domain=.xxx.xxx.com;Expires=Sat, 09-Jan-2021 17:32:47 GMT;Max-Age=7776000")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "inf=123456; path=/; domain=.xxx.com;")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "AUT=aaaabbbbcccc; path=/; domain=.xxx.com; HttpOnly")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "SES=abcdabcd; path=/; domain=.xxx.com;")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "JKL=abcdaaaa=; path=/; domain=.xxx.com; Secure;")
        fullHttpResponse.headers().set(httpHeaders)

        when:
        FullNettyClientHttpResponse response = new FullNettyClientHttpResponse(fullHttpResponse, HttpStatus.OK, null, null, null, false)

        then:
        Cookies cookies = response.getCookies()
        cookies != null
        cookies.size() == 5
        cookies.get("enctp").maxAge == 7776000
        cookies.get("enctp").value == "1"
        cookies.get("inf").path == "/"
        cookies.get("inf").domain == ".xxx.com"
        cookies.get("AUT").httpOnly
        cookies.get("SES").value == "abcdabcd"
        cookies.get("SES").path == "/"
        cookies.get("JKL").secure
        cookies.get("JKL").domain == ".xxx.com"
    }

}

