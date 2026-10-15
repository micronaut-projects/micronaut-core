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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ReusedRequestHeadersSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ReusedRequestHeadersSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient)

    void "client-added headers are not written into a reused request"() {
        given:
        int port = server.port
        MutableHttpRequest<?> request = HttpRequest.GET("http://localhost:${port}/reused-request/host")
        Set<String> before = new TreeSet<>(request.headers.names())

        when:
        String first = client.toBlocking().retrieve(request)

        then:
        first == "localhost:${port}"
        new TreeSet<>(request.headers.names()) == before

        when:
        request.uri(URI.create("http://127.0.0.1:${port}/reused-request/host"))
        String second = client.toBlocking().retrieve(request)

        then:
        second == "127.0.0.1:${port}"
        new TreeSet<>(request.headers.names()) == before
    }

    void "content length of a reused request follows its current body"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.POST("http://localhost:${server.port}/reused-request/length", "a")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        Set<String> before = new TreeSet<>(request.headers.names())

        when:
        String first = client.toBlocking().retrieve(request)

        then:
        first == "1:a"
        new TreeSet<>(request.headers.names()) == before

        when:
        request.body("abcd")
        String second = client.toBlocking().retrieve(request)

        then:
        second == "4:abcd"
        new TreeSet<>(request.headers.names()) == before
    }

    @Requires(property = 'spec.name', value = 'ReusedRequestHeadersSpec')
    @Controller('/reused-request')
    static class EchoController {

        @Get(value = '/host', produces = MediaType.TEXT_PLAIN)
        String host(@Header(HttpHeaders.HOST) String host) {
            host
        }

        @Post(value = '/length', consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String length(@Header(HttpHeaders.CONTENT_LENGTH) String length, @Body String body) {
            "$length:$body"
        }
    }
}
