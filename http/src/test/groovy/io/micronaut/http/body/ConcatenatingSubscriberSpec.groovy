package io.micronaut.http.body

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ReadBuffer
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ConcatenatingSubscriberSpec extends Specification {
    private static final ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)

    private static CloseableAvailableByteBody available(String text) {
        return bbf.adapt(text.getBytes(StandardCharsets.UTF_8))
    }

    private static ReadBuffer buffer(String text) {
        return bbf.readBufferFactory().copyOf(text, StandardCharsets.UTF_8)
    }

    def test() {
        given:
        def input = Flux.just(
                available("s1"),
                available("s2"),
                bbf.adapt(Flux.just(buffer("s3"), buffer("s4")))
        )

        when:
        def text = new String(ConcatenatingSubscriber.concatenate(bbf, input, ConcatenatingSubscriber.Separators.JDK_JSON).toInputStream().readAllBytes(), StandardCharsets.UTF_8)
        then:
        text == "[s1,s2,s3s4]"
    }
}
