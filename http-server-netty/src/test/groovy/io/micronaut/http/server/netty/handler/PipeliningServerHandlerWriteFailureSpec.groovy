package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.micronaut.http.netty.compression.DefaultNettyZlibCodecFactory
import io.micronaut.http.netty.compression.NettyZlibCodecFactory
import io.micronaut.http.server.netty.HttpCompressionStrategy
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.EncoderException
import io.netty.handler.codec.compression.CompressionException
import io.netty.handler.codec.compression.ZlibDecoder
import io.netty.handler.codec.compression.ZlibEncoder
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A failure of the body of an HTTP/1 response cuts the response: what was written goes out, the
 * request is reported once and the connection is closed.
 */
class PipeliningServerHandlerWriteFailureSpec extends Specification {

    def 'a failed response whose body throws from its discard is still reported and closes the connection'() {
        given:
        def written = []
        def unbound = []
        OutboundAccess outbound = null
        def channel = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess access) {
                body.close()
                access.attachment(request.uri())
                outbound = access
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }

            @Override
            void handleUnboundError(Throwable cause) {
                unbound << cause
            }
        }))
        def factory = new NettyByteBodyFactory(channel)
        def failure = new IllegalStateException('discard failed')
        int discards = 0
        def responseBody = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            void allowDiscard() {
                discards++
                throw failure
            }
        })
        ByteBuf sent = Unpooled.copiedBuffer('sent', StandardCharsets.UTF_8)

        when: 'the response starts, and its body fails after the first piece'
        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'))
        outbound.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new StreamingNettyByteBody(responseBody))
        responseBody.add(factory.readBufferFactory().adapt(sent))
        channel.runPendingTasks()
        Throwable thrown = null
        try {
            responseBody.error(new IOException('body failed'))
            channel.runPendingTasks()
            channel.checkException()
        } catch (Throwable t) {
            thrown = t
        }
        List<Object> messages = []
        Object msg
        while ((msg = channel.readOutbound()) != null) {
            messages << msg
        }

        then: 'the failure of the discard does not keep the connection open'
        thrown.is(failure)
        discards == 1
        written == ['/']
        unbound.empty
        !channel.open
        messages.size() == 3
        messages[0] instanceof HttpResponse
        ((HttpContent) messages[1]).content().toString(StandardCharsets.UTF_8) == 'sent'
        // the empty write that the close waits for, so that what was written is sent first
        !((ByteBuf) messages[2]).isReadable()

        cleanup:
        messages.each { ReferenceCountUtil.release(it) }
        channel.finishAndReleaseAll()
    }

    def 'a full response whose compression fails, and fails again when it is discarded, is still reported and closes the connection'() {
        given:
        def written = []
        def failed = []
        def unbound = []
        OutboundAccess outbound = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess access) {
                body.close()
                access.attachment(request.uri())
                outbound = access
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }

            @Override
            void responseFailed(Object attachment, Throwable cause) {
                failed << cause
            }

            @Override
            void handleUnboundError(Throwable cause) {
                unbound << cause
            }
        })
        handler.setZlibCodecFactory(new FailingCodecFactory())
        handler.setCompressionStrategy(new AlwaysCompress())
        def channel = new EmbeddedChannel(handler)
        ByteBuf content = Unpooled.copiedBuffer('content', StandardCharsets.UTF_8)

        when:
        channel.writeInbound(snappyRequest())
        outbound.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(channel).adapt(content))
        channel.runPendingTasks()
        channel.checkException()

        then: 'the failure of the discard does not leave the request unanswered and the connection open'
        failed.size() == 1
        failed[0] instanceof EncoderException
        written == ['/']
        unbound.empty
        !channel.open
        content.refCnt() == 0

        cleanup:
        channel.finishAndReleaseAll()
    }

    def 'a streamed response whose compression fails, and fails again when it is discarded, still lets go of its body'() {
        given:
        def written = []
        def unbound = []
        OutboundAccess outbound = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess access) {
                body.close()
                access.attachment(request.uri())
                outbound = access
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }

            @Override
            void handleUnboundError(Throwable cause) {
                unbound << cause
            }
        })
        handler.setZlibCodecFactory(new FailingCodecFactory())
        handler.setCompressionStrategy(new AlwaysCompress())
        def channel = new EmbeddedChannel(handler)
        def factory = new NettyByteBodyFactory(channel)
        int discards = 0
        def responseBody = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            void allowDiscard() {
                discards++
            }
        })
        ByteBuf piece = Unpooled.copiedBuffer('piece', StandardCharsets.UTF_8)

        when: 'the encoder fails on the first piece of the body'
        channel.writeInbound(snappyRequest())
        outbound.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new StreamingNettyByteBody(responseBody))
        responseBody.add(factory.readBufferFactory().adapt(piece))
        channel.runPendingTasks()
        channel.checkException()

        then:
        discards == 1
        written == ['/']
        unbound.empty
        !channel.open
        piece.refCnt() == 0

        cleanup:
        channel.finishAndReleaseAll()
    }

    private static HttpRequest snappyRequest() {
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, 'snappy')
        request
    }

    private static class AlwaysCompress implements HttpCompressionStrategy {
        @Override
        boolean isEnabled() {
            true
        }

        @Override
        int getMaxZstdEncodeSize() {
            Integer.MAX_VALUE
        }

        @Override
        boolean shouldCompress(HttpResponse response) {
            true
        }
    }

    /**
     * Hands out a snappy encoder that fails to encode, and fails again when it is closed.
     */
    private static class FailingCodecFactory implements NettyZlibCodecFactory {
        @Override
        ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel) {
            DefaultNettyZlibCodecFactory.INSTANCE.newZlibEncoder(wrapper, compressionLevel, windowBits, memLevel)
        }

        @Override
        ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
            DefaultNettyZlibCodecFactory.INSTANCE.newZlibDecoder(wrapper, maxAllocation)
        }

        @Override
        ChannelHandler newSnappyFrameEncoder() {
            new ChannelOutboundHandlerAdapter() {
                @Override
                void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    ReferenceCountUtil.release(msg)
                    throw new CompressionException('encode failed')
                }

                @Override
                void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                    ctx.fireExceptionCaught(new CompressionException('close failed'))
                    ctx.close(promise)
                }
            }
        }
    }
}
