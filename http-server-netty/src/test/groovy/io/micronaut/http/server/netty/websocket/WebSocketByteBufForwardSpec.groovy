package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A {@code ByteBuf} argument is released by the framework exactly once, when the handler
 * completes. A handler that sends it on calls {@code retain()}, as the write consumes a reference.
 */
class WebSocketByteBufForwardSpec extends Specification {

    private static final byte[] LARGE = ('x' * 4096).bytes

    void "echo of a payload over 1 KiB with sendAsync"() {
        given:
        EmbeddedServer server = run()
        PipelineRecorder recorder = server.applicationContext.getBean(PipelineRecorder)
        HttpClient httpClient = HttpClient.newHttpClient()
        Collector collector = new Collector()
        WebSocket ws = connect(httpClient, server, '/bb-echo', collector)
        List<BinaryWebSocketFrame> frames = new CopyOnWriteArrayList<>()
        List<ChannelPipeline> pipelines = webSocketPipelines(recorder, 1)
        record(pipelines[0], frames)

        when:
        ws.sendBinary(ByteBuffer.wrap(LARGE), true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert collector.messages.size() == 1
        }
        collector.messages[0] == LARGE
        settle(pipelines)
        frames.size() == 1
        // only the reference the test holds is left
        frames[0].refCnt() == 1

        cleanup:
        frames?.each { if (it.refCnt() > 0) it.release() }
        ws?.abort()
        httpClient?.close()
        server?.close()
    }

    void "forward to a session on another event loop"() {
        given:
        EmbeddedServer server = run()
        PipelineRecorder recorder = server.applicationContext.getBean(PipelineRecorder)
        HttpClient httpClient = HttpClient.newHttpClient()
        Collector collector = new Collector()
        List<WebSocket> sockets = []
        sockets << connect(httpClient, server, '/bb-relay', collector)
        List<ChannelPipeline> pipelines = webSocketPipelines(recorder, 1)
        ChannelPipeline receiver = pipelines[0]
        ChannelPipeline sender = null
        WebSocket senderSocket = null
        for (int i = 0; i < 16 && sender == null; i++) {
            WebSocket candidate = connect(httpClient, server, '/bb-relay', new WebSocket.Listener() {})
            sockets << candidate
            pipelines = webSocketPipelines(recorder, sockets.size())
            if (pipelines.last().channel().eventLoop() != receiver.channel().eventLoop()) {
                sender = pipelines.last()
                senderSocket = candidate
            }
        }
        List<BinaryWebSocketFrame> frames = new CopyOnWriteArrayList<>()
        record(sender, frames)

        expect:
        sender.channel().eventLoop() != receiver.channel().eventLoop()

        when:
        senderSocket.sendBinary(ByteBuffer.wrap(LARGE), true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert collector.messages.size() == 1
        }
        collector.messages[0] == LARGE
        settle([sender, receiver, sender])
        frames.size() == 1
        frames[0].refCnt() == 1

        cleanup:
        frames?.each { if (it.refCnt() > 0) it.release() }
        sockets.each { it.abort() }
        httpClient?.close()
        server?.close()
    }

    void "broadcastAsync of the received ByteBuf"() {
        given:
        EmbeddedServer server = run()
        PipelineRecorder recorder = server.applicationContext.getBean(PipelineRecorder)
        HttpClient httpClient = HttpClient.newHttpClient()
        Collector first = new Collector()
        Collector second = new Collector()
        WebSocket receiverSocket = connect(httpClient, server, '/bb-broadcast', first)
        webSocketPipelines(recorder, 1)
        WebSocket senderSocket = connect(httpClient, server, '/bb-broadcast', second)
        List<ChannelPipeline> pipelines = webSocketPipelines(recorder, 2)
        List<BinaryWebSocketFrame> frames = new CopyOnWriteArrayList<>()
        record(pipelines[1], frames)

        when:
        senderSocket.sendBinary(ByteBuffer.wrap(LARGE), true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert first.messages.size() == 1
            assert second.messages.size() == 1
        }
        first.messages[0] == LARGE
        second.messages[0] == LARGE
        settle(pipelines + pipelines)
        frames.size() == 1
        frames[0].refCnt() == 1

        cleanup:
        frames?.each { if (it.refCnt() > 0) it.release() }
        senderSocket?.abort()
        receiverSocket?.abort()
        httpClient?.close()
        server?.close()
    }

    void "a ByteBuf the handler only reads is released exactly once"() {
        given:
        EmbeddedServer server = run()
        PipelineRecorder recorder = server.applicationContext.getBean(PipelineRecorder)
        ReadServer readServer = server.applicationContext.getBean(ReadServer)
        HttpClient httpClient = HttpClient.newHttpClient()
        WebSocket ws = connect(httpClient, server, '/bb-read', new WebSocket.Listener() {})
        List<BinaryWebSocketFrame> frames = new CopyOnWriteArrayList<>()
        List<ChannelPipeline> pipelines = webSocketPipelines(recorder, 1)
        record(pipelines[0], frames)

        when:
        ws.sendBinary(ByteBuffer.wrap(LARGE), true).get(10, TimeUnit.SECONDS)
        ws.sendBinary(ByteBuffer.wrap(LARGE), true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert readServer.lengths == [LARGE.length, LARGE.length]
        }
        settle(pipelines)
        frames.size() == 2
        // the handler runs within the read of the frame: it saw the reference of the pipeline,
        // the one the framework holds for it, and the one the test holds
        readServer.refCounts == [3, 3]
        // the pipeline and the framework each released their reference, and only that one
        frames*.refCnt() == [1, 1]

        cleanup:
        frames?.each { if (it.refCnt() > 0) it.release() }
        ws?.abort()
        httpClient?.close()
        server?.close()
    }

    private static EmbeddedServer run() {
        return ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                    : 'WebSocketByteBufForwardSpec',
                'micronaut.netty.event-loops.default.num-threads': 4
        ])
    }

    private static WebSocket connect(HttpClient httpClient, EmbeddedServer server, String path, WebSocket.Listener listener) {
        return httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}$path"), listener)
                .get(10, TimeUnit.SECONDS)
    }

    private static List<ChannelPipeline> webSocketPipelines(PipelineRecorder recorder, int count) {
        new PollingConditions(timeout: 10).eventually {
            assert recorder.pipelines.count { it.get(NettyServerWebSocketHandler.ID) != null } == count
        }
        return recorder.pipelines.findAll { it.get(NettyServerWebSocketHandler.ID) != null }
    }

    private static void record(ChannelPipeline pipeline, List<BinaryWebSocketFrame> frames) {
        // added on the event loop, so that it sees the next frame
        pipeline.channel().eventLoop().submit({
            pipeline.addBefore(NettyServerWebSocketHandler.ID, 'frame-recorder', new ChannelInboundHandlerAdapter() {
                @Override
                void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof BinaryWebSocketFrame) {
                        // hold a reference, so that the pooled buffer is not recycled before the test checks it
                        frames.add(((BinaryWebSocketFrame) msg).retain())
                    }
                    ctx.fireChannelRead(msg)
                }
            })
        } as Runnable).get(10, TimeUnit.SECONDS)
    }

    /**
     * Waits for the tasks queued on the event loops: the release of the payload once its handler
     * completed, and the release of the buffers that were written.
     */
    private static boolean settle(List<ChannelPipeline> pipelines) {
        pipelines.each { it.channel().eventLoop().submit({} as Runnable).get(10, TimeUnit.SECONDS) }
        return true
    }

    static class Collector implements WebSocket.Listener {
        final List<byte[]> messages = new CopyOnWriteArrayList<>()
        private final ByteArrayOutputStream current = new ByteArrayOutputStream()

        @Override
        CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()]
            data.get(bytes)
            current.write(bytes)
            if (last) {
                messages.add(current.toByteArray())
                current.reset()
            }
            webSocket.request(1)
            return null
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketByteBufForwardSpec')
    @ServerWebSocket('/bb-echo')
    static class EchoServer {

        @OnMessage
        void onMessage(ByteBuf message, WebSocketSession session) {
            session.sendAsync(message.retain())
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketByteBufForwardSpec')
    @ServerWebSocket('/bb-read')
    static class ReadServer {
        final List<Integer> lengths = new CopyOnWriteArrayList<>()
        final List<Integer> refCounts = new CopyOnWriteArrayList<>()

        @OnMessage
        void onMessage(ByteBuf message) {
            refCounts.add(message.refCnt())
            lengths.add(message.readableBytes())
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketByteBufForwardSpec')
    @ServerWebSocket('/bb-relay')
    static class RelayServer {
        volatile WebSocketSession receiver

        @OnOpen
        void onOpen(WebSocketSession session) {
            if (receiver == null) {
                receiver = session
            }
        }

        @OnMessage
        void onMessage(ByteBuf message, WebSocketSession session) {
            receiver.sendAsync(message.retain())
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketByteBufForwardSpec')
    @ServerWebSocket('/bb-broadcast')
    static class BroadcastServer {
        private final WebSocketBroadcaster broadcaster

        BroadcastServer(WebSocketBroadcaster broadcaster) {
            this.broadcaster = broadcaster
        }

        @OnMessage
        void onMessage(ByteBuf message) {
            broadcaster.broadcastAsync(message.retain())
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketByteBufForwardSpec')
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
