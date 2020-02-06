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
import io.micronaut.http.codec.CodecException
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

import javax.print.attribute.standard.Media
import java.nio.charset.StandardCharsets

/**
 * @author graemerocher
 * @since 1.0
 */
class DataStreamSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test read bytebuffer stream"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient,embeddedServer.getURL())

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

        cleanup:
        client.stop()

    }

    void "test read bytebuffer stream - regulate demand"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient,embeddedServer.getURL())

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

        cleanup:
        client.stop()

    }

    void "test read response bytebuffer stream"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient,embeddedServer.getURL())

        when:
        List<byte[]> arrays = client.exchangeStream(HttpRequest.GET(
                '/datastream/books'
        )).map({res -> res.body.get().toByteArray() }).toList().blockingGet()

        then:
        arrays.size() == 2
        new String(arrays[0]) == 'The Stand'
        new String(arrays[1]) == 'The Shining'

        cleanup:
        client.stop()

    }

    void "test that stream response is free of race conditions"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient,embeddedServer.getURL())

        when:
        List<byte[]> arrays = client.exchangeStream(HttpRequest.GET(
                '/datastream/books'
        )).blockingIterable().toList().collect { res -> res.body.get().toByteArray() }

        then:
        arrays.size() == 2
        new String(arrays[0]) == 'The Stand'
        new String(arrays[1]) == 'The Shining'

        cleanup:
        client.stop()
    }

    void "test streaming body codec exception"() {
        given:
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        Publisher<String> bodyPublisher = client.retrieve(HttpRequest.POST(
                '/datastream/books', Flowable.just(new Book(title: 'The Shining'))
        ).contentType("custom/content"))
        String body = Flowable.fromPublisher(bodyPublisher).toList().map({list -> list.join('')}).blockingGet()

        then:
        def ex = thrown(CodecException)
        ex.message.startsWith("Cannot encode value")

        cleanup:
        client.stop()
    }

    void "test streaming ByteBuffer"() {
        given:
        ByteBuffer<byte[]> buffer = context.getBean(ByteBufferFactory).wrap("The Shining".bytes)
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        Publisher<String> bodyPublisher = client.retrieve(HttpRequest.POST(
                '/datastream/books', Flowable.just(buffer)
        ).contentType("custom/content"))
        String body = Flowable.fromPublisher(bodyPublisher).toList().map({list -> list.join('')}).blockingGet()

        then:
        body == 'The Shining'

        cleanup:
        client.stop()
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
    }
}
