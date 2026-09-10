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
package io.micronaut.http.server.netty.redirect

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

@Retry
class HttpToHttpsRedirectSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.port'                  : -1,
            'micronaut.server.dual-protocol'         : true,
            'micronaut.server.http-to-https-redirect': true,
            'micronaut.ssl.enabled'                  : true,
            'micronaut.server.ssl.port'              : -1,
            'micronaut.server.ssl.build-self-signed' : true,
            'micronaut.http.client.follow-redirects' : false
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer
            .applicationContext
            .createBean(HttpClient, new URL("http://localhost:${(embeddedServer.boundPorts - embeddedServer.port).first()}"))

    void 'test http to https redirect when enabled'() {
        when:
        HttpResponse response = httpClient.toBlocking().exchange('/hello')

        then:
        response.status == HttpStatus.PERMANENT_REDIRECT
        response.header(HttpHeaders.LOCATION).startsWith("https://localhost")
        response.header(HttpHeaders.CONNECTION) == 'close'
    }

    void 'test http to https redirect retains the query string'() {
        when:
        HttpResponse response = httpClient.toBlocking().exchange(HttpRequest.GET('/hello?foo=bar&baz=a%20b'))

        then:
        response.status == HttpStatus.PERMANENT_REDIRECT
        def location = URI.create(response.header(HttpHeaders.LOCATION))
        location.scheme == 'https'
        location.rawPath == '/hello'
        location.rawQuery == 'foo=bar&baz=a%20b'
    }

    void 'test http to https redirect retains an empty query string'() {
        when: 'the request target ends in "?", which is a query component that happens to be empty'
        String location = redirectLocationOf('/hello?')

        then: 'the delimiter is kept, because an empty query and an absent query are distinct URI forms'
        location.startsWith('https://localhost')
        location.endsWith('/hello?')
    }

    void 'test http to https redirect reproduces the query string verbatim'() {
        when: 'the query uses reserved and percent encoded characters'
        String location = redirectLocationOf('/hello?a=1%2F2&b=x:y@z&c=p,q$r&d=%7Bjson%7D')

        then: 'the Location header carries the original bytes without a decode or re-encode round trip'
        location.startsWith('https://localhost')
        location.endsWith('/hello?a=1%2F2&b=x:y@z&c=p,q$r&d=%7Bjson%7D')
    }

    /**
     * Sends a request line verbatim so the request target is not normalised by a client, and returns
     * the value of the Location header of the response.
     */
    private String redirectLocationOf(String requestTarget) {
        int port = (embeddedServer.boundPorts - embeddedServer.port).first() as int
        new Socket('localhost', port).withCloseable { Socket socket ->
            socket.soTimeout = 10_000
            socket.outputStream.with {
                write("GET ${requestTarget} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes('ISO-8859-1'))
                flush()
            }
            String response = new String(socket.inputStream.readAllBytes(), 'ISO-8859-1')
            assert response.startsWith('HTTP/1.1 308 Permanent Redirect')
            return response.readLines()
                    .find { it.toLowerCase(Locale.ROOT).startsWith('location:') }
                    .substring('location:'.length())
                    .trim()
        }
    }
}
