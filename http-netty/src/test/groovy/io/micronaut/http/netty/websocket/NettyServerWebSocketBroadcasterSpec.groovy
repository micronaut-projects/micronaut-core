package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.exceptions.WebSocketSessionException
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.DefaultChannelId
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.GlobalEventExecutor
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate

class NettyServerWebSocketBroadcasterSpec extends Specification {

    static final AtomicInteger BROKEN_WRITES = new AtomicInteger()

    EmbeddedChannel channel = new EmbeddedChannel()
    ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    WebSocketMessageEncoder encoder = new WebSocketMessageEncoder(null, null)
    WebSocketSessionRepository repository = new WebSocketSessionRepository() {
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
    }

    def setup() {
        new NettyWebSocketSession('id', channel, HttpRequest.GET('/'), null, null, '13', false) {
        }
        channels.add(channel)
    }

    def cleanup() {
        channel.finishAndReleaseAll()
    }

    void 'broadcast returns a Flux that broadcasts for each subscription, as before'() {
        given:
        def broadcaster = new NettyServerWebSocketBroadcaster(encoder, repository)

        when:
        Publisher<String> publisher = broadcaster.broadcast('hello', MediaType.TEXT_PLAIN_TYPE, { true })

        then:
        publisher instanceof Flux
        channel.readOutbound() == null

        when:
        def emitted = Flux.from(publisher).collectList().block()
        channel.runPendingTasks()

        then:
        emitted == ['hello']
        texts() == ['hello']
    }

    void 'the broadcaster broadcasts asynchronously without going through broadcast'() {
        given:
        def broadcaster = new NettyServerWebSocketBroadcaster(encoder, repository)

        when:
        def sent = broadcaster.broadcastAsync('async', MediaType.TEXT_PLAIN_TYPE, { true }).get(10, TimeUnit.SECONDS)
        channel.runPendingTasks()

        then:
        sent == 'async'
        texts() == ['async']
    }

    void 'broadcastAsync of a subclass that overrides broadcast goes through it, as before'() {
        given:
        def broadcaster = new OverridingBroadcaster(encoder, repository)

        when:
        def sent = broadcaster.broadcastAsync('through broadcast', MediaType.TEXT_PLAIN_TYPE, { true }).get(10, TimeUnit.SECONDS)
        channel.runPendingTasks()

        then:
        sent == 'through broadcast'
        broadcaster.broadcasts == ['through broadcast']
        texts() == ['through broadcast']
    }

    void 'a subclass whose broadcastAsync calls super.broadcast does not recurse'() {
        given:
        def broadcaster = new AsyncOverridingBroadcaster(encoder, repository)

        when:
        def sent = broadcaster.broadcastAsync('once', MediaType.TEXT_PLAIN_TYPE, { true }).get(10, TimeUnit.SECONDS)
        channel.runPendingTasks()

        then:
        sent == 'once'
        texts() == ['once']
    }

    void 'a filter that throws fails the broadcast and the frame is released'() {
        given:
        def broadcaster = new NettyServerWebSocketBroadcaster(encoder, repository)
        ByteBuf content = Unpooled.buffer().writeBytes([1, 2, 3] as byte[])

        when:
        broadcaster.broadcastAsync(content, MediaType.APPLICATION_OCTET_STREAM_TYPE, { throw new IllegalStateException('bad filter') }).get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketSessionException
        e.cause.cause.message == 'bad filter'
        content.refCnt() == 0
    }

    // a write failure to one receiver must not fail the broadcast (and so the sender's handler)
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
        EmbeddedChannel healthy = new EmbeddedChannel(DefaultChannelId.newInstance())
        EmbeddedChannel broken = new EmbeddedChannel(DefaultChannelId.newInstance(), new ChannelOutboundHandlerAdapter() {
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
        new NettyWebSocketSession(UUID.randomUUID().toString(), ch, HttpRequest.GET("/chat"), null, null, "13", false) {}
    }

    private List<String> texts() {
        List<String> texts = []
        Object message
        while ((message = channel.readOutbound()) != null) {
            texts.add(((TextWebSocketFrame) message).text())
            ((TextWebSocketFrame) message).release()
        }
        return texts
    }

    static class OverridingBroadcaster extends NettyServerWebSocketBroadcaster {
        final List<Object> broadcasts = new CopyOnWriteArrayList<>()

        OverridingBroadcaster(WebSocketMessageEncoder encoder, WebSocketSessionRepository repository) {
            super(encoder, repository)
        }

        @Override
        <T> Publisher<T> broadcast(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
            broadcasts.add(message)
            return super.broadcast(message, mediaType, filter)
        }
    }

    static class AsyncOverridingBroadcaster extends NettyServerWebSocketBroadcaster {
        AsyncOverridingBroadcaster(WebSocketMessageEncoder encoder, WebSocketSessionRepository repository) {
            super(encoder, repository)
        }

        @Override
        <T> CompletableFuture<T> broadcastAsync(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
            return Flux.from(super.broadcast(message, mediaType, filter)).next().toFuture()
        }
    }
}
