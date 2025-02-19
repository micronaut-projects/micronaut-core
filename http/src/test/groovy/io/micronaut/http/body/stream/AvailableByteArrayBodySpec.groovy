package io.micronaut.http.body.stream

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class AvailableByteArrayBodySpec extends Specification {
    def move() {
        given:
        def a = AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, "foo".getBytes(StandardCharsets.UTF_8))
        def b = a.move()

        when:
        a.close()
        then:
        b.buffer().get().toString(StandardCharsets.UTF_8) == "foo"
    }
}
