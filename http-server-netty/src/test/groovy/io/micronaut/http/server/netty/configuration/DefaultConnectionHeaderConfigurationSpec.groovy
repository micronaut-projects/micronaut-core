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
package io.micronaut.http.server.netty.configuration

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class DefaultConnectionHeaderConfigurationSpec extends Specification {

    void "connection header is not set by default"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': DefaultConnectionHeaderConfigurationSpec.simpleName])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        client.exchange(
          HttpRequest.GET('/connection/fail')
        ).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.header(HttpHeaders.CONNECTION) == "close"

        cleanup:
        embeddedServer.stop()
        client.stop()
    }

    void "connection header set to keep-alive when keepAlive configured to true"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.default.connection.header.keepAlive':true
        ])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        client.exchange(
          HttpRequest.GET('/connection/fail')
        ).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.header(HttpHeaders.CONNECTION) == "keep-alive"

        cleanup:
        embeddedServer.stop()
        client.stop()
    }

    void "connection header is not set by when keepAlive configured to false"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
          'micronaut.default.connection.header.keepAlive':false
        ])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        client.exchange(
          HttpRequest.GET('/connection/fail')
        ).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.header(HttpHeaders.CONNECTION) == "close"

        cleanup:
        embeddedServer.stop()
        client.stop()
    }

    void "response contains keep alive header when its passed as connection header in the request"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': DefaultConnectionHeaderConfigurationSpec.simpleName])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse response = client.exchange(
          HttpRequest.GET("/connection/ok").header(HttpHeaders.CONNECTION, "keep-alive")
        ).blockingFirst()

        then:
        response.header(HttpHeaders.CONNECTION) == "keep-alive"

        cleanup:
        embeddedServer.close()
    }

    void "response contains keep alive header when response is ok"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': DefaultConnectionHeaderConfigurationSpec.simpleName])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse response = client.exchange(
          HttpRequest.GET("/connection/ok")
        ).blockingFirst()

        then:
        response.header(HttpHeaders.CONNECTION) == "keep-alive"

        cleanup:
        embeddedServer.close()
    }

    void "response contains connection-close header when its passed as connection header in the request"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': DefaultConnectionHeaderConfigurationSpec.simpleName])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse response = client.exchange(
          HttpRequest.GET("/connection/ok").header(HttpHeaders.CONNECTION, "close")
        ).blockingFirst()

        then:
        response.header(HttpHeaders.CONNECTION) == "close"

        cleanup:
        embeddedServer.close()
    }

    @Controller('/connection')
    static class TestController {
        @Get("/fail")
        HttpStatus index() {
            HttpStatus.INTERNAL_SERVER_ERROR
        }

        @Get("/ok")
        HttpStatus keepAlive() {
            HttpStatus.OK
        }

    }

}
