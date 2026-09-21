package io.micronaut.http.server.netty.handler

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A streaming response body can hand over bytes it has already buffered as soon as the response
 * writer subscribes to it, before the response is the one being written (e.g. the body of a
 * partially received request, or a response of the HTTP client relayed by a route). Those bytes
 * must be written after the response headers.
 */
class PipeliningServerHandlerEarlyDataSpec extends Specification {

    def 'bytes a body buffered before the response is written are sent'() {
        given:
        StreamingNettyByteBody.SharedBuffer buffer = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                buffer = streamingBuffer(ctx)
                buffer.add(readBuffer("hello "))
                // the writer subscribes here, and gets 'hello ' right away
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new StreamingNettyByteBody(buffer))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        buffer.add(readBuffer("world"))
        buffer.complete()
        ch.runPendingTasks()

        then:
        ch.checkException()
        responseBodies(ch) == ["hello world"]
    }

    def 'bytes a queued response body buffered are sent after the response before it'() {
        given:
        def firstBody = Sinks.many().unicast().<ByteBuf>onBackpressureBuffer()
        StreamingNettyByteBody.SharedBuffer secondBuffer = null
        int requests = 0
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                if (requests++ == 0) {
                    outboundAccess.write(response, new NettyByteBodyFactory(ctx.channel()).adaptNetty(firstBody.asFlux()))
                } else {
                    secondBuffer = streamingBuffer(ctx)
                    secondBuffer.add(readBuffer("early "))
                    // queued behind the first response, which is still being written
                    outboundAccess.write(response, new StreamingNettyByteBody(secondBuffer))
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"))
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second"))
        ch.flushInbound()
        firstBody.tryEmitNext(Unpooled.copiedBuffer("first", StandardCharsets.UTF_8))
        firstBody.tryEmitComplete()
        secondBuffer.add(readBuffer("late"))
        secondBuffer.complete()
        ch.runPendingTasks()

        then:
        ch.checkException()
        responseBodies(ch) == ["first", "early late"]
    }

    private static StreamingNettyByteBody.SharedBuffer streamingBuffer(ChannelHandlerContext ctx) {
        return new NettyByteBodyFactory(ctx.channel()).createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }
        })
    }

    private static readBuffer(String s) {
        return NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(Unpooled.copiedBuffer(s, StandardCharsets.UTF_8))
    }

    private static List<String> responseBodies(EmbeddedChannel ch) {
        List<String> bodies = []
        StringBuilder current = null
        Object message
        while ((message = ch.readOutbound()) != null) {
            if (message instanceof HttpResponse) {
                current = new StringBuilder()
            }
            if (message instanceof HttpContent) {
                current.append(message.content().toString(StandardCharsets.UTF_8))
                message.release()
            }
            if (message instanceof LastHttpContent) {
                bodies.add(current.toString())
            }
        }
        return bodies
    }
}
