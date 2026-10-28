package io.micronaut.http.server.codec

import io.micronaut.buffer.netty.NettyByteBufferFactory
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.sse.Event
import io.netty.buffer.ByteBuf
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

class TextStreamCodecSpec extends Specification {

    @Unroll
    void "test encode event"() {
        given:
        TextStreamCodec codec = new TextStreamCodec(StandardCharsets.UTF_8, NettyByteBufferFactory.DEFAULT, () -> {
            throw new UnsupportedOperationException()
        }, null)

        when:
        ByteBuffer<ByteBuf> buffer = codec.encode(event, NettyByteBufferFactory.DEFAULT)
        String encoded = buffer.toString(StandardCharsets.UTF_8)
        buffer.asNativeBuffer().release()

        then:
        encoded == expected

        where:
        event                                          | expected
        Event.of("a")                                  | "data: a\n\n"
        Event.of("a\nb")                               | "data: a\ndata: b\n\n"
        Event.of("a\rb")                               | "data: a\ndata: b\n\n"
        Event.of("a\r\nb")                             | "data: a\ndata: b\n\n"
        Event.of("a\r\rid: x\n\ndata: b")              | "data: a\ndata: \ndata: id: x\ndata: \ndata: data: b\n\n"
        Event.of("a\n")                                | "data: a\ndata: \n\n"
        Event.of("")                                   | "\n"
        Event.of("").comment("keep-alive")             | ": keep-alive\n\n"
        Event.of("\n")                                 | "data: \ndata: \n\n"
        Event.of("a").id("1\r\nevent: x\n\0")          | "id: 1event: x\ndata: a\n\n"
        Event.of("a").name("foo\rdata: x\n\0")         | "event: foodata: x\0\ndata: a\n\n"
        Event.of("a").comment("c1\nid: x\r\nc3\r")     | ": c1\n: id: x\n: c3\n: \ndata: a\n\n"
    }
}
