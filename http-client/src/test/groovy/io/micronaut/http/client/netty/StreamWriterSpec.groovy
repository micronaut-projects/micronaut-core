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

    def "bytes written while the channel is not writable are acknowledged once it is writable again"() {
        given:
        def channel = new EmbeddedChannel()
        def upstream = new RecordingUpstream()
        def writer = new StreamWriter(channel, streamingBody(channel, upstream), { throw new AssertionError(it) })
        writer.startWriting()
        def outboundBuffer = channel.unsafe().outboundBuffer()

        when: "a chunk is written while the channel is writable"
        writer.add(readBuffer("foo"))
        then: "it is acknowledged immediately"
        upstream.consumed == 3

        when: "a chunk is written while the channel is not writable"
        outboundBuffer.setUserDefinedWritability(1, false)
        writer.add(readBuffer("barbaz"))
        then: "the acknowledgement is held back to apply backpressure"
        !channel.isWritable()
        upstream.consumed == 3

        when: "the channel becomes writable again"
        outboundBuffer.setUserDefinedWritability(1, true)
        writer.channelWritabilityChanged()
        then: "the held back bytes are acknowledged"
        upstream.consumed == 9

        when: "writability changes again with nothing held back"
        writer.channelWritabilityChanged()
        then:
        upstream.consumed == 9

        cleanup:
        writer.cancel()
        channel.finishAndReleaseAll()
    }

    def "writability changes after cancel are ignored"() {
        given:
        def channel = new EmbeddedChannel()
        def upstream = new RecordingUpstream()
        def writer = new StreamWriter(channel, streamingBody(channel, upstream), { throw new AssertionError(it) })
        writer.startWriting()
        def outboundBuffer = channel.unsafe().outboundBuffer()
        outboundBuffer.setUserDefinedWritability(1, false)
        writer.add(readBuffer("foo"))

        when: "the request is done before the channel became writable again"
        writer.cancel()
        def consumedAtCancel = upstream.consumed
        outboundBuffer.setUserDefinedWritability(1, true)
        writer.channelWritabilityChanged()

        then: "the held back bytes are not acknowledged by the writer anymore, the connection may serve another request"
        upstream.consumed == consumedAtCancel

        cleanup:
        channel.finishAndReleaseAll()
    }

    private static readBuffer(String s) {
        NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(Unpooled.copiedBuffer(s, StandardCharsets.UTF_8))
    }

    private static StreamingNettyByteBody streamingBody(EmbeddedChannel channel, BufferConsumer.Upstream upstream = new RecordingUpstream()) {
        def sharedBuffer = new NettyByteBodyFactory(channel).createStreamingBuffer(BodySizeLimits.UNLIMITED, upstream)
        return new StreamingNettyByteBody(sharedBuffer)
    }

    private static final class RecordingUpstream implements BufferConsumer.Upstream {
        long consumed

        @Override
        void onBytesConsumed(long bytesConsumed) {
            // record the acknowledged bytes, the tests check the backpressure signal
            consumed += bytesConsumed
        }
    }
}
