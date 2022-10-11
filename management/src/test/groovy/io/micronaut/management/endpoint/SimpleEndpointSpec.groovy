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
package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.util.Toggleable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.management.endpoint.annotation.Selector
import io.micronaut.management.endpoint.annotation.Write
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleEndpointSpec extends Specification {

    void "test read simple endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.myValue':'foo'], Environment.TEST)
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        def response = rxClient.toBlocking().exchange("/simple", String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'test foo'

        cleanup:
        rxClient.close()
        server.close()
    }

    void "test read simple endpoint with HEAD"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.myValue':'foo'], Environment.TEST)
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        def response = rxClient.toBlocking().exchange(HttpRequest.HEAD("/simple"), String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == null

        cleanup:
        rxClient.close()
        server.close()
    }

    void "test read simple endpoint with argument"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.myValue':'foo']
        )
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())


        when:
        def response = rxClient.toBlocking().exchange("/simple/baz", String)


        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'test baz'

        cleanup:
        rxClient.close()
        server.close()
    }

    void "test write simple endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.myValue':'foo']
        )
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        def response = rxClient.toBlocking().exchange(HttpRequest.POST("/simple", "bar").contentType("text/plain"), String)

        then:
        response.code() == HttpStatus.OK.code

        when:
        response = rxClient.toBlocking().exchange("/simple", String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'test bar'

        cleanup:
        rxClient.close()
        server.close()
    }

    void "test disable endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.enabled':false]
        )
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        rxClient.toBlocking().exchange("/simple", String)

        then:
        HttpClientResponseException ex = thrown()
        ex.response.code() == HttpStatus.NOT_FOUND.code

        cleanup:
        rxClient.close()
        server.close()
    }
}

@Endpoint(id = 'simple', defaultSensitive = false)
class Simple implements Toggleable {

    private final ApplicationContext applicationContext

    Simple(ApplicationContext applicationContext) {
        assert  applicationContext != null
        this.applicationContext = applicationContext
    }
    boolean enabled = true
    String myValue

    @Read
    String value() {
        "test $myValue"
    }

    @Read
    String named(@Selector String name) {
        "test $name"
    }

    @Write(consumes = 'text/plain')
    void value(@Body String val) {
        this.myValue = val
    }
}
