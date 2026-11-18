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
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.buffer.UnpooledHeapByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoop
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer

class WebSocketCloseStateSpec extends Specification {
    void 'payload release is idempotent and does not hold the completion monitor'() {
        given:
        def completion = new AtomicReference<BiConsumer>()
        def locked = []
        def bytes = new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, 1, 1) {
            @Override
            boolean release() {
                locked.add(completion.get() != null && Thread.holdsLock(completion.get()))
                super.release()
            }
        }
        bytes.writeByte(1)
        def handler = handler(true, false)
        handler.messageFlow = Stub(ExecutionFlow) {
            onComplete(_) >> { BiConsumer callback ->
                completion.set(callback)
                throw new IllegalStateException('completion registration failed')
            }
        }
        channel.pipeline().addLast(handler)

        when:
        channel.writeInbound(new TextWebSocketFrame(bytes))
        ((Runnable) completion.get()).run()
        channel.runPendingTasks()

        then:
        bytes.refCnt() == 0
        locked == [false, false]
    }

    List<Class<?>> written = new CopyOnWriteArrayList<>()
    List<ChannelPromise> promises = new CopyOnWriteArrayList<>()
    EmbeddedChannel channel = new EmbeddedChannel(recorder())
    NettyWebSocketSession session = new NettyWebSocketSession('test', channel, HttpRequest.GET('/'), null, null, '13', false)

    private ChannelOutboundHandlerAdapter recorder() {
        new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                written.add(message.class)
                ReferenceCountUtil.release(message)
                promises.add(promise)
            }
        }
    }

    void cleanup() {
        promises.each { it.trySuccess() }
        channel.runPendingTasks()
        channel.finishAndReleaseAll()
    }

    void 'off-loop send precedes close with handler #withHandler'() {
        given:
        def loop = new DefaultEventLoop()
        def localChannel = loop.submit({ ->
            new EmbeddedChannel() {
                @Override
                EventLoop eventLoop() { loop }
            }
        } as Callable<EmbeddedChannel>).get(5, TimeUnit.SECONDS)
        def localSession = new NettyWebSocketSession('queued', localChannel, HttpRequest.GET('/'), null, null, '13', false)
        def handler = withHandler ? this.handler(false, true, localSession) : null
        if (handler != null) {
            loop.submit { localChannel.pipeline().addLast(handler) }.get(5, TimeUnit.SECONDS)
        }
        def entered = new CountDownLatch(1)
        def resume = new CountDownLatch(1)
        loop.execute {
            entered.countDown()
            assert resume.await(5, TimeUnit.SECONDS)
        }
        assert entered.await(5, TimeUnit.SECONDS)

        when:
        localSession.close(new CloseReason(1005, 'invalid'))

        then:
        thrown(IllegalArgumentException)
        localSession.open
        CloseState.get(localChannel).get().is(CloseState.OPEN)

        when:
        def send = localSession.sendAsync('payload', MediaType.TEXT_PLAIN_TYPE)
        localSession.close()

        then:
        !localSession.open

        when:
        resume.countDown()
        send.get(5, TimeUnit.SECONDS)
        loop.submit {}.get(5, TimeUnit.SECONDS)
        loop.submit {}.get(5, TimeUnit.SECONDS)
        def data = localChannel.readOutbound()
        def close = localChannel.readOutbound()

        then:
        data instanceof TextWebSocketFrame
        data.text() == 'payload'
        close instanceof CloseWebSocketFrame
        localChannel.readOutbound() == null
        handler == null || handler.closes.get() == 1

        cleanup:
        resume.countDown()
        data?.release()
        close?.release()
        loop.submit { localChannel.finishAndReleaseAll() }.get(5, TimeUnit.SECONDS)
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS)

        where:
        withHandler << [false, true]
    }

    void 'session can close after its handler is removed'() {
        given:
        def handler = handler(false, false)
        channel.pipeline().addLast(handler)

        when:
        channel.pipeline().remove(handler)
        session.close(CloseReason.GOING_AWAY)

        then:
        written == [CloseWebSocketFrame]

        when:
        promises.each { it.trySuccess() }
        channel.runPendingTasks()

        then:
        !channel.open
    }

    void 'a stalled close frame forces termination with handler #withHandler'() {
        given:
        channel.freezeTime()
        def handler = withHandler ? this.handler() : null
        if (handler != null) {
            channel.pipeline().addLast(handler)
        }

        when:
        session.close()
        channel.advanceTimeBy(2, TimeUnit.SECONDS)
        channel.runScheduledPendingTasks()
        channel.runPendingTasks()

        then:
        written == [CloseWebSocketFrame]
        !channel.open
        handler == null || handler.closes.get() == 1

        where:
        withHandler << [false, true]
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
        def loop = new DefaultEventLoop()
        def localChannel = loop.submit({ ->
            new EmbeddedChannel(recorder()) {
                @Override
                EventLoop eventLoop() { loop }
            }
        } as Callable<EmbeddedChannel>).get(5, TimeUnit.SECONDS)
        def localSession = new NettyWebSocketSession('race', localChannel, HttpRequest.GET('/'), null, null, '13', false)
        if (withHandler) {
            def localHandler = handler(false, true, localSession)
            loop.submit { localChannel.pipeline().addLast(localHandler) }.get(5, TimeUnit.SECONDS)
        }
        def executor = Executors.newFixedThreadPool(2)
        CountDownLatch go = new CountDownLatch(1)

        when:
        def send = executor.submit({
            assert go.await(5, TimeUnit.SECONDS)
            30.times {
                try {
                    localSession.sendAsync('hello', MediaType.TEXT_PLAIN_TYPE)
                    localSession.sendSync(null, MediaType.TEXT_PLAIN_TYPE)
                    localSession.sendPingAsync(new byte[0])
                    localSession.send('hello', MediaType.TEXT_PLAIN_TYPE).subscribe({}, { error ->
                        assert error instanceof WebSocketSessionException
                    })
                } catch (WebSocketSessionException ignored) {
                }
            }
        } as Runnable)
        def close = executor.submit({
            assert go.await(5, TimeUnit.SECONDS)
            localSession.close()
            localSession.close()
        } as Runnable)
        go.countDown()
        send.get(5, TimeUnit.SECONDS)
        close.get(5, TimeUnit.SECONDS)
        loop.submit {}.get(5, TimeUnit.SECONDS)

        then:
        written.count(CloseWebSocketFrame) == 1
        written.last() == CloseWebSocketFrame
        !localSession.open

        when:
        localSession.sendAsync('late', MediaType.TEXT_PLAIN_TYPE)

        then:
        thrown(WebSocketSessionException)

        cleanup:
        go.countDown()
        executor.shutdownNow()
        loop.submit {
            promises.each { it.trySuccess() }
            localChannel.finishAndReleaseAll()
        }.get(5, TimeUnit.SECONDS)
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS)

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

    private TestHandler handler(boolean withMessage = false, boolean withClose = true, NettyWebSocketSession target = session) {
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
            closeMethod() >> (withClose ? Optional.of(close) : Optional.empty())
            openMethod() >> Optional.empty()
            errorMethod() >> Optional.empty()
        }
        ConversionService conversion = Stub() {
            convert(_, _, _) >> { value, sourceType, targetType -> Optional.of(value) }
        }
        new TestHandler(target, bean, close, Stub(RequestBinderRegistry), Stub(MediaTypeCodecRegistry), Stub(MessageBodyHandlerRegistry), conversion)
    }

    static class TestHandler extends AbstractNettyWebSocketHandler {
        final NettyWebSocketSession session
        final MethodExecutionHandle closeMethod
        final AtomicInteger closes = new AtomicInteger()
        final AtomicInteger messages = new AtomicInteger()
        boolean failAfterMessage
        ExecutionFlow<?> messageFlow

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
                if (messageFlow != null) {
                    return messageFlow
                }
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
