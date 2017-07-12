/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty

import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpVersion
import org.particleframework.core.convert.DefaultConversionService
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpMethod
import spock.lang.Specification
import static io.netty.handler.codec.http.HttpMethod.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyHttpRequestSpec extends Specification {

    void "test netty http request parameters"() {
        given:
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri)
        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, new DefaultConversionService())
        String fullURI = request.uri.toString()
        String expectedPath = fullURI.indexOf('?') > -1 ? fullURI.substring(0, fullURI.indexOf('?')) : fullURI

        expect:
        fullURI == uri
        request.path.toString() == expectedPath
        request.method == HttpMethod."$method"
        request.parameters.names == params as Set

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

        NettyHttpRequest request = new NettyHttpRequest(nettyRequest, new DefaultConversionService())
        String fullURI = request.uri.toString()
        String expectedPath = fullURI.indexOf('?') > -1 ? fullURI.substring(0, fullURI.indexOf('?')) : fullURI

        expect:
        fullURI == uri
        request.path.toString() == expectedPath
        request.method == HttpMethod."$method"
        request.cookies.names == names as Set

        where:
        method | uri        | headers                                                               | content | names
        GET    | '/foo/bar' | [(HttpHeaders.COOKIE): 'yummy_cookie=choco; tasty_cookie=strawberry'] | null    | ['yummy_cookie', 'tasty_cookie']
    }
}
