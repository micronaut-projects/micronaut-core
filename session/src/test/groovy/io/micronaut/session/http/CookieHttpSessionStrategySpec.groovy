/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.session.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.cookie.CookieEncoder
import io.netty.handler.codec.http.cookie.ServerCookieEncoder
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.netty.cookies.NettyCookie
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.session.Session
import spock.lang.Specification

import java.util.regex.Pattern

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class CookieHttpSessionStrategySpec extends Specification {

    void "test resolve default cookie config"() {
        given:
        CookieHttpSessionStrategy strategy = new CookieHttpSessionStrategy(new HttpSessionConfiguration())
        def nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/test')
        CookieEncoder encoder = ServerCookieEncoder.STRICT
        def encoded = encoder.encode(((NettyCookie) Cookie.of(HttpSessionConfiguration.DEFAULT_COOKIENAME, new String(Base64.encoder.encode("1234".bytes)))).getNettyCookie())
        nettyRequest.headers().add(HttpHeaders.COOKIE, encoded)
        HttpRequest request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), ConversionService.SHARED, new HttpServerConfiguration())

        expect:
        strategy.resolveIds(request) == ['1234']

    }

    void "test encode default cookie config"() {
        given:
        def configuration = new HttpSessionConfiguration()
        if (domain) configuration.domainName = domain
        if (path) configuration.cookiePath = path
        if (prefix) configuration.prefix = prefix
        configuration.cookieSecure = configSecure
        CookieHttpSessionStrategy strategy = new CookieHttpSessionStrategy(configuration)

        def request = Mock(HttpRequest)
        request.isSecure() >> secure

        def response = HttpResponse.ok()
        def session = Mock(Session)
        session.getId() >> id
        session.isExpired() >> expired

        strategy.encodeId(request, response, session)
        def header = response.headers.get(HttpHeaders.SET_COOKIE)

        expect:
        expected instanceof Pattern ? expected.matcher(header).find() : header == expected


        where:
        id     | prefix | path   | domain        | encoded             | expired | secure | configSecure | expected
        "1234" | null   | null   | null          | encode(id)          | false   | false  | true         | "SESSION=$encoded; Path=/; Secure; HTTPOnly"
        "1234" | null   | null   | null          | encode(id)          | false   | false  | false        | "SESSION=$encoded; Path=/; HTTPOnly"
        "1234" | "foo-" | null   | null          | encode(prefix + id) | false   | false  | false        | "SESSION=$encoded; Path=/; HTTPOnly"
        "1234" | null   | "/foo" | null          | encode(id)          | false   | false  | false        | "SESSION=$encoded; Path=/foo; HTTPOnly"
        "1234" | null   | null   | "example.com" | encode(id)          | false   | false  | false        | "SESSION=$encoded; Path=/; Domain=example.com; HTTPOnly"
        "1234" | null   | null   | null          | encode(id)          | true    | false  | false        | ~/SESSION=; Max-Age=0; Expires=.*; Path=\/; HTTPOnly/
        "1234" | null   | null   | null          | encode(id)          | false   | true   | false        | "SESSION=$encoded; Path=/; HTTPOnly"
        "1234" | null   | null   | null          | encode(id)          | false   | true   | true         | "SESSION=$encoded; Path=/; Secure; HTTPOnly"
        "1234" | null   | null   | null          | encode(id)          | false   | true   | null         | "SESSION=$encoded; Path=/; Secure; HTTPOnly"

    }

    protected String encode(String id) {
        new String(Base64.encoder.encode(id.bytes))
    }
}
