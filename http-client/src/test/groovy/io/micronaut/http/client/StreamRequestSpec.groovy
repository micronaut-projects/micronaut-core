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
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * @author graemerocher
 * @since 1.0
 */
@Property(name = 'spec.name', value = 'StreamRequestSpec')
@MicronautTest
class StreamRequestSpec extends Specification {
    @Inject
    @Client("/")
    StreamingHttpClient client

    @Inject
    ApplicationContext applicationContext

    @Inject
    EmbeddedServer embeddedServer

    void "test stream post request with numbers"() {

        when:
        int i = 0
        HttpResponse<List> result = Flux.from(client.exchange(HttpRequest.POST('/stream/request/numbers', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next(i++)
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER
        )).contentType(MediaType.APPLICATION_JSON_TYPE), List)).blockFirst()

        then:
        result.body().size() == 5
        result.body() == [0, 1, 2, 3, 4]
   }

    void "test stream post request with strings"() {
        when:
        int i = 0
        HttpResponse<List> result = Flux.from(client.exchange(HttpRequest.POST('/stream/request/strings', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next("Number ${i++}")
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER
        )).contentType(MediaType.TEXT_PLAIN_TYPE), List)).blockFirst()

        then:
        result.body().size() == 5
        result.body() == ["Number 0", "Number 1", "Number 2", "Number 3", "Number 4"]

    }

    void "test stream get request with JSON strings"() {
        given:
        StreamingHttpClient client = StreamingHttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse<?> result = Flux.from(client.exchangeStream(HttpRequest.GET('/stream/request/jsonstrings'))).blockFirst()

        then:
        result.headers.getAll(HttpHeaders.TRANSFER_ENCODING).size() == 1

        cleanup:
        client.stop()
        client.close()
    }

    void "test stream post request with byte chunks"() {
        when:
        int i = 0
        HttpResponse<List> result = Flux.from(client.exchange(HttpRequest.POST('/stream/request/bytes', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next("Number ${i++}".getBytes(StandardCharsets.UTF_8))
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER

        )).contentType(MediaType.TEXT_PLAIN_TYPE), List)).blockFirst()

        then:
        result.body().size() == 5
        result.body() == ["Number 0", "Number 1", "Number 2", "Number 3", "Number 4"]
    }

    void "test stream post request with POJOs"() {
        when:
        int i = 0
        HttpResponse<List> result = Flux.from(client.exchange(HttpRequest.POST('/stream/request/pojos', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next(new Book(title:"Number ${i++}"))
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER
        )), Argument.of(List, Book))).blockFirst()

        then:
        result.body().size() == 5
        result.body() == [new Book(title: "Number 0"), new Book(title: "Number 1"), new Book(title: "Number 2"), new Book(title: "Number 3"), new Book(title: "Number 4")]
    }

    void "test stream post request with POJOs flowable"() {

        given:
        def configuration = new DefaultHttpClientConfiguration()
        configuration.setReadTimeout(Duration.ofMinutes(1))
        HttpClient client = applicationContext.createBean(HttpClient, embeddedServer.getURL(), configuration)

        when:
        int i = 0
        HttpResponse<List> result = Flux.from(client.exchange(HttpRequest.POST('/stream/request/pojo-flowable', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next(new Book(title:"Number ${i++}"))
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER

        )), Argument.of(List, Book))).blockFirst()

        then:
        result.body().size() == 5
        result.body() == [new Book(title: "Number 0"), new Book(title: "Number 1"), new Book(title: "Number 2"), new Book(title: "Number 3"), new Book(title: "Number 4")]

        cleanup:
        client.stop()
        client.close()
    }

    void "test json stream post request with POJOs flowable"() {
        when:
        int i = 0
        List<Book> result = Flux.from(client.jsonStream(HttpRequest.POST('/stream/request/pojo-flowable', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next(new Book(title:"Number ${i++}"))
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER

        )), Book)).collectList().block()

        then:
        result.size() == 5
        result == [new Book(title: "Number 0"), new Book(title: "Number 1"), new Book(title: "Number 2"), new Book(title: "Number 3"), new Book(title: "Number 4")]
    }

    void "test json stream post request with POJOs flowable error"() {
        when:
        int i = 0
        List<Book> result = Flux.from(client.jsonStream(HttpRequest.POST('/stream/request/pojo-flowable-error', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next(new Book(title:"Number ${i++}"))
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER

        )), Book)).collectList().block()

        then:
        def e= thrown(RuntimeException) // TODO: this should be HttpClientException
        e != null

    }

    void "test manually setting the content length does not chunked encoding"() {
        when:
        int i = 0
        HttpResponse<String> result = Flux.from(client.exchange(HttpRequest.POST('/stream/request/strings/contentLength', Flux.create(emitter -> {
                while(i < 5) {
                    emitter.next("aa")
                    i++
                }
                emitter.complete()
        }, FluxSink.OverflowStrategy.BUFFER
        )).contentType(MediaType.TEXT_PLAIN_TYPE).contentLength(10), String)).blockFirst()

        then:
        noExceptionThrown()
        result.body().size() == 10
        result.body() == "aaaaaaaaaa"

    }

    @Requires(property = 'spec.name', value = 'StreamRequestSpec')
    @Controller('/stream/request')
    static class StreamController {

        @Post("/numbers")
        @SingleResult
        Publisher<List<Long>> numbers(@Header MediaType contentType, @Body Publisher<List<Long>> numbers) {
            assert contentType == MediaType.APPLICATION_JSON_TYPE
            numbers
        }

        @Get("/jsonstrings")
        Publisher<String> jsonStrings() {
            return Flux.just("Hello World")
        }

        @Post(uri = "/strings", consumes = MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<List<String>> strings(@Body Publisher<String> strings) {
            Flux.from(strings).collectList()
        }

        @Post(uri = "/strings/contentLength", processes = MediaType.TEXT_PLAIN)
        Publisher<String> strings(@Body Publisher<String> strings, HttpHeaders headers) {
            assert headers.contentLength().isPresent()
            assert headers.contentLength().getAsLong() == 10
            assert !headers.getFirst(HttpHeaders.TRANSFER_ENCODING).isPresent()
            strings
        }

        @Post(uri = "/bytes", consumes = MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<List<String>> bytes(@Body Publisher<byte[]> strings) {
            Flux.from(strings).map({ byte[] bytes -> new String(bytes, StandardCharsets.UTF_8)}).collectList()
        }

        @Post("/pojos")
        @SingleResult
        Publisher<List<Book>> pojos(@Header MediaType contentType, @Body Publisher<List<Book>> books) {
            assert contentType == MediaType.APPLICATION_JSON_TYPE
            books
        }

        @Post("/pojo-flowable")
        Publisher<Book> pojoReactiveSequence(@Header MediaType contentType, @Body Publisher<Book> books) {
            assert contentType == MediaType.APPLICATION_JSON_TYPE
            books
        }

        @Post("/pojo-flowable-error")
        Publisher<Book> pojoReactiveSequenceError(@Header MediaType contentType, @Body Publisher<Book> books) {
            return Flux.from(books).flatMap({ Book book ->
                if(book.title.endsWith("3")) {
                    return Flux.error(new RuntimeException("Can't have books with 3"))
                }
                else {
                    return Flux.just(book)
                }
            })
        }
    }

    @EqualsAndHashCode
    @ToString(includePackage = false)
    static class Book {
        String title
    }
}
