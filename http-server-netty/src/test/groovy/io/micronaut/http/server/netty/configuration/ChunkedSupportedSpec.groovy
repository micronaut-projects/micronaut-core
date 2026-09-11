/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.server.netty.configuration

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ChunkedSupportedSpec extends Specification {

    private static String sendChunkedRequest(EmbeddedServer server) {
        new Socket(server.host, server.port).withCloseable { Socket socket ->
            socket.soTimeout = 10_000
            socket.outputStream.write(("POST /chunked-supported HTTP/1.1\r\n" +
                    "Host: ${server.host}:${server.port}\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "5\r\nhello\r\n" +
                    "0\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            return new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
        }
    }

    void "chunked request bodies are accepted by default"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ChunkedSupportedSpec'
        ])

        when:
        String response = sendChunkedRequest(server)

        then:
        response.startsWith('HTTP/1.1 200 OK')
        response.contains('echo:hello')

        cleanup:
        server.stop()
    }

    void "chunked request bodies are rejected when chunked support is disabled"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                               : 'ChunkedSupportedSpec',
                'micronaut.server.netty.chunked-supported': false
        ])

        when:
        String response = sendChunkedRequest(server)

        then:
        response.startsWith('HTTP/1.1 400 Bad Request')
        !response.contains('echo:hello')

        cleanup:
        server.stop()
    }

    @Requires(property = "spec.name", value = "ChunkedSupportedSpec")
    @Controller('/chunked-supported')
    static class TestController {
        @Post(consumes = "text/plain", produces = "text/plain")
        String echo(@Body String body) {
            return "echo:" + body
        }
    }
}
