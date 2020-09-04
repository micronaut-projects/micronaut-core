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

import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.annotation.Produces
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class JsonStreamSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.serverHeader': 'JsonStreamSpec'])

    void "test json stream response content type"() {
        given:
        RxStreamingHttpClient streamingHttpClient = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        HttpResponse response = streamingHttpClient.exchangeStream(HttpRequest.GET('/json/stream')).blockingFirst()

        expect:
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_JSON_STREAM_TYPE
        response.header("Server") == "JsonStreamSpec"
        response.header("Date")
    }

    void "test json stream response content type with a response return"() {
        given:
        RxStreamingHttpClient streamingHttpClient = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        HttpResponse response = streamingHttpClient.exchangeStream(HttpRequest.GET('/json/stream/custom')).blockingFirst()
        List<Book> books = streamingHttpClient.jsonStream(HttpRequest.GET('/json/stream/custom'), Book).toList().blockingGet()

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
        RxStreamingHttpClient streamingHttpClient = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        HttpResponse response = streamingHttpClient.exchangeStream(HttpRequest.GET('/json/stream/single')).blockingFirst()
        Book book = streamingHttpClient.jsonStream(HttpRequest.GET('/json/stream/single'), Book).blockingFirst()

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
        RxStreamingHttpClient client = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        def books = client.jsonStream(HttpRequest.GET("/json/stream/empty"), Book)

        then:
        books.blockingIterable().iterator().toList() == []
    }

    @Controller("/json/stream")
    static class StreamController {

        @Get(produces = MediaType.APPLICATION_JSON_STREAM)
        Flowable<Book> stream() {
            return Flowable.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }

        @Get(uri = "/custom", produces = MediaType.APPLICATION_JSON_STREAM)
        HttpResponse<Flowable<Book>> streamResponse() {
            return HttpResponse.ok(Flowable.just(new Book(title: "The Stand"), new Book(title: "The Shining"))).header("X-MyHeader", "42")
        }

        @Get(uri = "/single", produces = MediaType.APPLICATION_JSON_STREAM)
        HttpResponse<Single<Book>> streamSingleResponse() {
            return HttpResponse.ok(Single.just(new Book(title: "The Stand"))).header("X-MyHeader", "42")
        }

        @Get(uri = "/empty", produces = MediaType.APPLICATION_JSON_STREAM)
        Flowable<Book> emptyStream() {
            return Flowable.empty()
        }

    }

    static class Book {
        String title
    }
}
