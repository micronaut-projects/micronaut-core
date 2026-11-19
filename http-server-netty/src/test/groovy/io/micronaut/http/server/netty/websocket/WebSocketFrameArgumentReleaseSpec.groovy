package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A {@link WebSocketFrame} argument of an {@code @OnMessage} handler is released once the
 * handler completed, and when its arguments cannot be bound.
 */
class WebSocketFrameArgumentReleaseSpec extends Specification {

    void "a WebSocketFrame argument is released (#path)"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketFrameArgumentReleaseSpec'])
        PipelineRecorder recorder = server.applicationContext.getBean(PipelineRecorder)
        FrameServer bean = server.applicationContext.getBean(FrameServer)
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
            if (path == '/frame') {
                assert bean.received.size() == 1
            }
            // only the reference the test holds is left
            assert frames[0].refCnt() == 1
        }

        cleanup:
        frames?.each { if (it.refCnt() > 0) it.release() }
        ws?.abort()
        httpClient?.close()
        server?.close()

        where:
        path << ['/frame', '/frame-unbound']
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

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentReleaseSpec')
    @ServerWebSocket("/frame")
    static class FrameServer {
        final List<String> received = new CopyOnWriteArrayList<>()

        @OnMessage
        void onMessage(WebSocketFrame frame) {
            received.add(((TextWebSocketFrame) frame).text())
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentReleaseSpec')
    @ServerWebSocket("/frame-unbound")
    static class UnboundFrameServer {
        @OnMessage
        void onMessage(WebSocketFrame frame, FailingValue value) {
        }
    }

    static class FailingValue {
    }

    /**
     * Binds when the session opens, and fails once the test arms it, when a message is bound.
     */
    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentReleaseSpec')
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

    @Requires(property = 'spec.name', value = 'WebSocketFrameArgumentReleaseSpec')
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
