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
package io.micronaut.docs.server.consumes

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConsumesControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'consumesspec'
    ], Environment.TEST)

    @AutoCleanup
    @Shared
    HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    def "@Consumes allow you to control which media type is accepted"() {
        given:
        Book book = new Book(title: "The Stand", pages: 1000)

        when:
        rxClient.toBlocking().exchange(HttpRequest.POST("/consumes", book)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then:
        thrown(HttpClientResponseException)

        when:
        rxClient.toBlocking().exchange(HttpRequest.POST("/consumes", book)
                .contentType(MediaType.APPLICATION_JSON))

        then:
        noExceptionThrown()

        when:
        rxClient.toBlocking().exchange(HttpRequest.POST("/consumes/multiple", book)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then:
        noExceptionThrown()

        when:
        rxClient.toBlocking().exchange(HttpRequest.POST("/consumes/multiple", book)
                .contentType(MediaType.APPLICATION_JSON))

        then:
        noExceptionThrown()

        when:
        rxClient.toBlocking().exchange(HttpRequest.POST("/consumes/member", book)
                        .contentType(MediaType.TEXT_PLAIN))

        then:
        noExceptionThrown()
    }

    @Introspected
    static class Book {
        String title
        Integer pages
    }
}
