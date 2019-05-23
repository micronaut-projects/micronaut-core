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

import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SslStaticCertSpec extends Specification {

    @Shared
    String host = Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST)

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'micronaut.ssl.enabled': true,
            'micronaut.ssl.keyStore.path': 'classpath:keystore.p12',
            'micronaut.ssl.keyStore.password': 'foobar',
            'micronaut.ssl.keyStore.type': 'PKCS12',
            'micronaut.ssl.protocols': ['TLSv1.2'],
            'micronaut.ssl.ciphers': ['TLS_RSA_WITH_AES_128_CBC_SHA',
                                      'TLS_RSA_WITH_AES_256_CBC_SHA',
                                      'TLS_RSA_WITH_AES_128_GCM_SHA256',
                                      'TLS_RSA_WITH_AES_256_GCM_SHA384',
                                      'TLS_DHE_RSA_WITH_AES_128_GCM_SHA256',
                                      'TLS_DHE_RSA_WITH_AES_256_GCM_SHA384',
                                      'TLS_DHE_DSS_WITH_AES_128_GCM_SHA256',
                                      'TLS_DHE_DSS_WITH_AES_256_GCM_SHA384'],
    ])

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    void "expect the url to be https"() {
        expect:
        embeddedServer.getURL().toString() == "https://${host}:8443"
    }

    void "test send https request"() {
        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/ssl/static"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.body() == "Hello"

        cleanup:
        client.stop()
        context.stop()
    }

    @Controller('/')
    static class SslStaticController {

        @Get('/ssl/static')
        String simple() {
            return "Hello"
        }

    }
}
