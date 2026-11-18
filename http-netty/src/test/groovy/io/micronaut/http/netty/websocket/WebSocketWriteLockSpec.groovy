package io.micronaut.http.netty.websocket

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.websocket.exceptions.WebSocketSessionException
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoop
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class WebSocketWriteLockSpec extends Specification {
    def 'off-loop #kind send respects close queue order with closeFirst=#closeFirst'() {
        given:
        def loop = new DefaultEventLoop()
        def channel = loop.submit({ ->
            new EmbeddedChannel() {
                @Override
                EventLoop eventLoop() { loop }
            }
        } as Callable<EmbeddedChannel>).get(5, TimeUnit.SECONDS)
        def session = new NettyWebSocketSession('test', channel, HttpRequest.GET('/'), null, null, '13', false)
        def payload = kind == 'frame' ? new TextWebSocketFrame('payload') : Unpooled.buffer().writeByte(1)
        def entered = new CountDownLatch(1)
        def resume = new CountDownLatch(1)
        loop.execute {
            entered.countDown()
            assert resume.await(5, TimeUnit.SECONDS)
        }
        assert entered.await(5, TimeUnit.SECONDS)

        when:
        if (closeFirst) {
            session.close()
        }
        def send = session.sendAsync(payload, MediaType.TEXT_PLAIN_TYPE)
        session.close()

        then:
        session.open

        when:
        resume.countDown()
        ExecutionException failure = null
        try {
            send.get(5, TimeUnit.SECONDS)
        } catch (ExecutionException e) {
            failure = e
        }

        then:
        if (closeFirst) {
            assert failure.cause instanceof WebSocketSessionException
            assert failure.cause.cause.message == 'Session closed'
            assert payload.refCnt() == 0
        } else {
            assert failure == null
            assert payload.refCnt() == 1
        }

        when:
        loop.submit {}.get(5, TimeUnit.SECONDS)
        def data = closeFirst ? null : channel.readOutbound()
        def close = channel.readOutbound()

        then:
        closeFirst || data.content().readableBytes() == (kind == 'frame' ? 7 : 1)
        close instanceof CloseWebSocketFrame
        channel.readOutbound() == null

        when:
        session.sendAsync('late', MediaType.TEXT_PLAIN_TYPE)

        then:
        def closed = thrown(WebSocketSessionException)
        closed.message == 'Session closed'

        cleanup:
        resume.countDown()
        close?.release()
        data?.release()
        loop.submit { channel.finishAndReleaseAll() }.get(5, TimeUnit.SECONDS)
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS)

        where:
        [kind, closeFirst] << [['frame', 'ByteBuf'], [false, true]].combinations()
    }

    def 'a send on a shut-down event loop reports a session failure and releases its frame'() {
        given:
        def loop = new DefaultEventLoop()
        def channel = loop.submit({ ->
            new EmbeddedChannel() {
                @Override
                EventLoop eventLoop() { loop }
            }
        } as Callable<EmbeddedChannel>).get(5, TimeUnit.SECONDS)
        def session = new NettyWebSocketSession('test', channel, HttpRequest.GET('/'), null, null, '13', false)
        def payload = new TextWebSocketFrame('payload')
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS)

        when:
        def send = session.sendAsync(payload, MediaType.TEXT_PLAIN_TYPE)
        send.get(5, TimeUnit.SECONDS)

        then:
        def failure = thrown(ExecutionException)
        failure.cause instanceof WebSocketSessionException
        failure.cause.cause instanceof java.util.concurrent.RejectedExecutionException
        payload.refCnt() == 0

        cleanup:
        channel.finishAndReleaseAll()
    }

    def 'an event loop #operation listener can acquire a lock held by a concurrent sender'() {
        given:
        def loop = new DefaultEventLoop()
        def sender = Executors.newSingleThreadExecutor()
        def userLock = new ReentrantLock()
        def listenerEntered = new CountDownLatch(1)
        def listenerAcquired = new AtomicBoolean()
        def writes = new AtomicInteger()
        def channel = loop.submit({ ->
            new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                    ReferenceCountUtil.release(message)
                    if (writes.getAndIncrement() == 0) {
                        promise.addListener {
                            listenerEntered.countDown()
                            // Bound the deadlock so a regression cannot strand the test's threads.
                            if (userLock.tryLock(3, TimeUnit.SECONDS)) {
                                try {
                                    listenerAcquired.set(true)
                                } finally {
                                    userLock.unlock()
                                }
                            }
                        }
                    }
                    promise.setSuccess()
                }
            }) {
                @Override
                EventLoop eventLoop() { loop }
            }
        } as Callable<EmbeddedChannel>).get(5, TimeUnit.SECONDS)
        def session = new NettyWebSocketSession('test', channel, HttpRequest.GET('/'), null, null, '13', false)

        when:
        def concurrentSend = sender.submit({
            userLock.lock()
            try {
                loop.execute {
                    assert loop.inEventLoop()
                    if (operation == 'send') {
                        session.sendAsync('first', MediaType.TEXT_PLAIN_TYPE)
                    } else {
                        session.sendPingAsync(new byte[0])
                    }
                }
                assert listenerEntered.await(5, TimeUnit.SECONDS)
                session.sendAsync('second', MediaType.TEXT_PLAIN_TYPE)
            } finally {
                userLock.unlock()
            }
        } as Runnable)
        concurrentSend.get(10, TimeUnit.SECONDS)
        loop.submit {}.get(5, TimeUnit.SECONDS)

        then:
        listenerAcquired.get()
        writes.get() == 2

        cleanup:
        sender.shutdownNow()
        loop.submit { channel.finishAndReleaseAll() }.get(5, TimeUnit.SECONDS)
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS)

        where:
        operation << ['send', 'ping']
    }
}
