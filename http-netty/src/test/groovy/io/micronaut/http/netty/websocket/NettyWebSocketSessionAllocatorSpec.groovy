package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.local.LocalAddress
import io.netty.channel.local.LocalChannel
import io.netty.channel.local.LocalServerChannel
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A session encodes text sent on the event loop of its channel into a buffer of the allocator of
 * that channel, and text sent from another thread as before.
 */
class NettyWebSocketSessionAllocatorSpec extends Specification {

    DefaultEventLoopGroup group = new DefaultEventLoopGroup(1)
    ByteBufAllocator channelAllocator = new UnpooledByteBufAllocator(false)
    List<ByteBufAllocator> written = new CopyOnWriteArrayList<>()
    Channel server
    Channel client

    def setup() {
        LocalAddress address = new LocalAddress(UUID.randomUUID().toString())
        server = new ServerBootstrap().group(group).channel(LocalServerChannel)
                .childHandler(new ChannelInboundHandlerAdapter())
                .bind(address).sync().channel()
        client = new Bootstrap().group(group).channel(LocalChannel)
                .option(ChannelOption.ALLOCATOR, channelAllocator)
                .handler(new ChannelOutboundHandlerAdapter() {
                    @Override
                    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        written.add(((TextWebSocketFrame) msg).content().alloc())
                        ReferenceCountUtil.release(msg)
                        promise.setSuccess()
                    }
                })
                .connect(address).sync().channel()
    }

    def cleanup() {
        client?.close()?.sync()
        server?.close()?.sync()
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS)
    }

    void "a send on the event loop of the channel encodes into the allocator of the channel"() {
        given:
        NettyWebSocketSession session = session(client)

        when:
        client.eventLoop().submit({ session.sendAsync("hello", MediaType.TEXT_PLAIN_TYPE) } as Callable)
                .get(10, TimeUnit.SECONDS)
                .get(10, TimeUnit.SECONDS)

        then:
        written == [channelAllocator]
    }

    void "a send from another thread keeps the unpooled heap copy"() {
        given:
        NettyWebSocketSession session = session(client)

        when:
        session.sendAsync("hello", MediaType.TEXT_PLAIN_TYPE).get(10, TimeUnit.SECONDS)

        then:
        written.size() == 1
        !written[0].is(channelAllocator)
    }

    private static NettyWebSocketSession session(Channel ch) {
        new NettyWebSocketSession(UUID.randomUUID().toString(), ch, HttpRequest.GET("/chat"), null, null, "13", false) {}
    }
}
