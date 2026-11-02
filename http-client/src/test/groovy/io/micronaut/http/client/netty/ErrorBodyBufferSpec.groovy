package io.micronaut.http.client.netty

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A buffered streaming error body retains only the bytes it carries, not every piece it arrived in.
 */
class ErrorBodyBufferSpec extends Specification {

    def "empty and tiny pieces do not retain their backing buffers"() {
        given:
        def backing = Unpooled.buffer(65536).writeBytes(new byte[65536])
        def buffer = new ErrorBodyBuffer(ByteBufAllocator.DEFAULT, 1000)

        when:
        for (int i = 0; i < 100_000; i++) {
            assert buffer.add(backing.retainedSlice(0, 0))
        }
        for (int i = 0; i < 500; i++) {
            assert buffer.add(backing.retainedSlice(i, 1))
        }

        then:
        backing.refCnt() == 1
        buffer.content().readableBytes() == 500

        cleanup:
        buffer.release()
        backing.release()
    }

    def "a piece past the limit is released and not added"() {
        given:
        def buffer = new ErrorBodyBuffer(ByteBufAllocator.DEFAULT, 5)
        def first = Unpooled.copiedBuffer("abc", StandardCharsets.UTF_8)
        def second = Unpooled.copiedBuffer("def", StandardCharsets.UTF_8)

        expect:
        buffer.add(first)
        buffer.sizeWith(second) == 6
        !buffer.add(second)
        first.refCnt() == 0
        second.refCnt() == 0
        buffer.content().toString(StandardCharsets.UTF_8) == "abc"

        cleanup:
        buffer.release()
    }
}
