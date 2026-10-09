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

    void 'test http to https redirect answers a request target that is not a valid URI'() {
        when: 'the query holds characters that java.net.URI rejects'
        String response = rawResponseOf(requestTarget, 'localhost')

        then: 'the request is answered and the connection is closed rather than left hanging'
        response.startsWith('HTTP/1.1 400 Bad Request')
        response.toLowerCase(Locale.ROOT).contains('connection: close')

        where:
        requestTarget << ['/hello?filter={"a":1}', '/hello?a=b|c']
    }

    void 'test http to https redirect answers a host that is not a valid URI authority'() {
        when:
        String response = rawResponseOf('/hello', 'local{host')

        then:
        response.startsWith('HTTP/1.1 400 Bad Request')
        response.toLowerCase(Locale.ROOT).contains('connection: close')
    }

    /**
     * Sends a request line verbatim, without a {@code Connection: close} header so that the server
     * has to close the connection by itself, and returns the full response.
     */
    private String rawResponseOf(String requestTarget, String host) {
        int port = (embeddedServer.boundPorts - embeddedServer.port).first() as int
        new Socket('localhost', port).withCloseable { Socket socket ->
            socket.soTimeout = 10_000
            socket.outputStream.with {
                write("GET ${requestTarget} HTTP/1.1\r\nHost: ${host}\r\n\r\n".getBytes('ISO-8859-1'))
                flush()
            }
            // InputStream.readAllBytes is not available on Java 8
            def bytes = new ByteArrayOutputStream()
            byte[] buf = new byte[1024]
            int n
            while ((n = socket.inputStream.read(buf)) != -1) {
                bytes.write(buf, 0, n)
            }
            return new String(bytes.toByteArray(), 'ISO-8859-1')
        }
    }
}
