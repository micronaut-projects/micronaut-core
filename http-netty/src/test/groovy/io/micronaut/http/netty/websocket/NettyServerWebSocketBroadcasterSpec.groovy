package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.GlobalEventExecutor
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.time.Duration

/**
 * A write failure to one receiver must not fail the broadcast (and so the sender's handler).
 */
class NettyServerWebSocketBroadcasterSpec extends Specification {

    static final java.util.concurrent.atomic.AtomicInteger BROKEN_WRITES = new java.util.concurrent.atomic.AtomicInteger()

    void "a write failure to one receiver does not fail broadcast"() {
        given:
        def setup = setupGroup()

        when:
        def result = Flux.from(setup.broadcaster.broadcast("hello", MediaType.TEXT_PLAIN_TYPE, s -> true))
                .collectList().block(Duration.ofSeconds(10))

        then:
        noExceptionThrown()
        result == ["hello"]
        receivedText(setup.healthy) == "hello"
        BROKEN_WRITES.get() > 0

        cleanup:
        setup.group.close()
    }

    void "a write failure to one receiver does not fail broadcastSync"() {
        given:
        def setup = setupGroup()

        when:
        setup.broadcaster.broadcastSync("hello", MediaType.TEXT_PLAIN_TYPE, s -> true)

        then:
        noExceptionThrown()
        receivedText(setup.healthy) == "hello"
        BROKEN_WRITES.get() > 0

        cleanup:
        setup.group.close()
    }

    private static String receivedText(EmbeddedChannel ch) {
        TextWebSocketFrame f = ch.readOutbound()
        try {
            return f.text()
        } finally {
            f.release()
        }
    }

    private Map setupGroup() {
        ChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
        EmbeddedChannel healthy = new EmbeddedChannel(io.netty.channel.DefaultChannelId.newInstance())
        EmbeddedChannel broken = new EmbeddedChannel(io.netty.channel.DefaultChannelId.newInstance(), new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg)
                BROKEN_WRITES.incrementAndGet()
                // e.g. SslClosedEngineException after close_notify, or "Broken pipe"
                promise.setFailure(new IOException("Broken pipe"))
            }
        })
        session(healthy)
        session(broken)
        group.add(healthy)
        group.add(broken)
        def repo = new WebSocketSessionRepository() {
            @Override
            void addChannel(Channel channel) { group.add(channel) }

            @Override
            void removeChannel(Channel channel) { group.remove(channel) }

            @Override
            ChannelGroup getChannelGroup() { group }
        }
        def broadcaster = new NettyServerWebSocketBroadcaster(new WebSocketMessageEncoder(null), repo)
        return [group: group, healthy: healthy, broken: broken, broadcaster: broadcaster]
    }

    private static NettyWebSocketSession session(Channel ch) {
        new NettyWebSocketSession(UUID.randomUUID().toString(), ch, HttpRequest.GET("/chat"), null, "13", false) {}
    }
}
