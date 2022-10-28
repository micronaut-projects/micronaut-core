package io.micronaut.http.client.netty

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.Cookies
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse
import io.micronaut.http.netty.stream.StreamedHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class NettyStreamedHttpResponseSpec extends Specification {

    void "test cookies"() {
        given:
        String cookieDef = "simple-cookie=avalue; max-age=60; path=/; domain=.micronaut.io"
        HttpHeaders httpHeaders = new DefaultHttpHeaders(false).add(HttpHeaderNames.SET_COOKIE, cookieDef)
        StreamedHttpResponse streamedHttpResponse = new DefaultStreamedHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, null)
        streamedHttpResponse.headers().set(httpHeaders)

        when:
        NettyStreamedHttpResponse response = new NettyStreamedHttpResponse(streamedHttpResponse, ConversionService.SHARED)

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
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "enctp=1;Domain=.xxx.xxx.com;Expires=Sat, 09-Jan-2021 17:32:47 GMT;Max-Age=7776000")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "inf=123456; path=/; domain=.xxx.com;")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "AUT=aaaabbbbcccc; path=/; domain=.xxx.com; HttpOnly")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "SES=abcdabcd; path=/; domain=.xxx.com;")
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "JKL=abcdaaaa=; path=/; domain=.xxx.com; Secure;")
        StreamedHttpResponse streamedHttpResponse = new DefaultStreamedHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, null)
        streamedHttpResponse.headers().set(httpHeaders)

        when:
        NettyStreamedHttpResponse response = new NettyStreamedHttpResponse(streamedHttpResponse, ConversionService.SHARED)

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

    void "test adding new cookies"() {
        HttpHeaders httpHeaders = new DefaultHttpHeaders(false)
        httpHeaders.add(HttpHeaderNames.SET_COOKIE, "INIT=abcdaaaa=; path=/; domain=.xxx.com; Secure;")
        StreamedHttpResponse streamedHttpResponse = new DefaultStreamedHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, null)
        streamedHttpResponse.headers().set(httpHeaders)

        when:
        NettyStreamedHttpResponse response = new NettyStreamedHttpResponse(streamedHttpResponse, ConversionService.SHARED)
        response.cookie(Cookie.of("ADDED", "xyz").httpOnly(true).domain(".foo.com"))

        then:
        Cookies cookies = response.getCookies()
        cookies != null
        cookies.size() == 2
        cookies.get("INIT").secure
        cookies.get("INIT").domain == ".xxx.com"
        cookies.get("ADDED").httpOnly
        cookies.get("ADDED").domain == ".foo.com"

        response.getHeaders().getAll("Set-Cookie") == [
                'INIT=abcdaaaa=; path=/; domain=.xxx.com; Secure;',
                'ADDED=xyz; Domain=.foo.com; HTTPOnly',
        ]
    }
}
