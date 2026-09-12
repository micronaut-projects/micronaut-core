package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Tests fragmented message decoding and frame buffer ownership for {@code @OnMessage} handlers, using a raw Netty
 * client so that frames can be fragmented exactly as needed.
 */
class WebSocketFrameOwnershipSpec extends Specification {

    private static final String SPEC_NAME = 'WebSocketFrameOwnershipSpec'

    void "fragmented JSON message is decoded into a POJO from all fragments"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])
        PojoSocket socket = server.applicationContext.getBean(PojoSocket)
        RawWebSocketClient client = RawWebSocketClient.connect(server, '/ws/frame-ownership/pojo')
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        client.channel.write(new TextWebSocketFrame(false, 0, '{"text":"hel'))
        client.channel.writeAndFlush(new ContinuationWebSocketFrame(true, 0, 'lo"}')).sync()

        then:
        conditions.eventually {
            socket.received.size() == 1
            socket.received[0] == new Message('hello')
        }
        client.closeFrames.isEmpty()
        socket.errors.isEmpty()

        cleanup:
        client.shutdown()
        server.stop()
    }

    void "binary message bound to #type on a blocking executor sees the complete payload"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])
        BinarySocket socket = server.applicationContext.getBean(type)
        RawWebSocketClient client = RawWebSocketClient.connect(server, path)
        PollingConditions conditions = new PollingConditions(timeout: 10)
        byte[] payload = new byte[16_000]
        new Random(42).nextBytes(payload)

        when:
        ByteBuf first = PooledByteBufAllocator.DEFAULT.buffer(8_000).writeBytes(payload, 0, 8_000)
        ByteBuf second = PooledByteBufAllocator.DEFAULT.buffer(8_000).writeBytes(payload, 8_000, 8_000)
        client.channel.write(new BinaryWebSocketFrame(false, 0, first))
        client.channel.writeAndFlush(new ContinuationWebSocketFrame(true, 0, second)).sync()

        then:
        conditions.eventually {
            socket.received.size() == 1
        }
        socket.received[0] == payload
        socket.errors.isEmpty()
        client.closeFrames.isEmpty()

        when:"the connection goes away and the leak detector gets a chance to run"
        client.channel.close().sync()
        client.closed.get(10, TimeUnit.SECONDS)
        System.gc()
        Thread.sleep(200)

        then:
        client.errors.isEmpty()

        cleanup:
        client.shutdown()
        server.stop()

        where:
        type             | path
        ByteBufSocket    | '/ws/frame-ownership/bytebuf'
        ByteBufferSocket | '/ws/frame-ownership/bytebuffer'
    }

    static class RawWebSocketClient {
        final NioEventLoopGroup group = new NioEventLoopGroup(1)
        final CompletableFuture<Void> handshake = new CompletableFuture<>()
        final CompletableFuture<Void> closed = new CompletableFuture<>()
        final List<Map> closeFrames = new CopyOnWriteArrayList<>()
        final List<String> textMessages = new CopyOnWriteArrayList<>()
        final List<Throwable> errors = new CopyOnWriteArrayList<>()
        Channel channel

        static RawWebSocketClient connect(EmbeddedServer server, String path) {
            RawWebSocketClient client = new RawWebSocketClient()
            URI uri = new URI("ws://${server.host}:${server.port}${path}")
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), 1024 * 1024)
            Bootstrap bootstrap = new Bootstrap()
                    .group(client.group)
                    .channel(NioSocketChannel)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new HttpClientCodec())
                                    .addLast(new HttpObjectAggregator(8192))
                                    .addLast(new SimpleChannelInboundHandler<Object>() {
                                        @Override
                                        void channelActive(ChannelHandlerContext ctx) {
                                            handshaker.handshake(ctx.channel())
                                        }

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                            if (!handshaker.isHandshakeComplete()) {
                                                handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg)
                                                client.handshake.complete(null)
                                            } else if (msg instanceof CloseWebSocketFrame) {
                                                client.closeFrames.add([code: msg.statusCode(), reason: msg.reasonText()])
                                            } else if (msg instanceof TextWebSocketFrame) {
                                                client.textMessages.add(msg.text())
                                            }
                                        }

                                        @Override
                                        void channelInactive(ChannelHandlerContext ctx) {
                                            client.closed.complete(null)
                                        }

                                        @Override
                                        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            client.errors.add(cause)
                                            client.handshake.completeExceptionally(cause)
                                            ctx.close()
                                        }
                                    })
                        }
                    })
            client.channel = bootstrap.connect(server.host, server.port).sync().channel()
            client.handshake.get(10, TimeUnit.SECONDS)
            return client
        }

        void shutdown() {
            channel?.close()?.sync()
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync()
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameOwnershipSpec')
    @ServerWebSocket('/ws/frame-ownership/pojo')
    static class PojoSocket {
        final List<Message> received = new CopyOnWriteArrayList<>()
        final List<Throwable> errors = new CopyOnWriteArrayList<>()

        @OnMessage
        void onMessage(Message message) {
            received.add(message)
        }

        @OnError
        void onError(Throwable error) {
            errors.add(error)
        }
    }

    static abstract class BinarySocket {
        final List<byte[]> received = new CopyOnWriteArrayList<>()
        final List<Throwable> errors = new CopyOnWriteArrayList<>()

        @OnError
        void onError(Throwable error) {
            errors.add(error)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameOwnershipSpec')
    @ServerWebSocket('/ws/frame-ownership/bytebuf')
    static class ByteBufSocket extends BinarySocket {
        @OnMessage
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onMessage(ByteBuf message) {
            // give the event loop a chance to release the frame before we read it
            Thread.sleep(100)
            received.add(ByteBufUtil.getBytes(message))
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketFrameOwnershipSpec')
    @ServerWebSocket('/ws/frame-ownership/bytebuffer')
    static class ByteBufferSocket extends BinarySocket {
        @OnMessage
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onMessage(ByteBuffer<?> message) {
            Thread.sleep(100)
            received.add(message.toByteArray())
        }
    }
}
