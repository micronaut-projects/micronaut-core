package io.micronaut.http.client.netty

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ErrorBodyAccumulatorSpec extends Specification {

    void "many tiny chunks retain a bounded number of components"() {
        given:
        def accumulator = new ErrorBodyAccumulator(ByteBufAllocator.DEFAULT)

        when:
        200_000.times {
            accumulator.add(Unpooled.wrappedBuffer([(byte) 'a'] as byte[]))
            accumulator.add(Unpooled.EMPTY_BUFFER)
        }

        then:
        accumulator.readableBytes() == 200_000
        accumulator.numComponents() <= 200_000.intdiv(ErrorBodyAccumulator.TAIL_CAPACITY) + 1

        when:
        def body = accumulator.finish()

        then:
        body.toString(StandardCharsets.US_ASCII) == 'a' * 200_000

        cleanup:
        accumulator.release()
    }

    void "large chunks are kept as components and order is preserved"() {
        given:
        def accumulator = new ErrorBodyAccumulator(ByteBufAllocator.DEFAULT)
        def big = 'b' * ErrorBodyAccumulator.TAIL_CAPACITY

        when:
        accumulator.add(Unpooled.copiedBuffer('x', StandardCharsets.US_ASCII))
        accumulator.add(Unpooled.copiedBuffer(big, StandardCharsets.US_ASCII))
        accumulator.add(Unpooled.copiedBuffer('y', StandardCharsets.US_ASCII))

        then:
        accumulator.finish().toString(StandardCharsets.US_ASCII) == 'x' + big + 'y'

        cleanup:
        accumulator.release()
    }
}
