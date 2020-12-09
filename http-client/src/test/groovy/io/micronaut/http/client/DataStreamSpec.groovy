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
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.codec.CodecException
import io.micronaut.http.multipart.PartData
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.reactivex.Flowable
import io.reactivex.Single
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Issue
import spock.lang.Retry
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

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
        PollingConditions conditions = new PollingConditions(timeout: 2)

        List<byte[]> arrays = []
        publisher.subscribe(new Subscriber<ByteBuffer<?>>() {
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
        conditions.eventually {
            assert arrays.size() == 1
            assert new String(arrays[0]) == 'The Stand'
        }
    }

    void "test read response bytebuffer stream"() {
        when:
        List<byte[]> arrays = []
        PollingConditions conditions = new PollingConditions(timeout: 2)
        client.exchangeStream(HttpRequest.GET(
                '/datastream/books'
        )).subscribe({res -> arrays.add(res.body.get().toByteArray()) })

        then:
        conditions.eventually {
            assert arrays.size() == 2
            assert new String(arrays[0]) == 'The Stand'
            assert new String(arrays[1]) == 'The Shining'
        }
    }

    @Retry
    void "test that stream response is free of race conditions"() {
        when:
        List<byte[]> arrays = client.exchangeStream(HttpRequest.GET(
                '/datastream/books'
        )).map { res -> res.body.get().toByteArray() }.blockingIterable().toList()

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
        when:
        byte[] data = client.toBlocking().retrieve("/datastream/data", byte[].class)

        then:
        data == [188309,188310] as byte[]
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/1286")
    void "test returning a stream and sending a multipart request"() {
        def requestBody = MultipartBody.builder()
                .addPart(
                        "data",
                        "randomFileName.dat",
                        MediaType.APPLICATION_OCTET_STREAM_TYPE,
                        new byte[4096]
                )

        def request = HttpRequest.POST("/datastream/upload", requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                .accept(MediaType.TEXT_PLAIN_TYPE)

        HttpResponse response
        String body
        client.exchangeStream(request)
                .doOnNext(resp -> {
                    response = resp
                    body = new String(resp.body().toByteArray())
                })
                .subscribe()

        expect:
        new PollingConditions(timeout: 3).eventually {
            assert response.status() == HttpStatus.OK
            assert body == "Read 4096 bytes"
        }
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

        @Post(uri = "/upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Single<HttpResponse<String>> test(StreamingFileUpload data) {
            AtomicInteger bytes = new AtomicInteger()

            Single.<HttpResponse<String>>create { emitter ->
                data.subscribe(new Subscriber<PartData>() {
                    private Subscription s

                    @Override
                    void onSubscribe(Subscription s) {
                        this.s = s
                        s.request(1)
                    }

                    @Override
                    void onNext(PartData partData) {
                        bytes.addAndGet(partData.bytes.length)
                        s.request(1)
                    }

                    @Override
                    void onError(Throwable t) {
                        emitter.onError(t)
                    }

                    @Override
                    void onComplete() {
                        emitter.onSuccess(HttpResponse.ok("Read ${bytes.get()} bytes".toString()))
                    }
                })
            }
        }
    }
}
