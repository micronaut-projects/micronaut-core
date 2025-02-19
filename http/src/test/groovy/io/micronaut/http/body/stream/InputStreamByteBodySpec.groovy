package io.micronaut.http.body.stream

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.body.ByteBodyFactory
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class InputStreamByteBodySpec extends Specification {
    def move() {
        given:
        def pool = Executors.newCachedThreadPool()
        def a = InputStreamByteBody.create(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)), OptionalLong.empty(), pool, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE))
        def b = a.move()

        when:
        a.close()
        then:
        b.buffer().get().toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        pool.shutdown()
    }
}
