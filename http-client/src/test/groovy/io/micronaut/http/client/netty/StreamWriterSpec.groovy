package io.micronaut.http.client.netty

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpContent
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class StreamWriterSpec extends Specification {
    def "writes body chunks while the request is active"() {
        given:
        def channel = new EmbeddedChannel()
        def errors = []
        def writer = new StreamWriter(channel, streamingBody(channel), { errors.add(it) })
        writer.startWriting()
        def buf = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)

        when:
        writer.add(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(buf))
        writer.complete()

        then:
        HttpContent content = channel.readOutbound()
        content.content().toString(StandardCharsets.UTF_8) == "foo"
        channel.readOutbound() != null
        writer.isCompleted()
        errors.isEmpty()

        cleanup:
        content?.release()
        writer.cancel()
        channel.finishAndReleaseAll()
    }

    def "writes arriving after cancel are discarded"() {
        // add0/complete0 can be queued on the event loop when the body emits from another
        // thread. When the request finishes first, the writer is cancelled and the connection
        // may already serve the next request, so the queued writes must not reach the channel.
        given:
        def channel = new EmbeddedChannel()
        def errors = []
        def writer = new StreamWriter(channel, streamingBody(channel), { errors.add(it) })
        writer.startWriting()
        def buf = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)

        when:
        writer.cancel()
        writer.add(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(buf))
        writer.complete()
        writer.error(new RuntimeException("late error"))

        then:
        channel.outboundMessages().isEmpty()
        buf.refCnt() == 0
        !writer.isCompleted()
        errors.isEmpty()

        cleanup:
        channel.finishAndReleaseAll()
    }

    private static StreamingNettyByteBody streamingBody(EmbeddedChannel channel) {
        def sharedBuffer = new NettyByteBodyFactory(channel).createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }
        })
        return new StreamingNettyByteBody(sharedBuffer)
    }
}
