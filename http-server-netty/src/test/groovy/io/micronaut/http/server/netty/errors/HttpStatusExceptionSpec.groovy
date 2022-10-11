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
package io.micronaut.http.server.netty.errors

import groovy.json.JsonSlurper
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux

/**
 * @author Iván López
 * @since 1.0
 */
class HttpStatusExceptionSpec extends AbstractMicronautSpec {

    void 'test HttpStatusException'() {
        when:
        HttpResponse response = Flux.from(rxClient
            .exchange(HttpRequest.GET('/errors')))
                .onErrorResume( t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()

        then:
        response.code() == HttpStatus.UNPROCESSABLE_ENTITY.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json._embedded.errors[0].message == 'The error message'
        json._links.self.href == '/errors'
    }

    void 'test returning an arbitrary POGO'() {
        when:
        HttpResponse<String> response = Flux.from(rxClient
            .exchange((HttpRequest.GET('/errors/book')), String)).blockFirst()

        then:
        response.code() == HttpStatus.ACCEPTED.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json.title == 'The title'
    }

    @Controller('/errors')
    static class BookController {
        @Get
        String serverError() {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, 'The error message')
        }

        @Get('/book')
        String book() {
            throw new HttpStatusException(HttpStatus.ACCEPTED, new Book(title: 'The title'))
        }
    }

    static class Book {
        String title
    }
}
