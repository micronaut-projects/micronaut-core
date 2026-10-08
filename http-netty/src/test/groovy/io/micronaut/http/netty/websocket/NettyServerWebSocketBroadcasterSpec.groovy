package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.websocket.exceptions.WebSocketSessionException
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * A broadcast whose filter throws.
 */
class BroadcastFilterFailureSpec extends Specification {

    void 'a filter that throws fails the broadcast and the frame is released'() {
        given:
        EmbeddedChannel channel = new EmbeddedChannel()
        new NettyWebSocketSession('id', channel, HttpRequest.GET('/'), null, null, '13', false) {
        }
        ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
        channels.add(channel)
        NettyServerWebSocketBroadcaster broadcaster = new NettyServerWebSocketBroadcaster(new WebSocketMessageEncoder(null, null), new WebSocketSessionRepository() {
            @Override
            void addChannel(Channel ch) {
                channels.add(ch)
            }

            @Override
            void removeChannel(Channel ch) {
                channels.remove(ch)
            }

            @Override
            ChannelGroup getChannelGroup() {
                return channels
            }
        })
        ByteBuf content = Unpooled.buffer().writeBytes([1, 2, 3] as byte[])

        when:
        broadcaster.broadcastAsync(content, MediaType.APPLICATION_OCTET_STREAM_TYPE, { throw new IllegalStateException('bad filter') }).get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketSessionException
        e.cause.cause.message == 'bad filter'
        content.refCnt() == 0

        cleanup:
        channel.finishAndReleaseAll()
    }
}
