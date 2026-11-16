package io.micronaut.http.netty.websocket

import io.micronaut.core.bind.BoundExecutable
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.bind.RequestBinderRegistry
import io.micronaut.http.body.MessageBodyHandlerRegistry
import io.micronaut.http.codec.MediaTypeCodecRegistry
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.MethodExecutionHandle
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.context.WebSocketBean
import io.micronaut.websocket.exceptions.WebSocketSessionException
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebSocketCloseStateSpec extends Specification {
    List<Class<?>> written = new CopyOnWriteArrayList<>()
    List<ChannelPromise> promises = new CopyOnWriteArrayList<>()
    EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
        @Override
        void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
            written.add(message.class)
            ReferenceCountUtil.release(message)
            promises.add(promise)
        }
    })
    NettyWebSocketSession session = new NettyWebSocketSession('test', channel, HttpRequest.GET('/'), null, null, '13', false)

    void cleanup() {
        promises.each { it.trySuccess() }
        channel.runPendingTasks()
        channel.finishAndReleaseAll()
    }

    void 'concurrent closes write one frame and invoke OnClose once'() {
        given:
        def handler = handler()
        channel.pipeline().addLast(handler)
        def executor = Executors.newFixedThreadPool(2)
        CountDownLatch go = new CountDownLatch(1)

        when:
        def closes = (0..<2).collect {
            executor.submit({
                assert go.await(5, TimeUnit.SECONDS)
                session.close(CloseReason.GOING_AWAY)
            } as Runnable)
        }
        go.countDown()
        closes.each { it.get(5, TimeUnit.SECONDS) }
        promises.each { it.trySuccess() }
        channel.runPendingTasks()

        then:
        written == [CloseWebSocketFrame]
        handler.closes.get() == 1
        !session.open
        CloseState.get(channel).get().is(CloseState.CLOSED)

        cleanup:
        go.countDown()
        executor.shutdownNow()
    }

    void 'send racing close never writes data after the close frame with handler #withHandler'() {
        given:
        if (withHandler) {
            channel.pipeline().addLast(handler())
        }
        def executor = Executors.newFixedThreadPool(2)
        CountDownLatch go = new CountDownLatch(1)

        when:
        def send = executor.submit({
            assert go.await(5, TimeUnit.SECONDS)
            30.times {
                try {
                    session.sendAsync('hello', MediaType.TEXT_PLAIN_TYPE)
                    session.sendSync(null, MediaType.TEXT_PLAIN_TYPE)
                    session.sendPingAsync(new byte[0])
                    session.send('hello', MediaType.TEXT_PLAIN_TYPE).subscribe({}, { error ->
                        assert error instanceof WebSocketSessionException
                    })
                } catch (WebSocketSessionException ignored) {
                }
            }
        } as Runnable)
        def close = executor.submit({
            assert go.await(5, TimeUnit.SECONDS)
            session.close()
            session.close()
        } as Runnable)
        go.countDown()
        send.get(5, TimeUnit.SECONDS)
        close.get(5, TimeUnit.SECONDS)

        then:
        written.count(CloseWebSocketFrame) == 1
        written.last() == CloseWebSocketFrame
        !session.open

        when:
        session.sendAsync('late', MediaType.TEXT_PLAIN_TYPE)

        then:
        thrown(WebSocketSessionException)

        cleanup:
        go.countDown()
        executor.shutdownNow()

        where:
        withHandler << [true, false]
    }

    void 'completion and failure cleanup release only the owned payload reference'() {
        given:
        def handler = handler(true)
        handler.failAfterMessage = true
        channel.pipeline().addLast(handler)
        TextWebSocketFrame frame = new TextWebSocketFrame('payload')
        // An independent owner must survive both cleanup paths, even with a positive refCnt.
        frame.retain()

        when:
        channel.writeInbound(frame)
        channel.runPendingTasks()

        then:
        frame.refCnt() == 1
        handler.messages.get() == 1

        cleanup:
        frame.release()
    }

    private TestHandler handler(boolean withMessage = false) {
        ExecutableMethod executable = Stub() {
            getArguments() >> ([Argument.of(ByteBuf)] as Argument[])
        }
        MethodExecutionHandle message = Stub() {
            intValue(_, _) >> OptionalInt.empty()
            getExecutableMethod() >> executable
        }
        MethodExecutionHandle close = Stub() {
            getExecutableMethod() >> Stub(ExecutableMethod) {
                getArguments() >> new Argument[0]
            }
        }
        WebSocketBean bean = Stub() {
            messageMethod() >> (withMessage ? Optional.of(message) : Optional.empty())
            closeMethod() >> Optional.of(close)
            openMethod() >> Optional.empty()
            errorMethod() >> Optional.empty()
        }
        ConversionService conversion = Stub() {
            convert(_, _, _) >> { value, sourceType, targetType -> Optional.of(value) }
        }
        new TestHandler(session, bean, close, Stub(RequestBinderRegistry), Stub(MediaTypeCodecRegistry), Stub(MessageBodyHandlerRegistry), conversion)
    }

    static class TestHandler extends AbstractNettyWebSocketHandler {
        final NettyWebSocketSession session
        final MethodExecutionHandle closeMethod
        final AtomicInteger closes = new AtomicInteger()
        final AtomicInteger messages = new AtomicInteger()
        boolean failAfterMessage

        TestHandler(NettyWebSocketSession session, WebSocketBean bean, MethodExecutionHandle closeMethod,
                    RequestBinderRegistry binders, MediaTypeCodecRegistry codecs, MessageBodyHandlerRegistry bodies, ConversionService conversion) {
            super(binders, codecs, bodies, bean, HttpRequest.GET('/'), [:], WebSocketVersion.V13, '', null, conversion)
            this.session = session
            this.closeMethod = closeMethod
        }

        @Override
        Argument<?> getBodyArgument() { Argument.of(ByteBuf) }

        @Override
        Argument<?> getPongArgument() { Argument.OBJECT_ARGUMENT }

        @Override
        protected NettyWebSocketSession createWebSocketSession(ChannelHandlerContext ctx) { session }

        @Override
        protected ExecutionFlow<?> invokeExecutable(BoundExecutable bound, MethodExecutionHandle method) {
            if (method.is(closeMethod)) {
                closes.incrementAndGet()
            } else {
                messages.incrementAndGet()
            }
            ExecutionFlow.empty()
        }

        @Override
        protected void messageHandled(ChannelHandlerContext ctx, Object message) {
            if (failAfterMessage) {
                throw new IllegalStateException('completion failed')
            }
        }
    }
}
