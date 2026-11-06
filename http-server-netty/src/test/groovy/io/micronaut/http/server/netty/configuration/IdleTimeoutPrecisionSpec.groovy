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
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class IdleTimeoutPrecisionSpec extends Specification {

    void "a sub-second idle timeout closes the connection"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'IdleTimeoutPrecisionSpec',
                'micronaut.server.idle-timeout': '500ms'
        ])

        when:
        String response = new Socket(server.host, server.port).withCloseable { Socket socket ->
            socket.soTimeout = 5_000
            socket.outputStream.write(("GET /idle-timeout HTTP/1.1\r\n" +
                    "Host: ${server.host}:${server.port}\r\n" +
                    "\r\n").getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            // the keep-alive connection is left idle, so the server is expected to close
            // it and we read to EOF rather than hitting the socket read timeout
            return new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
        }

        then:
        response.startsWith('HTTP/1.1 200 OK')
        response.endsWith('idle')

        cleanup:
        server.stop()
    }

    @Requires(property = "spec.name", value = "IdleTimeoutPrecisionSpec")
    @Controller('/idle-timeout')
    static class TestController {
        @Get(produces = "text/plain")
        String index() {
            return "idle"
        }
    }
}
