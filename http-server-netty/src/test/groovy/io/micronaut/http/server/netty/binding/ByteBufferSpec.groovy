package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.reactivex.Flowable
import spock.lang.Specification

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

@MicronautTest
@Property(name = "spec.name", value = "ByteBufferSpec")
class ByteBufferSpec extends Specification {

    @Inject
    @Client("/")
    RxHttpClient rxClient

    void "test reading the body with a publisher of bytebuffers"() {
        when:
        String response = rxClient.retrieve(
                HttpRequest.POST("/buffer-test", "hello")
                        .contentType(MediaType.TEXT_PLAIN), String).blockingFirst()

        then:
        response == "hello"
    }

    void "test reading a long body with a completable of bytebuffers"() {
        when:
        String body = "hello" * 1000
        String response = rxClient.retrieve(
                HttpRequest.POST("/buffer-completable", body)
                        .contentType(MediaType.TEXT_PLAIN), String).blockingFirst()

        then:
        response == body
    }

    void "test read bytes"() {
        when:
        def bytes = rxClient.retrieve(HttpRequest.GET('/bytes'), byte[].class).blockingFirst()

        then:
        new String(bytes) == 'blah'
    }


    void "test read byteBuffer"() {
        when:
        def bytes = rxClient.retrieve(HttpRequest.GET('/byteBuffer'), byte[].class).blockingFirst()

        then:
        new String(bytes) == 'blah'
    }

    void "test read byteBuf"() {
        when:
        def bytes = rxClient.retrieve(HttpRequest.GET('/byteBuf'), byte[].class).blockingFirst()

        then:
        new String(bytes) == 'blah'
    }

    @Requires(property = "spec.name", value = "ByteBufferSpec")
    @Controller
    static class ByteBufferController {

        @Inject ByteBufferFactory<?, ?> byteBufferFactory

        @Post(uri = "/buffer-test", processes = MediaType.TEXT_PLAIN)
        Flowable<String> buffer(@Body Flowable<ByteBuffer> body) {
            return body.map({ buffer -> buffer.toString(StandardCharsets.UTF_8) })
        }

        @Post(uri = "/buffer-completable", processes = MediaType.TEXT_PLAIN)
        CompletableFuture<String> buffer(@Body CompletableFuture<ByteBuffer> body) {
            return body.thenApply({ buffer -> buffer.toString(StandardCharsets.UTF_8) })
        }

        @Get(uri = "/bytes", produces = MediaType.IMAGE_JPEG)
        HttpResponse<byte[]> bytesAndResponse() throws IOException {
            return HttpResponse
                    .ok("blah".getBytes())
                    .contentType(MediaType.IMAGE_JPEG);
        }

        @Get(uri = "/byteBuffer", produces = MediaType.IMAGE_JPEG)
        HttpResponse<ByteBuffer<?>> byteBufferAndResponse() throws IOException {
            return HttpResponse
                    .ok(byteBufferFactory.wrap("blah".getBytes()))
                    .contentType(MediaType.IMAGE_JPEG);
        }


        @Get(uri = "/byteBuf", produces = MediaType.IMAGE_JPEG)
        HttpResponse<ByteBuf> byteBuffAndResponse() throws IOException {
            return HttpResponse
                    .ok(Unpooled.copiedBuffer("blah", StandardCharsets.UTF_8))
                    .contentType(MediaType.IMAGE_JPEG);
        }
    }

}
