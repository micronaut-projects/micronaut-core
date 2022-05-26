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

import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
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
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.Semaphore

/**
 * Created by graemerocher on 19/01/2018.
 */
class JsonStreamSpec  extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            "spec.name": 'JsonStreamSpec'
    ])

    @Shared
    @AutoCleanup
    ApplicationContext context = embeddedServer.applicationContext

    @Shared
    BookClient bookClient = context.getBean(BookClient)

    static Semaphore signal

    void "test read JSON stream demand all"() {
        given:
        StreamingHttpClient client = context.createBean(StreamingHttpClient, embeddedServer.getURL())

        when:
        List<Map> jsonObjects = Flux.from(client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        ))).collectList().block()

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
        StreamingHttpClient client = context.createBean(StreamingHttpClient, embeddedServer.getURL())

        when:
        List<Chunk> jsonObjects = Flux.from(client.jsonStream(
                HttpRequest.POST('/jsonstream/books/raw', '''
{"type":"ADDED"}
{"type":"ADDED"}
{"type":"ADDED"}
{"type":"ADDED"}
''').contentType(MediaType.APPLICATION_JSON_STREAM_TYPE)
    .accept(MediaType.APPLICATION_JSON_STREAM_TYPE), Chunk)).collectList().block()

        then:
        jsonObjects.size() == 4

        cleanup:
        client.stop()

    }

    void "test read JSON stream demand all POJO"() {
        given:
        StreamingHttpClient client = context.createBean(StreamingHttpClient, embeddedServer.getURL())

        when:
        List<Book> jsonObjects = Flux.from(client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        ), Book)).collectList().block()

        then:
        jsonObjects.size() == 2
        jsonObjects.every() { it instanceof Book}
        jsonObjects[0].title == 'The Stand'
        jsonObjects[1].title == 'The Shining'
    }

    void "test read JSON stream demand one"() {
        given:
        StreamingHttpClient client = context.createBean(StreamingHttpClient, embeddedServer.getURL())

        when:
        Flux stream = Flux.from(client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        )))
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
        StreamingHttpClient client = context.createBean(StreamingHttpClient, embeddedServer.getURL())
        signal = new Semaphore(1)
        when:
        // Funny request flow which required the server to release the semaphore so we can keep sending stuff
        Flux stream = Flux.from(client.jsonStream(HttpRequest.POST(
                '/jsonstream/books/count',
                Mono.fromCallable {
                    JsonStreamSpec.signal.acquire()
                    new Book(title: "Micronaut for dummies")
                }
                .repeat(9)
                ).contentType(MediaType.APPLICATION_JSON_STREAM_TYPE).accept(MediaType.APPLICATION_JSON_STREAM_TYPE)))

        then:
        stream.timeout(Duration.of(5, ChronoUnit.SECONDS)).blockFirst().bookCount == 10
    }

    void "we can stream data from the server through the generated client"() {
        when:
        List<Book> books = Flux.from(bookClient.list()).collectList().block()
        then:
        books.size() == 2
        books*.title == ['The Stand', 'The Shining']
    }

    void "we can use a generated client to stream books to the server"() {
        given:
        signal = new Semaphore(1)
        when:
        Mono<LibraryStats> result = Mono.from(bookClient.count(
                Mono.fromCallable {
                    JsonStreamSpec.signal.acquire()
                    new Book(title: "Micronaut for dummies, volume 2")
                }
                .repeat(6)))
        then:
        result.timeout(Duration.of(10, ChronoUnit.SECONDS)).block().bookCount == 7
    }

    @Requires(property = "spec.name", value = 'JsonStreamSpec' )
    @Client("/jsonstream/books")
    static interface BookClient {
        @Get(consumes = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list();

        @Post(uri = "/count", processes = MediaType.APPLICATION_JSON_STREAM)
        @SingleResult
        Publisher<LibraryStats> count(@Body Flux<Book> theBooks)
    }

    @Requires(property = "spec.name", value = 'JsonStreamSpec' )
    @Controller("/jsonstream/books")
    @ExecuteOn(TaskExecutors.IO)
    static class BookController {

        @Get(produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list() {
            return Flux.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }

        // Funny controller which signals the semaphone, causing the the client to send more
        @Post(uri = "/count", processes = MediaType.APPLICATION_JSON_STREAM)
        Publisher<LibraryStats> count(@Body Publisher<Book> theBooks) {
            Flux.from(theBooks).map {
                Book b ->
                    JsonStreamSpec.signal.release()
                    b.title
            }.count().map {
                bookCount -> new LibraryStats(bookCount: bookCount)
            }
        }

        @Post(uri = "/raw", processes = MediaType.APPLICATION_JSON_STREAM)
        String rawData(@Body Publisher<Chunk> chunks) {
            return Flux.from(chunks)
                    .map({ chunk -> "{\"type\":\"${chunk.type}\"}"})
                    .collectList()
                    .map({ chunkList -> "\n" + chunkList.join("\n")})
                    .block()
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

