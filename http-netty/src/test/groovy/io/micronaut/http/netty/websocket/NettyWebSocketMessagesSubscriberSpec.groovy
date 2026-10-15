package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.EventLoopGroup
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.local.LocalAddress
import io.netty.channel.local.LocalChannel
import io.netty.channel.local.LocalServerChannel
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The writes of {@link NettyWebSocketSession#sendAllAsync}, over a local channel.
 */
class NettyWebSocketMessagesSubscriberSpec extends Specification {

    @Shared
    @AutoCleanup('shutdownGracefully')
    EventLoopGroup group = new DefaultEventLoopGroup(2)

    Channel server
    Channel client
    FlushCounter flushes = new FlushCounter()
    List<String> received = new CopyOnWriteArrayList<>()

    def setup() {
        LocalAddress address = new LocalAddress(UUID.randomUUID().toString())
        server = new ServerBootstrap().group(group).channel(LocalServerChannel)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        void channelRead(ChannelHandlerContext ctx, Object msg) {
                            received.add(((TextWebSocketFrame) msg).text())
                            ((TextWebSocketFrame) msg).release()
                        }
                    })
                }
            }).bind(address).sync().channel()
        client = new Bootstrap().group(group).channel(LocalChannel).handler(flushes).connect(address).sync().channel()
    }

    def cleanup() {
        client?.close()?.sync()
        server?.close()?.sync()
    }

    void 'the messages written while the channel is writable share a flush'() {
        given:
        NettyWebSocketSession session = session(client)

        when: 'a publisher that emits as it is asked, on the event loop'
        CompletableFuture<Boolean> sent = client.eventLoop().submit({
            session.sendAllAsync(Flux.range(0, 100).map { "m" + it }, MediaType.TEXT_PLAIN_TYPE)
        } as java.util.concurrent.Callable<CompletableFuture<Boolean>>).get(10, TimeUnit.SECONDS)

        then:
        sent.get(10, TimeUnit.SECONDS)
        new PollingConditions(timeout: 10).eventually {
            received == (0..<100).collect { "m" + it }
        }
        flushes.count.get() <= 2
    }

    void 'the next message is requested once the written ones are out when the channel is not writable'() {
        given:
        client.config().setWriteBufferWaterMark(new WriteBufferWaterMark(0, 1))
        NettyWebSocketSession session = session(client)
        AtomicInteger requested = new AtomicInteger()

        when:
        CompletableFuture<Boolean> sent = session.sendAllAsync(
            Flux.range(0, 20).map { "m" + it }.doOnRequest { requested.addAndGet((int) it) }, MediaType.TEXT_PLAIN_TYPE)

        then:
        sent.get(10, TimeUnit.SECONDS)
        new PollingConditions(timeout: 10).eventually {
            received == (0..<20).collect { "m" + it }
        }
        // one message at a time, each one flushed
        flushes.count.get() >= 20
        requested.get() <= 21
    }

    void 'a publisher that emits from another thread sends in order'() {
        given:
        NettyWebSocketSession session = session(client)

        when:
        CompletableFuture<Boolean> sent = session.sendAllAsync(
            Flux.range(0, 200).map { "m" + it }.publishOn(reactor.core.scheduler.Schedulers.parallel(), 1), MediaType.TEXT_PLAIN_TYPE)

        then:
        sent.get(10, TimeUnit.SECONDS)
        new PollingConditions(timeout: 10).eventually {
            received == (0..<200).collect { "m" + it }
        }
    }

    void 'cancelling the future cancels the publisher'() {
        given:
        NettyWebSocketSession session = session(client)
        CompletableFuture<Boolean> cancelled = new CompletableFuture<>()

        when:
        CompletableFuture<Boolean> sent = session.sendAllAsync(Flux.never().doOnCancel { cancelled.complete(true) }, MediaType.TEXT_PLAIN_TYPE)
        sent.cancel(true)

        then:
        cancelled.get(10, TimeUnit.SECONDS)
    }

    void 'closing the channel completes the future with false and cancels the publisher'() {
        given:
        NettyWebSocketSession session = session(client)
        CompletableFuture<Boolean> cancelled = new CompletableFuture<>()

        when:
        CompletableFuture<Boolean> sent = session.sendAllAsync(Flux.never().doOnCancel { cancelled.complete(true) }, MediaType.TEXT_PLAIN_TYPE)
        client.close().sync()

        then:
        sent.get(10, TimeUnit.SECONDS) == false
        cancelled.get(10, TimeUnit.SECONDS)
    }

    private static NettyWebSocketSession session(Channel channel) {
        return new NettyWebSocketSession('id', channel, HttpRequest.GET('/'), null, null, '13', false) {
        }
    }

    @io.netty.channel.ChannelHandler.Sharable
    static class FlushCounter extends ChannelOutboundHandlerAdapter {
        final AtomicInteger count = new AtomicInteger()

        @Override
        void flush(ChannelHandlerContext ctx) throws Exception {
            count.incrementAndGet()
            super.flush(ctx)
        }
    }
}
