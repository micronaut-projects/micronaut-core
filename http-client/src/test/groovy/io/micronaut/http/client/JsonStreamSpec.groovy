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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

/**
 * Created by graemerocher on 19/01/2018.
 */
class JsonStreamSpec  extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    BookClient bookClient = embeddedServer.getApplicationContext().getBean(BookClient)

    void "test read JSON stream demand all"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        List<Map> jsonObjects = client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        )).toList().blockingGet()

        then:
        jsonObjects.size() == 2
        jsonObjects[0].title == 'The Stand'
        jsonObjects[1].title == 'The Shining'

        cleanup:
        client.stop()

    }

    void "test read JSON stream demand all POJO"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        List<Book> jsonObjects = client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        ), Book).toList().blockingGet()

        then:
        jsonObjects.size() == 2
        jsonObjects.every() { it instanceof Book}
        jsonObjects[0].title == 'The Stand'
        jsonObjects[1].title == 'The Shining'
    }

    void "test read JSON stream demand one"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        def stream = client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        ))
        Map json

        stream.subscribe(new Subscriber<Map<String, Object>>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(1)
            }

            @Override
            void onNext(Map<String, Object> stringObjectMap) {
                json = stringObjectMap
            }

            @Override
            void onError(Throwable t) {

            }

            @Override
            void onComplete() {

            }
        })

        PollingConditions conditions = new PollingConditions()
        then:
        conditions.eventually {
            json != null
            json.title == "The Stand"
        }

    }

    void "test that consumes/produces are symmetrical"() {
        when:
        def result = bookClient.format([new Book(title: "Micronaut for dummies")])
        then:
        result == "<h1>Book</h1><p>Title: Micronaut for dummies</p>"
    }

    static interface BookOps {

        @Get(produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list()

        @Post(value="/format")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(value = MediaType.TEXT_HTML, single = true)
        String format(@Body List<Book> theBooks)
    }

    @Controller("/jsonstream/books")
    static class BookController implements BookOps {

        @Get(produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list() {
            return Flowable.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }

        @Post("/format")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(value = MediaType.TEXT_HTML, single = true)
        String format(@Body List<Book> theBooks) {
            return theBooks.collect {
                Book book -> "<h1>Book</h1><p>Title: $book.title</p>".toString() // XSS
            }.join()
        }
    }

    @Client("/jsonstream/books")
    static interface BookClient extends BookOps {
    }

    static class Book {
        String title
    }
}

