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
package io.micronaut.http.server.netty.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.core.async.annotation.SingleResult

/**
 * @author graemerocher
 * @since 1.0
 */
class JsonStreamSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.serverHeader': 'JsonStreamSpec'])

    void "test json stream response content type"() {
        given:
        StreamingHttpClient streamingHttpClient = embeddedServer.applicationContext.createBean(StreamingHttpClient, embeddedServer.getURL())

        HttpResponse response = Flux.from(streamingHttpClient.exchangeStream(HttpRequest.GET('/json/stream'))).blockFirst()

        expect:
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_JSON_STREAM_TYPE
        response.header("Server") == "JsonStreamSpec"
        response.header("Date")
    }

    void "test json stream response content type with a response return"() {
        given:
        StreamingHttpClient streamingHttpClient = embeddedServer.applicationContext.createBean(StreamingHttpClient, embeddedServer.getURL())

        HttpResponse response = Flux.from(streamingHttpClient.exchangeStream(HttpRequest.GET('/json/stream/custom'))).blockFirst()
        List<Book> books = Flux.from(streamingHttpClient.jsonStream(HttpRequest.GET('/json/stream/custom'), Book)).collectList().block()

        expect:
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_JSON_STREAM_TYPE
        response.header("Server") == "JsonStreamSpec"
        response.header("Date")
        response.header("X-MyHeader") == "42"
        books[0].title == "The Stand"
        books[1].title == "The Shining"
    }

    void "test json stream response content type with a response return single body"() {
        given:
        StreamingHttpClient streamingHttpClient = embeddedServer.applicationContext.createBean(StreamingHttpClient, embeddedServer.getURL())

        HttpResponse response = Flux.from(streamingHttpClient.exchangeStream(HttpRequest.GET('/json/stream/single'))).blockFirst()
        Book book = Flux.from(streamingHttpClient.jsonStream(HttpRequest.GET('/json/stream/single'), Book)).blockFirst()

        expect:
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_JSON_STREAM_TYPE
        response.header("Server") == "JsonStreamSpec"
        response.header("Date")
        response.header("X-MyHeader") == "42"
        book.title == "The Stand"
    }

    void "test an empty json stream"() {
        given:
        StreamingHttpClient client = embeddedServer.applicationContext.createBean(StreamingHttpClient, embeddedServer.getURL())

        when:
        List<Book> books = Flux.from(client.jsonStream(HttpRequest.GET("/json/stream/empty"), Book)).collectList().block()

        then:
        books == []
    }

    @Controller("/json/stream")
    static class StreamController {

        @Get(produces = MediaType.APPLICATION_JSON_STREAM)
        Flux<Book> stream() {
            return Flux.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }

        @Get(uri = "/custom", produces = MediaType.APPLICATION_JSON_STREAM)
        HttpResponse<Flux<Book>> streamResponse() {
            return HttpResponse.ok(Flux.just(new Book(title: "The Stand"), new Book(title: "The Shining"))).header("X-MyHeader", "42")
        }

        @Get(uri = "/single", produces = MediaType.APPLICATION_JSON_STREAM)
        @SingleResult
        Publisher<HttpResponse<Book>> streamSingleResponse() {
            return Mono.just(HttpResponse.ok(new Book(title: "The Stand")).header("X-MyHeader", "42"))
        }

        @Get(uri = "/empty", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> emptyStream() {
            return Flux.empty()
        }

    }

    static class Book {
        String title
    }
}
