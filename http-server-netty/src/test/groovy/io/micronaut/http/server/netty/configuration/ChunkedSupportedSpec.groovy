/*
 * Copyright 2017-2026 original authors
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

    void "a request with a malformed chunk is rejected and the connection is closed"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ChunkedSupportedSpec'
        ])

        when: 'a valid chunk is followed by a chunk with an invalid size'
        String response = new Socket(server.host, server.port).withCloseable { Socket socket ->
            socket.soTimeout = 10_000
            socket.outputStream.write(("POST /chunked-supported HTTP/1.1\r\n" +
                    "Host: ${server.host}:${server.port}\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "\r\n" +
                    "5\r\nhello\r\n" +
                    "xyz\r\n").getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            // the connection must be closed by the server, so this does not time out
            def bytes = new ByteArrayOutputStream()
            byte[] buf = new byte[1024]
            try {
                int n
                while ((n = socket.inputStream.read(buf)) != -1) {
                    bytes.write(buf, 0, n)
                }
            } catch (SocketException ignored) {
                // a reset after the response is fine
            }
            return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
        }

        then:
        response.startsWith('HTTP/1.1 400 Bad Request')
        response.toLowerCase(Locale.ROOT).contains('connection: close')
        !response.contains('echo:')

        cleanup:
        server.stop()
    }

    void "a request with a malformed chunk is rejected: #description"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ChunkedSupportedSpec'
        ])

        when:
        byte[] head = ("POST /chunked-supported HTTP/1.1\r\n" +
                "Host: ${server.host}:${server.port}\r\n" +
                "Content-Type: text/plain\r\n" +
                (gzip ? "Content-Encoding: gzip\r\n" : "") +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n").getBytes(StandardCharsets.US_ASCII)
        byte[] payload = gzip ? gzip('hello') : 'hello'.getBytes(StandardCharsets.US_ASCII)
        def request = new ByteArrayOutputStream()
        request.write(head)
        request.write("${Integer.toHexString(payload.length)}${extension}\r\n".getBytes(StandardCharsets.US_ASCII))
        request.write(payload)
        request.write(tail.getBytes(StandardCharsets.US_ASCII))
        String response = exchange(server, request.toByteArray())

        then:
        response.startsWith(status)
        response.toLowerCase(Locale.ROOT).contains('connection: close')
        !response.contains('echo:')

        cleanup:
        server.stop()

        where:
        description                         | extension             | tail                                   | gzip  | status
        'chunk extension over the limit'    | ';x=' + ('a' * 5000)  | '\r\n5\r\nworld\r\n0\r\n\r\n' | false | 'HTTP/1.1 413'
        'invalid chunk size after a chunk'  | ''                    | '\r\nxyz\r\n'                       | false | 'HTTP/1.1 400'
        'invalid trailer'                   | ''                    | '\r\n0\r\n' + ('a' * 9000) + '\r\n\r\n' | false | 'HTTP/1.1 4'
        'gzip body, invalid chunk size'     | ''                    | '\r\nxyz\r\n'                       | true  | 'HTTP/1.1 400'
    }

    private static byte[] gzip(String text) {
        def bytes = new ByteArrayOutputStream()
        new java.util.zip.GZIPOutputStream(bytes).withCloseable { it.write(text.getBytes(StandardCharsets.US_ASCII)) }
        return bytes.toByteArray()
    }

    /**
     * Send the raw request and read until the server closes the connection.
     */
    private static String exchange(EmbeddedServer server, byte[] request) {
        new Socket(server.host, server.port).withCloseable { Socket socket ->
            socket.soTimeout = 10_000
            socket.outputStream.write(request)
            socket.outputStream.flush()
            def bytes = new ByteArrayOutputStream()
            byte[] buf = new byte[1024]
            try {
                int n
                while ((n = socket.inputStream.read(buf)) != -1) {
                    bytes.write(buf, 0, n)
                }
            } catch (SocketException ignored) {
                // a reset after the response is fine
            }
            return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
        }
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
