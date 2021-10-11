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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

@Retry
class HostHeaderSpec extends Specification {

    @Shared
    String host = Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST)

    // Unix-like environments (e.g. Travis) may not allow to bind on reserved ports without proper privileges.
    @IgnoreIf({ os.linux })
    @Requires({ SocketUtils.isTcpPortAvailable(80) })
    void "test host header with server on 80"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder(['spec.name': 'HostHeaderSpec', 'micronaut.server.port': 80]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost"

        cleanup:
        embeddedServer.close()
        asyncClient.close()
    }

    void "test host header with server on random port"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HostHeaderSpec', ])
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "${host}:${embeddedServer.getURI().getPort()}"

        cleanup:
        embeddedServer.close()
        asyncClient.close()
    }

    // Unix-like environments (e.g. Travis) may not allow to bind on reserved ports without proper privileges.
    @IgnoreIf({ os.linux })
    @Requires({ SocketUtils.isTcpPortAvailable(80) })
    void "test host header with client authority"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder(['spec.name': 'HostHeaderSpec', 'micronaut.server.port': 80]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(new URL("http://foo@localhost"))
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost"

        cleanup:
        embeddedServer.close()
        asyncClient.close()
    }

    // Unix-like environments (e.g. Travis) may not allow to bind on reserved ports without proper privileges.
    @IgnoreIf({ os.linux })
    @Requires({ SocketUtils.isTcpPortAvailable(443) })
    void "test host header with https server on 443"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder([
                'spec.name': 'HostHeaderSpec',
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': 443
        ]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost"

        cleanup:
        embeddedServer.close()
        asyncClient.close()
    }

    void "test host header with https server on custom port"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder([
                'spec.name': 'HostHeaderSpec',
                'micronaut.ssl.port': -1,
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.buildSelfSigned': true
        ]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "${host}:${embeddedServer.getURI().getPort()}"

        cleanup:
        embeddedServer.close()
        asyncClient.close()
    }

    @io.micronaut.context.annotation.Requires(property = 'spec.name', value = 'HostHeaderSpec')
    @Controller("/echo-host")
    static class EchoHostController {

        @Get(produces = MediaType.TEXT_PLAIN)
        String simple(@Header String host) {
            host
        }
    }
}
