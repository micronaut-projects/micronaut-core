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
package io.micronaut.http.client

import groovy.transform.EqualsAndHashCode
import io.micronaut.http.annotation.QueryValue
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.http.annotation.Post
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HttpPostSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    void "test send invalid http method"() {
        given:
        def book = new Book(title: "The Stand", pages: 1000)

        when:
        Flowable<HttpResponse<Book>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.PATCH("/post/simple", book)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientException)
        e.message == "Method [PATCH] not allowed. Allowed methods: [POST]"
    }

    void "test simple post request with JSON"() {
        given:
        def book = new Book(title: "The Stand", pages: 1000)

        when:
        Flowable<HttpResponse<Book>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/post/simple", book)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        ))
        HttpResponse<Book> response = flowable.blockingFirst()
        Optional<Book> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.contentLength == 34
        body.isPresent()
        body.get() instanceof Book
        body.get() == book
    }



    void "test simple post request with URI template and JSON"() {
        given:
        def book = new Book(title: "The Stand",pages: 1000)
        when:
        Flowable<HttpResponse<Book>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/post/title/{title}", book)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        ))
        HttpResponse<Book> response = flowable.blockingFirst()
        Optional<Book> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.contentLength == 34
        body.isPresent()
        body.get() instanceof Book
        body.get().title == 'The Stand'
    }

    void "test simple post request with URI template and JSON Map"() {
        given:
        def book = [title: "The Stand",pages: 1000]
        when:
        Flowable<HttpResponse<Map>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/post/title/{title}", book)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Map
        ))
        HttpResponse<Map> response = flowable.blockingFirst()
        Optional<Map> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.contentLength == 34
        body.isPresent()
        body.get() instanceof Map
        body.get() == book
    }

    void "test simple post request with Form data"() {
        given:
        def book = new Book(title: "The Stand", pages: 1000)
        when:
        Flowable<HttpResponse<Book>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/post/form", book)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        ))
        HttpResponse<Book> response = flowable.blockingFirst()
        Optional<Book> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.contentLength == 34
        body.isPresent()
        body.get() instanceof Book
        body.get().title == 'The Stand'
    }

    void "test simple post retrieve blocking request with JSON"() {
        given:
        def toSend = new Book(title: "The Stand",pages: 1000)
        when:
        BlockingHttpClient blockingHttpClient = client.toBlocking()
        Book book = blockingHttpClient.retrieve(
                HttpRequest.POST("/post/simple", toSend)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        )

        then:
        book == toSend
    }

    void "test simple post request with a queryValue "() {
        given:
        def toSend = new Book(title: "The Stand",pages: 1000)
        when:
        BlockingHttpClient blockingHttpClient = client.toBlocking()
        Book book = blockingHttpClient.retrieve(
                HttpRequest.POST("/post/query?title=The%20Stand", toSend)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        )

        then:
        book == toSend
    }

    void "test simple post request with a queryValue and no body"() {
        when:
        BlockingHttpClient blockingHttpClient = client.toBlocking()
        Book book = blockingHttpClient.retrieve(
                HttpRequest.POST("/post/queryNoBody?title=The%20Stand", "")
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        )

        then:
        book.pages == 0
        book.title == "The Stand"
    }

    @Controller('/post')
    static class PostController {

        @Post('/simple')
        Book simple(@Body Book book, @Header String contentType, @Header long contentLength, @Header accept, @Header('X-My-Header') custom) {
            assert contentType == MediaType.APPLICATION_JSON
            assert contentLength == 34
            assert accept == MediaType.APPLICATION_JSON
            assert custom == 'Foo'
            return book
        }

        @Post('/query')
        Book simple(@Body Book book, @QueryValue String title) {
            assert title == book.title
            return book
        }

        @Post('/queryNoBody')
        Book simple(@QueryValue("title") String title) {
            return new Book(title: title, pages: 0)
        }

        @Post('/title/{title}')
        Book title(@Body Book book, String title, @Header String contentType, @Header long contentLength, @Header accept, @Header('X-My-Header') custom) {
            assert title == book.title
            assert contentType == MediaType.APPLICATION_JSON
            assert contentLength == 34
            assert accept == MediaType.APPLICATION_JSON
            assert custom == 'Foo'
            return book
        }

        @Post(uri = '/form', consumes = MediaType.APPLICATION_FORM_URLENCODED)
        Book form(@Body Book book, @Header String contentType, @Header long contentLength, @Header accept, @Header('X-My-Header') custom) {
            assert contentType == MediaType.APPLICATION_FORM_URLENCODED
            assert contentLength == 26
            assert accept == MediaType.APPLICATION_JSON
            assert custom == 'Foo'
            return book
        }
    }

    @EqualsAndHashCode
    static class Book {
        String title
        Integer pages
    }
}
