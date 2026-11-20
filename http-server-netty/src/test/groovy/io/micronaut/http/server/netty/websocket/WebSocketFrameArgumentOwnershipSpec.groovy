package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPipeline
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * An {@code @OnMessage} handler owns the {@link WebSocketFrame} it receives: it releases it or
 * sends it on. The framework only releases a message the handler never receives.
 */
class WebSocketFrameArgumentOwnershipSpec extends Specification {

    private static final String LARGE = 'x' * 4096

    void "a frame sent on by the handler arrives intact (relay)"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketFrameArgumentOwnershipSpec'])
        RelayServer relay = server.applicationContext.getBean(RelayServer)
        HttpClient httpClient = HttpClient.newHttpClient()
        Collector collector = new Collector()
        URI uri = URI.create("ws://localhost:${server.port}/relay")
        WebSocket receiver = httpClient.newWebSocketBuilder().buildAsync(uri, collector).get(10, TimeUnit.SECONDS)
        new PollingConditions(timeout: 10).eventually {
            assert relay.sessions.size() == 1
        }
        WebSocket sender = httpClient.newWebSocketBuilder().buildAsync(uri, new WebSocket.Listener() {}).get(10, TimeUnit.SECONDS)
        new PollingConditions(timeout: 10).eventually {
            assert relay.sessions.size() == 2
        }

        when:
        sender.sendText(LARGE, true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert relay.sendErrors.isEmpty()
            assert collector.messages == [LARGE]
        }
        relay.sendErrors.isEmpty()

        cleanup:
        sender?.abort()
        receiver?.abort()
        httpClient?.close()
        server?.close()
    }

    void "a frame sent back by the handler arrives intact (echo)"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketFrameArgumentOwnershipSpec'])
        EchoServer echo = server.applicationContext.getBean(EchoServer)
        HttpClient httpClient = HttpClient.newHttpClient()
        Collector collector = new Collector()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}/echo"), collector)
                .get(10, TimeUnit.SECONDS)

        when:
        3.times { ws.sendText(LARGE, true).get(10, TimeUnit.SECONDS) }

        then:
        new PollingConditions(timeout: 10).eventually {
            assert echo.sendErrors.isEmpty()
            assert collector.messages == [LARGE, LARGE, LARGE]
        }

        cleanup:
        ws?.abort()
        httpClient?.close()
        server?.close()
    }

    void "a handler reads the frame after it returned and releases it"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketFrameArgumentOwnershipSpec'])
        LaterReader reader = server.applicationContext.getBean(LaterReader)
        HttpClient httpClient = HttpClient.newHttpClient()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}/later"), new WebSocket.Listener() {})
                .get(10, TimeUnit.SECONDS)

        when:
        ws.sendText(LARGE, true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert reader.results.size() == 1
        }
        reader.results[0] == LARGE
        reader.refCntAfterRelease[0] == 0

        cleanup:
        ws?.abort()
        httpClient?.close()
        server?.close()
    }

    void "a message the handler never receives is released (#path)"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketFrameArgumentOwnershipSpec'])
        PipelineRecorder recorder = server.applicationContext.getBean(PipelineRecorder)
        HttpClient httpClient = HttpClient.newHttpClient()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}$path"), new WebSocket.Listener() {})
                .get(10, TimeUnit.SECONDS)
        List<WebSocketFrame> frames = new CopyOnWriteArrayList<>()
        new PollingConditions(timeout: 10).eventually {
            assert recorder.pipelines.any { it.get(NettyServerWebSocketHandler.ID) != null }
        }
        ChannelPipeline pipeline = recorder.pipelines.find { it.get(NettyServerWebSocketHandler.ID) != null }
        // added on the event loop, so that it sees the next frame
        pipeline.channel().eventLoop().submit({
            pipeline.addBefore(NettyServerWebSocketHandler.ID, 'frame-recorder', recorderHandler(frames))
        } as Runnable).get(10, TimeUnit.SECONDS)
        server.applicationContext.getBean(FailingBinder).fail = true

        when:
        ws.sendText('hello', true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert frames.size() == 1
            // only the reference the test holds is left
            assert frames[0].refCnt() == 1
        }

        cleanup:
        frames?.each { if (it.refCnt() > 0) it.release() }
        ws?.abort()
        httpClient?.close()
        server?.close()

        where:
        path << ['/frame-unbound', '/conversion-error']
    }

    private static ChannelInboundHandlerAdapter recorderHandler(List<WebSocketFrame> frames) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof TextWebSocketFrame) {
                    // hold a reference, so that the pooled buffer is not recycled before the test checks it
                    frames.add(((TextWebSocketFrame) msg).retain())
                }
                ctx.fireChannelRead(msg)
            }
        }
    }

    /**
     * Collects whole text messages.
     */
    static class Collector implements WebSocket.Listener {
        final List<String> messages = new CopyOnWriteArrayList<>()
        private final StringBuilder current = new StringBuilder()

        @Override
        CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            current.append(data)
            if (last) {
                messages.add(current.toString())
                current.setLength(0)
            }
            webSocket.request(1)
            return null
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @ServerWebSocket("/relay")
    static class RelayServer {
        final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>()
        final List<Throwable> sendErrors = new CopyOnWriteArrayList<>()

        @OnOpen
        void onOpen(WebSocketSession session) {
            sessions.add(session)
        }

        @OnMessage
        void onMessage(WebSocketFrame frame, WebSocketSession session) {
            WebSocketSession other = sessions.find { it.id != session.id }
            // no retain: sending the frame consumes the reference the handler owns
            other.sendAsync(frame).whenComplete { v, t -> if (t != null) sendErrors.add(t) }
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @ServerWebSocket("/echo")
    static class EchoServer {
        final List<Throwable> sendErrors = new CopyOnWriteArrayList<>()

        @OnMessage
        void onMessage(WebSocketFrame frame, WebSocketSession session) {
            session.sendAsync(frame).whenComplete { v, t -> if (t != null) sendErrors.add(t) }
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @ServerWebSocket("/later")
    static class LaterReader {
        final List<String> results = new CopyOnWriteArrayList<>()
        final List<Integer> refCntAfterRelease = new CopyOnWriteArrayList<>()

        @OnMessage
        void onMessage(WebSocketFrame frame) {
            // reads the frame after the method returned, then releases it
            CompletableFuture.runAsync({
                try {
                    Thread.sleep(300)
                    results.add(frame.content().toString(StandardCharsets.UTF_8))
                } catch (Throwable t) {
                    results.add(t.toString())
                } finally {
                    try {
                        frame.release()
                    } catch (Throwable ignored) {
                    }
                    refCntAfterRelease.add(frame.refCnt())
                }
            })
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @ServerWebSocket("/frame-unbound")
    static class UnboundFrameServer {
        @OnMessage
        void onMessage(WebSocketFrame frame, FailingValue value) {
        }
    }

    static class FailingValue {
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @ServerWebSocket("/conversion-error")
    static class ConversionErrorServer {
        @OnMessage
        void onMessage(Unconvertible value) {
        }
    }

    static class Unconvertible {
    }

    /**
     * Fails to convert a message body, after the frame content was buffered.
     */
    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @Singleton
    static class FailingConverter implements TypeConverter<ByteBuf, Unconvertible> {
        @Override
        Optional<Unconvertible> convert(ByteBuf object, Class<Unconvertible> targetType, ConversionContext context) {
            throw new IllegalStateException('conversion failed')
        }
    }

    /**
     * Binds when the session opens, and fails once the test arms it, when a message is bound.
     */
    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @Singleton
    static class FailingBinder implements TypedRequestArgumentBinder<FailingValue> {
        volatile boolean fail

        @Override
        Argument<FailingValue> argumentType() {
            return Argument.of(FailingValue)
        }

        @Override
        BindingResult<FailingValue> bind(ArgumentConversionContext<FailingValue> context, HttpRequest<?> source) {
            if (fail) {
                throw new IllegalStateException('binding failed')
            }
            return () -> Optional.of(new FailingValue())
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentOwnershipSpec')
    @Singleton
    static class PipelineRecorder implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
        final List<ChannelPipeline> pipelines = new CopyOnWriteArrayList<>()

        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.bean.register(new Customizer(null))
            return event.bean
        }

        class Customizer implements NettyServerCustomizer {
            final Channel channel

            Customizer(Channel channel) {
                this.channel = channel
            }

            @Override
            NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                return new Customizer(channel)
            }

            @Override
            void onInitialPipelineBuilt() {
                pipelines.add(channel.pipeline())
            }
        }
    }
}
