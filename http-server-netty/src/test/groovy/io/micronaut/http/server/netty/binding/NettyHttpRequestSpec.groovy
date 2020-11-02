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
package io.micronaut.http.server.netty.binding

import io.micronaut.http.MutableHttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpVersion
import io.micronaut.core.convert.DefaultConversionService
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import spock.lang.Specification
import static io.netty.handler.codec.http.HttpMethod.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyHttpRequestSpec extends Specification {

    void "test mutate request"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, GET, "/foo/bar")
        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), new DefaultConversionService(), new HttpServerConfiguration())

        when:
        def altered = request.mutate()
                            .uri({ builder -> builder.replacePath("/another")})
                            .header("foo", 'bar')

        then:
        altered.path == '/another'
        altered.headers.get("foo") == 'bar'
    }

    void "test mutating a mutable request"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, GET, "/foo/bar")
        def request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), new DefaultConversionService(), new HttpServerConfiguration())

        when:
        request = request.mutate()

        then:
        request instanceof MutableHttpRequest

        when: //mutate called on the result of .mutate()
        request.mutate()

        then:
        noExceptionThrown()
    }

    void "test netty http request parameters"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri)
        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), new DefaultConversionService(), new HttpServerConfiguration())
        String fullURI = request.uri.toString()
        String expectedPath = fullURI.indexOf('?') > -1 ? fullURI.substring(0, fullURI.indexOf('?')) : fullURI

        expect:
        fullURI == uri
        request.path.toString() == expectedPath
        request.method == HttpMethod."$method"
        request.parameters.names() == params as Set

        where:
        method | uri               | headers | content | params
        GET    | '/foo/bar'        | [:]     | null    | []
        GET    | '/foo/bar?q=test' | [:]     | null    | ['q']
    }

    void "test netty http cookies"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri)
        for (header in headers) {
            nettyRequest.headers().add(header.key.toString(), header.value)
        }

        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), new DefaultConversionService(), new HttpServerConfiguration())
        String fullURI = request.uri.toString()
        String expectedPath = fullURI.indexOf('?') > -1 ? fullURI.substring(0, fullURI.indexOf('?')) : fullURI

        expect:
        fullURI == uri
        request.path.toString() == expectedPath
        request.method == HttpMethod."$method"
        request.cookies.names() == names as Set

        where:
        method | uri        | headers                                                               | content | names
        GET    | '/foo/bar' | [(HttpHeaders.COOKIE): 'yummy_cookie=choco; tasty_cookie=strawberry'] | null    | ['yummy_cookie', 'tasty_cookie']
    }

    void "test netty http locale with accept-language"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri)
        for (header in headers) {
            nettyRequest.headers().add(header.key.toString(), header.value)
        }

        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), new DefaultConversionService(), new HttpServerConfiguration())
        String fullURI = request.uri.toString()
        String expectedPath = fullURI.indexOf('?') > -1 ? fullURI.substring(0, fullURI.indexOf('?')) : fullURI

        expect:
        fullURI == uri
        request.path.toString() == expectedPath
        request.method == HttpMethod."$method"
        request.locale.get().toString() == locale

        where:
        method | uri        | headers                                                                         | content | locale
        GET    | '/foo/bar' | [(HttpHeaders.ACCEPT_LANGUAGE): 'de-CH']                                        | null    | 'de_CH'
        GET    | '/foo/bar' | [(HttpHeaders.ACCEPT_LANGUAGE): 'en-US,en;q=0.5']                               | null    | 'en_US'
        GET    | '/foo/bar' | [(HttpHeaders.ACCEPT_LANGUAGE): 'fr-CH, fr;q=0.9, en;q=0.8, de;q=0.7, *;q=0.5'] | null    | 'fr_CH'
        GET    | '/foo/bar' | [(HttpHeaders.ACCEPT_LANGUAGE): '*']                                            | null    | Locale.default.toString()
    }

    void "test accept-charset parsing"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri)
        for (header in headers) {
            nettyRequest.headers().add(header.key.toString(), header.value)
        }

        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, Mock(ChannelHandlerContext), new DefaultConversionService(), new HttpServerConfiguration())
        String fullURI = request.uri.toString()
        String expectedPath = fullURI.indexOf('?') > -1 ? fullURI.substring(0, fullURI.indexOf('?')) : fullURI

        expect:
        fullURI == uri
        request.path.toString() == expectedPath
        request.method == HttpMethod."$method"
        request.characterEncoding.name() == charset

        where:
        method | uri        | headers                                                                         | content | charset
        GET    | '/foo/bar' | [(HttpHeaders.ACCEPT_CHARSET): 'utf-8, iso-8859-1;q=0.5']                       | null    | 'UTF-8'
        GET    | '/foo/bar' | [(HttpHeaders.ACCEPT_CHARSET): 'utf-16, iso-8859-1;q=0.5']                       | null    | 'UTF-16'
    }
}
