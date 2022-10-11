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
import io.micronaut.context.annotation.Property
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

@Retry(mode = Retry.Mode.SETUP_FEATURE_CLEANUP)
class SslStaticCertSpec extends Specification {

    @Shared
    String host = Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST)

    ApplicationContext context
    EmbeddedServer embeddedServer
    HttpClient client

    void setup() {
        context = ApplicationContext.run([
                'spec.name': 'SslStaticCertSpec',
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.keyStore.path': 'classpath:keystore.p12',
                'micronaut.ssl.keyStore.password': 'foobar',
                'micronaut.ssl.keyStore.type': 'PKCS12',
                'micronaut.ssl.protocols': ['TLSv1.2'],
                'micronaut.server.ssl.port': -1,
                'micronaut.ssl.ciphers': ['TLS_RSA_WITH_AES_128_CBC_SHA',
                                          'TLS_RSA_WITH_AES_256_CBC_SHA',
                                          'TLS_RSA_WITH_AES_128_GCM_SHA256',
                                          'TLS_RSA_WITH_AES_256_GCM_SHA384',
                                          'TLS_DHE_RSA_WITH_AES_128_GCM_SHA256',
                                          'TLS_DHE_RSA_WITH_AES_256_GCM_SHA384',
                                          'TLS_DHE_DSS_WITH_AES_128_GCM_SHA256',
                                          'TLS_DHE_DSS_WITH_AES_256_GCM_SHA384'],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true
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
        embeddedServer.getURL().toString() == "https://${host}:${embeddedServer.port}"
    }

    void "test send https request"() {
        when:
        Flux<HttpResponse<String>> reactiveSequence = Flux.from(client.exchange(
                HttpRequest.GET("/ssl/static"), String
        ))
        HttpResponse<String> response = reactiveSequence.blockFirst()

        then:
        response.body() == "Hello"
    }

    @Requires(property = 'spec.name', value = 'SslStaticCertSpec')
    @Controller('/')
    static class SslStaticController {

        @Get('/ssl/static')
        String simple() {
            return "Hello"
        }

    }
}
