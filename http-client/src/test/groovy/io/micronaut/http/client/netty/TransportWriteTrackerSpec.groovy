package io.micronaut.http.client.netty

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import spock.lang.Specification

import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets

class TransportWriteTrackerSpec extends Specification {
    def "a write is handed to the transport by the flush that follows it"() {
        given:
        def channel = new EmbeddedChannel()
        def tracker = new TransportWriteTracker()
        channel.pipeline().addFirst(TransportWriteTracker.NAME, tracker)

        when:
        long mark = tracker.mark()
        def promise = channel.newPromise()
        channel.write(Unpooled.copiedBuffer("POST / HTTP/1.1\r\n", StandardCharsets.US_ASCII), promise)

        then:
        !tracker.flushedSince(mark)
        !promise.done

        when:
        channel.flush()

        then:
        tracker.flushedSince(mark)
        promise.success
        !tracker.flushedSince(tracker.mark())

        cleanup:
        channel.finishAndReleaseAll()
    }

    def "a write rejected by a closed connection is not handed to the transport"() {
        given:
        def channel = new EmbeddedChannel()
        def tracker = new TransportWriteTracker()
        channel.pipeline().addFirst(TransportWriteTracker.NAME, tracker)
        channel.close().sync()

        when:
        long mark = tracker.mark()
        def promise = channel.newPromise()
        channel.writeAndFlush(Unpooled.copiedBuffer("POST / HTTP/1.1\r\n", StandardCharsets.US_ASCII), promise)

        then:
        promise.done
        promise.cause() instanceof ClosedChannelException
        !tracker.flushedSince(mark)
    }

    def "the tracker is found on the connection of a channel"() {
        given:
        def channel = new EmbeddedChannel()

        expect:
        TransportWriteTracker.find(channel) == null

        when:
        def tracker = new TransportWriteTracker()
        channel.pipeline().addFirst(TransportWriteTracker.NAME, tracker)

        then:
        TransportWriteTracker.find(channel).is(tracker)

        cleanup:
        channel.finishAndReleaseAll()
    }
}
