/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.binding

import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll

import static io.netty.handler.codec.http.HttpMethod.GET

/**
 * Pins the query parameter decoding behaviour of {@link NettyHttpRequest}.
 */
class NettyHttpRequestQueryParametersSpec extends Specification {

    private NettyHttpRequest request(String uri, HttpServerConfiguration config = new HttpServerConfiguration()) {
        new NettyHttpRequest(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, GET, uri), NettyByteBodyFactory.empty(),
                Mock(ChannelHandlerContext), new DefaultMutableConversionService(), config)
    }

    private static Map<String, List<String>> params(NettyHttpRequest request) {
        Map<String, List<String>> m = new LinkedHashMap<>()
        for (String name : request.parameters.names()) {
            m.put(name, request.parameters.getAll(name))
        }
        m
    }

    @Unroll
    void "query parameters for #uri (escapeHtmlUrl=#escape)"() {
        given:
        def config = new HttpServerConfiguration()
        config.escapeHtmlUrl = escape

        expect:
        params(request(uri, config)) == expected

        where:
        uri                              | escape | expected
        '/p'                             | false  | [:]
        '/p?'                            | false  | [:]
        '/p?a=1&b=2'                     | false  | [a: ['1'], b: ['2']]
        '/p?a=1&a=2&a='                  | false  | [a: ['1', '2', '']]
        '/p?a&b=&=c'                     | false  | [a: [''], b: [''], c: ['']]
        '/p?a=x+y&b=%2B%26%3D'           | false  | [a: ['x y'], b: ['+&=']]
        '/p?%61%62=%E2%82%AC'            | false  | [ab: ['€']]
        '/p?a=1;b=2'                     | false  | [a: ['1'], b: ['2']]
        '/p?a=1&&b=2&'                   | false  | [a: ['1'], b: ['2']]
        '/p?a=/x?y:z@'                   | false  | [a: ['/x?y:z@']]
        '/p%20q?a=1'                     | false  | [a: ['1']]
        '/p?a=1#frag'                    | false  | [a: ['1']]
        'http://example.com/p?a=1&b=x+y' | false  | [a: ['1'], b: ['x y']]
        'http://example.com/p?a=%26'     | false  | [a: ['']]
        '/p?a=%zz'                       | true   | [a: ['%zz']]
        '/p?a=|'                         | true   | [a: ['|']]
        '/p?a=%'                         | true   | [a: ['%']]
        '/p?a=%2'                        | true   | [a: ['%2']]
        '/p?a=%41%zz'                    | true   | [a: ['A%zz']]
    }

    @Unroll
    void "invalid request target #uri is rejected"() {
        when:
        request(uri).parameters

        then:
        thrown(IllegalArgumentException)

        where:
        uri << ['/p?a=%zz', '/p?a=%', '/p?a=%2', '/p?a=|', '/p%g1']
    }

    void "semicolon as normal char"() {
        given:
        def config = new HttpServerConfiguration()
        config.semicolonIsNormalChar = true

        expect:
        params(request('/p?a=1;b=2', config)) == [a: ['1;b=2']]
    }

    void "max params"() {
        given:
        def config = new HttpServerConfiguration()
        config.maxParams = 2

        expect:
        params(request('/p?a=1&b=2&c=3&d=4', config)) == [a: ['1'], b: ['2']]
        params(request('/p?a=%31&b=2&c=3&d=4', config)) == [a: ['1'], b: ['2']]
    }
}
