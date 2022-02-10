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
package io.micronaut.http.client.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientStreamSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared BookClient bookClient = embeddedServer.applicationContext.getBean(BookClient)

    void "test stream array of json objects"() {
        when:
        List<Book> books = Flux.from(bookClient.arrayStream()).collectList().block()

        then:
        books.size() == 2
        books[0].title == "The Stand"
        books[1].title == "The Shining"
    }

    void "test stream json stream of objects"() {
        when:
        List<Book> books = Flux.from(bookClient.jsonStream()).collectList().block()

        then:
        books.size() == 2
        books[0].title == "The Stand"
        books[1].title == "The Shining"
    }

    void "test a stream that produces an error with error type specified"() {
        when:
        Flux.from(bookClient.errorStream()).blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get().get("error") == "from server"
    }

    void "test a stream that produces an error without an error type"() {
        def client = embeddedServer.applicationContext.getBean(BookClientNoErrorType)

        when:
        Flux.from(client.errorStream()).blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        !ex.response.getBody(Map).isPresent()
    }

    void "test a stream that returns an error response"() {
        when:
        Flux.from(bookClient.errorStream2()).blockFirst()

        then:
        thrown(HttpClientResponseException)
    }

    @Client(value = '/rxjava/stream', errorType = Map)
    static interface BookClient extends BookApi {

        @Get("/error")
        Publisher<Book> errorStream()

        @Get("/error2")
        Publisher<Book> errorStream2()
    }

    @Client(value = '/rxjava/stream')
    static interface BookClientNoErrorType extends BookApi {

        @Get("/error")
        Publisher<Book> errorStream()
    }

    @Controller("/rxjava/stream")
    static class StreamController implements BookApi {

        @Override
        Publisher<Book> arrayStream() {
            return Flux.just(
                    new Book(title: "The Stand"),
                    new Book(title: "The Shining"),
            )
        }

        @Override
        Publisher<Book> jsonStream() {
            return Flux.just(
                    new Book(title: "The Stand"),
                    new Book(title: "The Shining"),
            )
        }

        @Get("/error")
        HttpResponse<Map> errorStream() {
            return HttpResponse.serverError([error: "from server"])
        }

        @Get("/error2")
        HttpResponse<Map> errorStream2() {
            return HttpResponse.serverError()
                    .contentLength(0)
        }
    }

    static interface BookApi {
        @Get("/array")
        Publisher<Book> arrayStream()

        @Get(value = "/json", processes = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> jsonStream()
    }

    static class Book {
        String title
    }
}
