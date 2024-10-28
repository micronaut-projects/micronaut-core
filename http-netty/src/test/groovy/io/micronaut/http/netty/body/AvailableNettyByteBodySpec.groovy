package io.micronaut.http.netty.body

import io.netty.buffer.Unpooled
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class AvailableNettyByteBodySpec extends Specification {
    def move() {
        given:
        def a = new AvailableNettyByteBody(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8))
        def b = a.move()

        when:
        a.close()
        then:
        b.buffer().get().toString(StandardCharsets.UTF_8) == "foo"
    }
}
