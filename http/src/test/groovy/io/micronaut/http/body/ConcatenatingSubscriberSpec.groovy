package io.micronaut.http.body

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.body.stream.AvailableByteArrayBody
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ConcatenatingSubscriberSpec extends Specification {
    private static AvailableByteArrayBody available(String text) {
        return AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, text.getBytes(StandardCharsets.UTF_8))
    }

    private static ByteBuffer buffer(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8))
    }

    def test() {
        given:
        def input = Flux.just(
                available("s1"),
                available("s2"),
                ByteBufferBodyAdapter.adapt(Flux.just(buffer("s3"), buffer("s4")))
        )

        when:
        def text = new String(ConcatenatingSubscriber.JsonByteBufferConcatenatingSubscriber.concatenateJson(input).toInputStream().readAllBytes(), StandardCharsets.UTF_8)
        then:
        text == "[s1,s2,s3s4]"
    }
}
