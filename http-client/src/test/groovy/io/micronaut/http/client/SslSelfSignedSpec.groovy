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
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

class SslSelfSignedSpec extends Specification {
    @Shared
    String host = Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST)

    ApplicationContext context
    EmbeddedServer embeddedServer
    HttpClient client

    void setup() {
        context = ApplicationContext.run([
                'spec.name': 'SslSelfSignedSpec',
                'micronaut.ssl.enabled': true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.ssl.port': -1,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        embeddedServer = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, embeddedServer.getURL())
    }

    void cleanup() {
        client.close()
        context.close()
    }

    void "expect the url to be https"() {
        expect:
        embeddedServer.getURL().toString().startsWith("https://${host}:")
    }

    void "test send https request"() {
        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.GET("/ssl"), String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.body() == "Hello"
    }

    @Requires(property = 'spec.name', value = 'SslSelfSignedSpec')
    @Controller('/')
    static class SslSelfSignedController {

        @Get('/ssl')
        String simple() {
            return "Hello"
        }

    }
}
