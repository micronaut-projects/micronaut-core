package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.websocket.exceptions.WebSocketSessionException
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.EventLoopGroup
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.local.LocalAddress
import io.netty.channel.local.LocalChannel
import io.netty.channel.local.LocalServerChannel
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.GlobalEventExecutor
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The blocking sends of a session and of the broadcaster on an event loop thread.
 */
class SyncSendOnEventLoopSpec extends Specification {

    @Shared
    @AutoCleanup('shutdownGracefully')
    EventLoopGroup group = new DefaultEventLoopGroup(2)

    Channel server
    Channel client
    HeldWrites held = new HeldWrites()
    NettyWebSocketSession session
    ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    NettyServerWebSocketBroadcaster broadcaster

    def setup() {
        LocalAddress address = new LocalAddress(UUID.randomUUID().toString())
        server = new ServerBootstrap().group(group).channel(LocalServerChannel)
            .childHandler(new ChannelInboundHandlerAdapter() {
                @Override
                void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ReferenceCountUtil.release(msg)
                }
            }).bind(address).sync().channel()
        client = new Bootstrap().group(group).channel(LocalChannel).handler(held).connect(address).sync().channel()
        session = new NettyWebSocketSession('id', client, HttpRequest.GET('/'), null, null, '13', false) {
        }
        channels.add(client)
        broadcaster = new NettyServerWebSocketBroadcaster(new WebSocketMessageEncoder(null, null), new WebSocketSessionRepository() {
            @Override
            void addChannel(Channel channel) {
                channels.add(channel)
            }

            @Override
            void removeChannel(Channel channel) {
                channels.remove(channel)
            }

            @Override
            ChannelGroup getChannelGroup() {
                return channels
            }
        })
    }

    def cleanup() {
        held.release()
        client?.close()?.sync()
        server?.close()?.sync()
    }

    void 'sendSync on the event loop does not wait for a write that is not done'() {
        when:
        onEventLoop { session.sendSync('held', MediaType.TEXT_PLAIN_TYPE) }

        then: 'it returned while the write is held'
        held.pending() == 1
    }

    void 'sendSync on the event loop fails when the write failed at once'() {
        given:
        held.failure = new IOException('boom')

        when:
        onEventLoop { session.sendSync('failing', MediaType.TEXT_PLAIN_TYPE) }

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketSessionException
        e.cause.message.contains('boom')
    }

    void 'broadcastSync on the event loop does not wait for a write that is not done'() {
        when:
        onEventLoop { broadcaster.broadcastSync('held', MediaType.TEXT_PLAIN_TYPE, { true }) }

        then:
        held.pending() == 1
    }

    void 'broadcastSync off the event loop waits for the write'() {
        when:
        CompletableFuture<Void> done = CompletableFuture.runAsync { broadcaster.broadcastSync('held', MediaType.TEXT_PLAIN_TYPE, { true }) }
        done.get(300, TimeUnit.MILLISECONDS)

        then:
        thrown(TimeoutException)

        when:
        held.release()

        then:
        done.get(10, TimeUnit.SECONDS) == null
    }

    void 'a filter that throws fails the broadcast and the frame is released'() {
        given:
        ByteBuf content = Unpooled.buffer().writeBytes([1, 2, 3] as byte[])

        when:
        broadcaster.broadcastAsync(content, MediaType.APPLICATION_OCTET_STREAM_TYPE, { throw new IllegalStateException('bad filter') }).get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketSessionException
        e.cause.cause.message == 'bad filter'
        content.refCnt() == 0
    }

    private void onEventLoop(Closure<?> work) {
        client.eventLoop().submit(work as Callable<Object>).get(5, TimeUnit.SECONDS)
    }

    /**
     * Holds the writes, or fails them, instead of writing them.
     */
    @ChannelHandler.Sharable
    static class HeldWrites extends ChannelOutboundHandlerAdapter {
        final List<ChannelPromise> promises = Collections.synchronizedList([])
        volatile Throwable failure

        @Override
        void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg)
            if (failure != null) {
                promise.setFailure(failure)
            } else {
                promises.add(promise)
            }
        }

        int pending() {
            return promises.size()
        }

        void release() {
            synchronized (promises) {
                promises.each { it.trySuccess() }
                promises.clear()
            }
        }
    }
}
