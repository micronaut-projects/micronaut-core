package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.buffer.AbstractByteBufAllocator
import io.netty.buffer.UnpooledHeapByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

/**
 * HTTP/2 request decompression buffers the whole decompressed batch before the body
 * size limit applies.
 */
class Http2RequestDecompressionLimitSpec extends Specification {

    static class PeakAllocator extends AbstractByteBufAllocator {
        final AtomicLong live = new AtomicLong()
        volatile long peak

        PeakAllocator() {
            super(false)
        }

        private ByteBuf track(int initialCapacity, int maxCapacity) {
            PeakAllocator self = this
            return new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity) {
                @Override
                protected byte[] allocateArray(int initialCap) {
                    long now = self.live.addAndGet(initialCap)
                    if (now > self.peak) {
                        self.peak = now
                    }
                    return new byte[initialCap]
                }

                @Override
                protected void freeArray(byte[] array) {
                    self.live.addAndGet(-array.length)
                }
            }
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return track(initialCapacity, maxCapacity)
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            return track(initialCapacity, maxCapacity)
        }

        @Override
        boolean isDirectBufferPooled() {
            return false
        }
    }

    static class DuplexHandler extends Http2ChannelDuplexHandler {
        @Override
        void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.fireChannelRead(msg)
        }
    }

    def "decompressed http2 request body is bounded by the configured limit"() {
        given:
        int limit = 1024 * 1024
        def alloc = new PeakAllocator()
        def server = new EmbeddedChannel()
        server.config().setAllocator(alloc)
        def client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
        List<CloseableByteBody> bodies = []
        server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                bodies.add(body)
            }

            @Override
            void handleUnboundError(Throwable cause) {
            }
        }).bodySizeLimits(new BodySizeLimits(limit, limit)).decompress(true).build())
        def duplex = new DuplexHandler()
        client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), duplex)
        EmbeddedTestUtil.advance(server, client)

        def bos = new ByteArrayOutputStream()
        new GZIPOutputStream(bos).withCloseable { it.write(new byte[60 * 1024 * 1024]) }
        byte[] gz = bos.toByteArray()
        assert gz.length < 65535

        when:
        def stream = duplex.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName()).scheme("http").authority("example.com").path("/")
        headers.add("content-encoding", "gzip")
        client.write(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(gz), true).stream(stream))
        client.flush()
        alloc.peak = 0
        EmbeddedTestUtil.advance(server, client)

        then: "peak memory stays within a small multiple of the configured limit"
        alloc.peak < 4L * limit

        cleanup:
        bodies.each { it.close() }
        // drop the connection: a graceful close would queue a GOAWAY for the open stream
        server?.unsafe()?.close(server.unsafe().voidPromise())
        server?.runPendingTasks()
        server?.finishAndReleaseAll()
        client?.finishAndReleaseAll()
    }

    def "streamed http2 request body may decompress beyond the buffer limit"() {
        given:
        int bufferLimit = 1024 * 1024
        def server = new EmbeddedChannel()
        def client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
        long received = 0
        Throwable failure = null
        boolean completed = false
        server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe({ received += it.length }, { failure = it }, { completed = true })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        }).bodySizeLimits(new BodySizeLimits(4 * bufferLimit, bufferLimit)).decompress(true).build())
        def duplex = new DuplexHandler()
        client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), duplex)
        EmbeddedTestUtil.advance(server, client)

        def bos = new ByteArrayOutputStream()
        new GZIPOutputStream(bos).withCloseable { it.write(new byte[2 * bufferLimit]) }
        byte[] gz = bos.toByteArray()

        when:
        def stream = duplex.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName()).scheme("http").authority("example.com").path("/")
        headers.add("content-encoding", "gzip")
        client.write(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(gz), true).stream(stream))
        client.flush()
        EmbeddedTestUtil.advance(server, client)

        then:
        failure == null
        completed
        received == 2L * bufferLimit

        cleanup:
        // drop the connection: a graceful close would queue a GOAWAY for the open stream
        server?.unsafe()?.close(server.unsafe().voidPromise())
        server?.runPendingTasks()
        server?.finishAndReleaseAll()
        client?.finishAndReleaseAll()
    }
}
