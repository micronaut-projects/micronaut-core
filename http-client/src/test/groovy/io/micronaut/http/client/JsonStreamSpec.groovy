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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.reactivex.Flowable
import io.reactivex.Single
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Created by graemerocher on 19/01/2018.
 */
class JsonStreamSpec  extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    BookClient bookClient = embeddedServer.getApplicationContext().getBean(BookClient)

    static Semaphore signal

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

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1864')
    void "test read JSON stream raw data and demand all"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        List<Chunk> jsonObjects = client.jsonStream(
                HttpRequest.POST('/jsonstream/books/raw', '''
{"type":"ADDED"}
{"type":"ADDED"}
{"type":"ADDED"}
{"type":"ADDED"}
''').contentType(MediaType.APPLICATION_JSON_STREAM_TYPE)
    .accept(MediaType.APPLICATION_JSON_STREAM_TYPE), Chunk).toList().blockingGet()

        then:
        jsonObjects.size() == 4

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

    void "we can stream books to the server"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, embeddedServer.getURL())
        signal = new Semaphore(1)
        when:
        // Funny request flow which required the server to relase the semaphore so we can keep sending stuff
        def stream = client.jsonStream(HttpRequest.POST(
                '/jsonstream/books/count',
                Flowable.fromCallable {
                    JsonStreamSpec.signal.acquire()
                    new Book(title: "Micronaut for dummies")
                }
                .repeat(10)
                ).contentType(MediaType.APPLICATION_JSON_STREAM_TYPE).accept(MediaType.APPLICATION_JSON_STREAM_TYPE))

        then:
        stream.timeout(5, TimeUnit.SECONDS).blockingSingle().bookCount == 10
    }

    void "we can stream data from the server through the generated client"() {
        when:
        List<Book> books = Flowable.fromPublisher(bookClient.list()).toList().blockingGet()
        then:
        books.size() == 2
        books*.title == ['The Stand', 'The Shining']
    }

    void "we can use a generated client to stream books to the server"() {
        given:
        signal = new Semaphore(1)
        when:
        Single<LibraryStats> result = bookClient.count(
                Flowable.fromCallable {
                    JsonStreamSpec.signal.acquire()
                    new Book(title: "Micronaut for dummies, volume 2")
                }
                .repeat(7))
        then:
        result.timeout(10, TimeUnit.SECONDS).blockingGet().bookCount == 7
    }

    @Client("/jsonstream/books")
    static interface BookClient {
        @Get(consumes = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list();

        @Post(uri = "/count", processes = MediaType.APPLICATION_JSON_STREAM)
        Single<LibraryStats> count(@Body Flowable<Book> theBooks)
    }

    @Controller("/jsonstream/books")
    @ExecuteOn(TaskExecutors.IO)
    static class BookController {

        @Get(produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list() {
            return Flowable.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }

        // Funny controller which signals the semaphone, causing the the client to send more
        @Post(uri = "/count", processes = MediaType.APPLICATION_JSON_STREAM)
        Single<LibraryStats> count(@Body Flowable<Book> theBooks) {
            theBooks.map {
                Book b ->
                    JsonStreamSpec.signal.release()
                    b.title
            }.count().map {
                bookCount -> new LibraryStats(bookCount: bookCount)
            }
        }

        @Post(uri = "/raw", processes = MediaType.APPLICATION_JSON_STREAM)
        String rawData(@Body Flowable<Chunk> chunks) {
            return chunks
                    .map({ chunk -> "{\"type\":\"${chunk.type}\"}"})
                    .toList()
                    .map({ chunkList -> "\n" + chunkList.join("\n")})
                    .blockingGet()
        }
    }

    static class Book {
        String title
    }

    static class LibraryStats {
        Integer bookCount
    }

    static class Chunk {
        String type
    }

}

