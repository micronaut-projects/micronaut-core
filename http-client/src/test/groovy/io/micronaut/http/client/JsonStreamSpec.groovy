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
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.UUID

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
    BookClient bookClient = embeddedServer.applicationContext.getBean(BookClient)

    @Shared
    @AutoCleanup
    StreamingHttpClient client = embeddedServer.applicationContext.createBean(StreamingHttpClient, embeddedServer.URL)

    @Shared
    StreamGate streamGate = embeddedServer.applicationContext.getBean(StreamGate)

    void "test read JSON stream demand all"() {
        when:
        List<Map> jsonObjects = Flux.from(client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        ))).collectList().block()

        then:
        jsonObjects.size() == 2
        jsonObjects[0].title == 'The Stand'
        jsonObjects[1].title == 'The Shining'
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1864')
    void "test read JSON stream raw data and demand all"() {
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
    }

    void "test read JSON stream demand all POJO"() {
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
        String requestId = UUID.randomUUID().toString()
        streamGate.reset(requestId)

        when:
        Flux stream = Flux.from(client.jsonStream(HttpRequest.POST(
                '/jsonstream/books/count',
                pacedBooks(10, "Micronaut for dummies ", requestId)
        ).header("X-Pacing-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON_STREAM_TYPE)
                .accept(MediaType.APPLICATION_JSON_STREAM_TYPE)))

        then:
        stream.timeout(Duration.of(5, ChronoUnit.SECONDS)).blockFirst().bookCount == 10

        cleanup:
        streamGate.clear(requestId)
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
        String requestId = UUID.randomUUID().toString()
        streamGate.reset(requestId)

        when:
        Mono<LibraryStats> result = Mono.from(bookClient.count(
                requestId,
                pacedBooks(7, "Micronaut for dummies, volume 2 - ", requestId)))

        then:
        result.timeout(Duration.of(10, ChronoUnit.SECONDS)).block().bookCount == 7

        cleanup:
        streamGate.clear(requestId)
    }

    private Flux<Book> pacedBooks(int total, String titlePrefix, String requestId) {
        Flux.range(1, total)
                .concatMap { index ->
                    Mono.fromCallable {
                        if (index > 1) {
                            streamGate.awaitPreviousReceived(requestId)
                        }
                        new Book(title: "${titlePrefix}${index}")
                    }.subscribeOn(Schedulers.boundedElastic())
                }
    }

    @Requires(property = "spec.name", value = 'JsonStreamSpec' )
    @Client("/jsonstream/books")
    static interface BookClient {
        @Get(consumes = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list();

        @Post(uri = "/count", processes = MediaType.APPLICATION_JSON_STREAM)
        Publisher<LibraryStats> count(@Header("X-Pacing-Id") String requestId,
                                      @Body Flux<Book> theBooks)
    }

    @Requires(property = "spec.name", value = 'JsonStreamSpec' )
    @Controller("/jsonstream/books")
    @ExecuteOn(TaskExecutors.IO)
    static class BookController {
        private final StreamGate streamGate

        BookController(StreamGate streamGate) {
            this.streamGate = streamGate
        }

        @Get(produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> list() {
            return Flux.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }

        @Post(uri = "/count", processes = MediaType.APPLICATION_JSON_STREAM)
        Publisher<LibraryStats> count(@Header("X-Pacing-Id") String requestId,
                                      @Body Publisher<Book> theBooks) {
            Flux.from(theBooks).map {
                Book b ->
                    streamGate.markReceived(requestId)
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

    @Requires(property = "spec.name", value = 'JsonStreamSpec' )
    @Singleton
    static class StreamGate {
        private final Map<String, BlockingQueue<Boolean>> acknowledgements = new ConcurrentHashMap<>()

        void reset(String requestId) {
            acknowledgements.put(requestId, new ArrayBlockingQueue<>(1))
        }

        void awaitPreviousReceived(String requestId) {
            try {
                if (queueFor(requestId).poll(10, TimeUnit.SECONDS) == null) {
                    throw new IllegalStateException("Timed out waiting for server to receive previous streamed item")
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new IllegalStateException("Interrupted while waiting for server receive acknowledgement", e)
            }
        }

        void markReceived(String requestId) {
            BlockingQueue<Boolean> queue = queueFor(requestId)
            queue.clear()
            queue.offer(Boolean.TRUE)
        }

        void clear(String requestId) {
            acknowledgements.remove(requestId)
        }

        private BlockingQueue<Boolean> queueFor(String requestId) {
            acknowledgements.computeIfAbsent(requestId) { new ArrayBlockingQueue<>(1) }
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
