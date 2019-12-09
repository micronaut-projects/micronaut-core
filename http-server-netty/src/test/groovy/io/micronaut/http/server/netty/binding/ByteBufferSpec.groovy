package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.reactivex.Flowable

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class ByteBufferSpec extends AbstractMicronautSpec {

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

    @Requires(property = "spec.name", value = "ByteBufferSpec")
    @Controller
    static class ByteBufferController {

        @Post(uri = "/buffer-test", processes = MediaType.TEXT_PLAIN)
        Flowable<String> buffer(@Body Flowable<ByteBuffer> body) {
            return body.map({ buffer -> buffer.toString(StandardCharsets.UTF_8) })
        }

        @Post(uri = "/buffer-completable", processes = MediaType.TEXT_PLAIN)
        CompletableFuture<String> buffer(@Body CompletableFuture<ByteBuffer> body) {
            return body.thenApply({ buffer -> buffer.toString(StandardCharsets.UTF_8) })
        }
    }

}
