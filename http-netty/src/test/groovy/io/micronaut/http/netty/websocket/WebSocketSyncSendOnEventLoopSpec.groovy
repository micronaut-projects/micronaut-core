package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.DefaultChannelId
import io.netty.channel.DefaultEventLoop
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.GlobalEventExecutor
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The blocking send methods must not wait on an event loop for a write that is still pending: the
 * write may only complete once that event loop (or one waiting for it) makes progress.
 */
class WebSocketSyncSendOnEventLoopSpec extends Specification {

    List<ChannelPromise> pending = new CopyOnWriteArrayList<>()
    DefaultEventLoop loop = new DefaultEventLoop()
    ChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    def cleanup() {
        pending.each { it.trySuccess() }
        group.close()
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS)
    }

    void "broadcastSync on an event loop does not wait for a pending write"() {
        given:
        EmbeddedChannel channel = stalledChannel()
        session(channel)
        group.add(channel)
        def broadcaster = new NettyServerWebSocketBroadcaster(new WebSocketMessageEncoder(null), repository())

        when:
        loop.submit({ broadcaster.broadcastSync("hello", MediaType.TEXT_PLAIN_TYPE, s -> true) } as Runnable)
                .get(10, TimeUnit.SECONDS)

        then:
        noExceptionThrown()
        pending.size() == 1
    }

    void "sendSync on an event loop does not wait for a pending write"() {
        given:
        EmbeddedChannel channel = stalledChannel()
        NettyWebSocketSession session = session(channel)

        when:
        loop.submit({ session.sendSync("hello", MediaType.TEXT_PLAIN_TYPE) } as Runnable)
                .get(10, TimeUnit.SECONDS)

        then:
        noExceptionThrown()
        pending.size() == 1
    }

    private EmbeddedChannel stalledChannel() {
        // a peer that does not read: the write never completes
        new EmbeddedChannel(DefaultChannelId.newInstance(), new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg)
                pending.add(promise)
            }
        })
    }

    private WebSocketSessionRepository repository() {
        new WebSocketSessionRepository() {
            @Override
            void addChannel(Channel channel) { group.add(channel) }

            @Override
            void removeChannel(Channel channel) { group.remove(channel) }

            @Override
            ChannelGroup getChannelGroup() { group }
        }
    }

    private static NettyWebSocketSession session(Channel ch) {
        new NettyWebSocketSession(UUID.randomUUID().toString(), ch, HttpRequest.GET("/chat"), null, null, "13", false) {}
    }
}
