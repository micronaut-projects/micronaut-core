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

import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.codec.CodecException
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.internal.operators.flowable.FlowableBlockingSubscribe
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.print.attribute.standard.Media
import java.nio.charset.StandardCharsets

/**
 * @author graemerocher
 * @since 1.0
 */
@MicronautTest
class DataStreamSpec extends Specification {

    @Inject
    @Client("/")
    RxStreamingHttpClient client

    @Inject
    ByteBufferFactory bufferFactory

    void "test read bytebuffer stream"() {

        when:
        List<byte[]> arrays = client.dataStream(HttpRequest.GET(
                '/datastream/books'
        )).map({buf ->
            buf.toByteArray()}
        ).toList().blockingGet()

        then:
        arrays.size() == 2
        new String(arrays[0]) == 'The Stand'
        new String(arrays[1]) == 'The Shining'

    }

    void "test read bytebuffer stream - regulate demand"() {

        when:
        def publisher = client.dataStream(HttpRequest.GET(
                '/datastream/books'
        ))

        List<byte[]> arrays = []
        FlowableBlockingSubscribe.subscribe(publisher, new Subscriber<ByteBuffer<?>>() {
            Subscription subscription
            @Override
            void onSubscribe(Subscription s) {
                s.request(1)
                subscription = s
            }

            @Override
            void onNext(ByteBuffer<?> byteBuffer) {
                arrays.add(byteBuffer.toByteArray())
                subscription.cancel()
            }

            @Override
            void onError(Throwable t) {

            }

            @Override
            void onComplete() {

            }
        })

        then:
        arrays.size() == 1
        new String(arrays[0]) == 'The Stand'
    }

    void "test read response bytebuffer stream"() {
        when:
        List<byte[]> arrays = client.exchangeStream(HttpRequest.GET(
                '/datastream/books'
        )).map({res -> res.body.get().toByteArray() }).toList().blockingGet()

        then:
        arrays.size() == 2
        new String(arrays[0]) == 'The Stand'
        new String(arrays[1]) == 'The Shining'
    }

    void "test that stream response is free of race conditions"() {
        when:
        List<byte[]> arrays = client.exchangeStream(HttpRequest.GET(
                '/datastream/books'
        )).blockingIterable().toList().collect { res -> res.body.get().toByteArray() }

        then:
        arrays.size() == 2
        new String(arrays[0]) == 'The Stand'
        new String(arrays[1]) == 'The Shining'
    }

    void "test streaming body codec exception"() {
        when:
        Publisher<String> bodyPublisher = client.retrieve(HttpRequest.POST(
                '/datastream/books', Flowable.just(new Book(title: 'The Shining'))
        ).contentType("custom/content"))
        String body = Flowable.fromPublisher(bodyPublisher).toList().map({list -> list.join('')}).blockingGet()

        then:
        def ex = thrown(CodecException)
        ex.message.startsWith("Cannot encode value")

    }

    void "test streaming ByteBuffer"() {
        given:
        ByteBuffer<byte[]> buffer = bufferFactory.wrap("The Shining".bytes)

        when:
        Publisher<String> bodyPublisher = client.retrieve(HttpRequest.POST(
                '/datastream/books', Flowable.just(buffer)
        ).contentType("custom/content"))
        String body = Flowable.fromPublisher(bodyPublisher).toList().map({list -> list.join('')}).blockingGet()

        then:
        body == 'The Shining'

    }

    void "test reading a byte array"() {
        RxStreamingHttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        byte[] data = client.toBlocking().retrieve("/datastream/data", byte[].class)

        then:
        data == [188309,188310] as byte[]
    }

    static class Book {
        String title
    }

    @Controller("/datastream")
    static class BookController {

        @Get(uri = "/books", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<byte[]> list() {
            return Flowable.just("The Stand".getBytes(StandardCharsets.UTF_8), "The Shining".getBytes(StandardCharsets.UTF_8))
        }

        @Post(uri = "/books", consumes = "custom/content", produces = MediaType.TEXT_PLAIN)
        Publisher<String> list(@Body Publisher<String> body) {
            return body
        }

        //testing that the client will ignore the content type if the type requested is byte[]
        @Get(uri = "/data", produces = MediaType.TEXT_PLAIN)
        byte[] data() {
            [188309,188310] as byte[]
        }
    }
}
