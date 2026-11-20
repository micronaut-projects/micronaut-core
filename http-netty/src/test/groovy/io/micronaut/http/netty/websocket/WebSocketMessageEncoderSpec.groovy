package io.micronaut.http.netty.websocket

import io.micronaut.http.MediaType
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.UnpooledByteBufAllocator
import java.nio.charset.StandardCharsets
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import spock.lang.Issue
import spock.lang.Specification

class WebSocketMessageEncoderSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2842")
    void "test a gstring is encoded correctly"() {
        WebSocketMessageEncoder encoder = new WebSocketMessageEncoder(null)

        when:
        WebSocketFrame frame = encoder.encodeMessage("${1+1}", MediaType.TEXT_PLAIN_TYPE)

        then:
        frame instanceof TextWebSocketFrame
        new String(ByteBufUtil.getBytes(frame.content())) == "2"
    }

    void "text is written into a buffer of the given allocator"() {
        given:
        WebSocketMessageEncoder encoder = new WebSocketMessageEncoder(null)
        ByteBufAllocator allocator = new UnpooledByteBufAllocator(true)

        when:
        WebSocketFrame frame = encoder.encodeMessage(text, MediaType.TEXT_PLAIN_TYPE, allocator)

        then:
        frame instanceof TextWebSocketFrame
        frame.content().alloc().is(allocator)
        frame.content().readableBytes() == text.getBytes(StandardCharsets.UTF_8).length
        frame.content().toString(StandardCharsets.UTF_8) == text

        cleanup:
        frame?.release()

        where:
        text << ['hello', 'gr\u00fc\u00dfe \u20ac \ud83d\ude00', '']
    }

    void "a byte array is a binary frame"() {
        WebSocketMessageEncoder encoder = new WebSocketMessageEncoder(null)

        when:
        WebSocketFrame frame = encoder.encodeMessage([1, 2] as byte[], MediaType.TEXT_PLAIN_TYPE)

        then:
        frame instanceof io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
        ByteBufUtil.getBytes(frame.content()) == [1, 2] as byte[]
    }
}
