package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.micronaut.http.server.netty.HttpCompressionStrategy
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoop
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class PipeliningServerHandlerCallerFailureSpec extends Specification {
    DefaultEventLoop loop = new DefaultEventLoop()
    PipeliningServerHandler handler
    EmbeddedChannel channel
    NettyByteBodyFactory factory
    OutboundAccess access
    int written

    void setup() {
        handler = new PipeliningServerHandler(Stub(RequestHandler) {
            accept(_, _, _, _) >> { ctx, request, CloseableByteBody body, OutboundAccess outbound ->
                body.close()
                access = outbound
            }
            responseWritten(_) >> { written++ }
            handleUnboundError(_) >> { Throwable cause -> throw new AssertionError(cause) }
        })
        onLoop {
            // Keep the embedded transport, but use a real loop so the test thread is off-loop.
            channel = new EmbeddedChannel(handler) {
                @Override
                EventLoop eventLoop() {
                    PipeliningServerHandlerCallerFailureSpec.this.loop
                }
            }
            factory = new NettyByteBodyFactory(channel)
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'))
        }
        assert !channel.eventLoop().inEventLoop()
    }

    void cleanup() {
        try {
            onLoop { channel?.finishAndReleaseAll() }
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS)
        }
    }

    def 'an off-loop second #kind write throws synchronously and releases the refused body'() {
        given:
        onLoop {
            access.write(response(), streamingBody())
        }
        def refused = bytes('refused')
        def body = kind == 'full' ? factory.adapt(refused) : streamingBody(refused)

        when:
        access.write(response(), body)

        then:
        def error = thrown(IllegalStateException)
        error.message == 'Only one response per request'
        kind != 'full' || refused.refCnt() == 0

        when:
        onLoop {
            channel.checkException()
            assert access.handler.phase.name() == 'STREAMING'
        }

        then:
        refused.refCnt() == 0
        written == 0
        channel.open

        cleanup:
        body.close()

        where:
        kind << ['full', 'streaming']
    }

    def 'off-loop #stage failure for a #kind response reaches the caller and releases the body'() {
        given:
        def failure = new IllegalArgumentException('preparation failed')
        def response = response()
        if (stage == 'preprocessing') {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders() {
                @Override
                boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
                    throw failure
                }
            })
        } else {
            handler.setCompressionStrategy(Stub(HttpCompressionStrategy) {
                isEnabled() >> true
                shouldCompress(_) >> { throw failure }
                getMaxZstdEncodeSize() >> 1024 * 1024
            })
        }
        def content = bytes('response')
        def body = kind == 'full' ? factory.adapt(content) : streamingBody(content)

        when:
        try {
            access.write(response, body)
        } catch (Throwable t) {
            // An unclaimed streaming body is still owned by the caller.
            if (kind == 'streaming') {
                body.close()
            }
            throw t
        }

        then:
        def error = thrown(IllegalArgumentException)
        error.is(failure)
        kind != 'full' || content.refCnt() == 0

        when:
        onLoop {
            channel.checkException()
            assert channel.outboundMessages().empty
        }

        then:
        content.refCnt() == 0
        access.handler == null
        written == 0
        channel.open

        cleanup:
        body.close()

        where:
        stage           | kind
        'preprocessing' | 'full'
        'preprocessing' | 'streaming'
        'compression'   | 'full'
        'compression'   | 'streaming'
    }

    private StreamingNettyByteBody streamingBody(ByteBuf content = null) {
        def buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }
        })
        if (content != null) {
            onLoop { buffer.add(factory.readBufferFactory().adapt(content)) }
        }
        new StreamingNettyByteBody(buffer)
    }

    private void onLoop(Closure action) {
        if (loop.inEventLoop()) {
            action()
        } else {
            loop.submit(action as Runnable).get(10, TimeUnit.SECONDS)
        }
    }

    private static DefaultHttpResponse response() {
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    }

    private static ByteBuf bytes(String value) {
        Unpooled.copiedBuffer(value, StandardCharsets.UTF_8)
    }
}
