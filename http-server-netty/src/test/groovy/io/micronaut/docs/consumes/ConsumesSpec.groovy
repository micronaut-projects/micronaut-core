/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.docs.consumes

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConsumesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'consumesspec'
    ], "test")

    @AutoCleanup
    @Shared
    RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "@Consumes allow you to control which media type is accepted"() {
        given:
        def book = new Book(title: "The Stand", pages: 1000)

        when:
        HttpRequest request = HttpRequest.POST("/test", book)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        thrown(HttpClientResponseException)

        when:
        request = HttpRequest.POST("/test", book)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        noExceptionThrown()

        when:
        request = HttpRequest.POST("/test/multiple-consumes", book)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        noExceptionThrown()

        when:
        request = HttpRequest.POST("/test/multiple-consumes", book)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        noExceptionThrown()
    }

    static class Book {
        String title
        Integer pages
    }
}
