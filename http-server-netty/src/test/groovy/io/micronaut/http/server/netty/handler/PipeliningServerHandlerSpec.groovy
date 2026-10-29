package io.micronaut.http.server.netty.handler

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpStatus
import io.micronaut.http.body.AvailableByteBody
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableAvailableByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.micronaut.http.server.netty.HttpCompressionStrategy
import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.netty.buffer.AbstractByteBufAllocator
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.compression.DecompressionException
import io.netty.handler.codec.compression.SnappyFrameEncoder
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.GZIPOutputStream

class PipeliningServerHandlerSpec extends Specification {
    def 'pipelined requests have their responses batched'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(resp, NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        expect:
        mon.read == 1
        mon.flush == 0

        when:
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        then:
        mon.read == 1
        mon.flush == 0

        when:
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        then:
        mon.read == 1
        mon.flush == 0

        when:
        ch.flushInbound()
        then:
        mon.read == 2
        mon.flush == 1
        ch.readOutbound() == toFull(resp)
        ch.readOutbound() == toFull(resp)
        ch.readOutbound() == null
        ch.checkException()
    }

    def 'streaming responses flush at the end of the event loop turn of every item'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<ByteBuf>onBackpressureBuffer()
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(resp, new NettyByteBodyFactory(ctx.channel()).adaptNetty(sink.asFlux()))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        expect:
        mon.read == 1
        mon.flush == 0

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        then:
        mon.read == 2
        // response is delayed until first content
        mon.flush == 0

        when:
        def c1 = Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8))
        sink.tryEmitNext(c1)
        then: 'the item is written outside a read, so the flush waits for the end of the turn'
        mon.read == 2
        mon.flush == 0
        ch.readOutbound() == null

        when:
        ch.runPendingTasks()
        then:
        mon.flush == 1
        ch.readOutbound() instanceof HttpResponse
        ch.readOutbound() == new DefaultHttpContent(c1)
        ch.readOutbound() == null

        when:
        def c2 = Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8))
        sink.tryEmitNext(c2)
        ch.runPendingTasks()
        then:
        mon.read == 2
        mon.flush == 2
        ch.readOutbound() == new DefaultHttpContent(c2)
        ch.readOutbound() == null
        ch.checkException()
    }

    def 'streaming response pieces written outside a read in one turn share one flush'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def upstream = new RecordingUpstream()
        def streamingBody = null
        def ch = new EmbeddedChannel(mon, new TaskRunner(), new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                streamingBody = new NettyByteBodyFactory(ctx.channel()).createStreamingBody(BodySizeLimits.UNLIMITED, upstream)
                outboundAccess.write(resp, streamingBody.rootBody())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))
        def pieces = (0..<50).collect { "piece $it," }
        def readBuffers = new NettyByteBodyFactory(ch).readBufferFactory()

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        def flushesBefore = mon.flush
        then:
        upstream.starts == 1

        when: 'the body arrives as many small pieces in one event loop turn, outside a read'
        int flushesInTurn = -1
        long consumedInTurn = -1
        // EmbeddedChannel runs pending tasks after every write made from outside one of its own
        // operations, so the pieces are added from inside one, see TaskRunner
        ch.writeOneInbound({
            for (String piece : pieces) {
                streamingBody.sharedBuffer().add(readBuffers.adapt(Unpooled.copiedBuffer(piece, StandardCharsets.UTF_8)))
            }
            flushesInTurn = mon.flush
            consumedInTurn = upstream.consumed
        } as Runnable)
        ch.runPendingTasks()
        def outbound = []
        Object msg
        while ((msg = ch.readOutbound()) != null) {
            outbound << msg
        }
        then: 'the flush waits for the end of the turn, and one flush covers all of them'
        ch.checkException()
        flushesInTurn == flushesBefore
        mon.flush == flushesBefore + 1

        and: 'the writable channel accepts bytes into the bounded aggregate'
        consumedInTurn == pieces.sum { it.length() }
        upstream.consumed == pieces.sum { it.length() }

        and: 'adjacent small pieces share one bounded content message'
        outbound[0] == resp
        outbound.size() == 2
        outbound.drop(1).every { it instanceof HttpContent && !(it instanceof LastHttpContent) }
        outbound.drop(1).collect { ((HttpContent) it).content().toString(StandardCharsets.UTF_8) } == [pieces.join('')]

        when: 'the body completes in a later turn'
        streamingBody.sharedBuffer().complete()
        ch.runPendingTasks()
        then:
        mon.flush == flushesBefore + 2
        ch.readOutbound() == LastHttpContent.EMPTY_LAST_CONTENT
        ch.readOutbound() == null

        cleanup:
        outbound.each { if (it instanceof HttpContent) it.release() }
        ch.finishAndReleaseAll()
    }

    def 'the final piece of a streaming response is the terminating message'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def upstream = new RecordingUpstream()
        def streamingBody = null
        def cleaned = 0
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                streamingBody = new NettyByteBodyFactory(ctx.channel()).createStreamingBody(BodySizeLimits.UNLIMITED, upstream)
                outboundAccess.write(resp, streamingBody.rootBody())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                cleaned++
            }
        }))
        def readBuffers = new NettyByteBodyFactory(ch).readBufferFactory()

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        streamingBody.sharedBuffer().add(readBuffers.adapt(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)))
        streamingBody.sharedBuffer().addAndComplete(readBuffers.adapt(Unpooled.copiedBuffer("bar", StandardCharsets.UTF_8)))
        ch.runPendingTasks()
        then:
        ch.checkException()
        cleaned == 1
        ch.readOutbound() == resp
        LastHttpContent last = ch.readOutbound()
        last.content().toString(StandardCharsets.UTF_8) == "foobar"
        ch.readOutbound() == null

        cleanup:
        last?.release()
        ch.finishAndReleaseAll()
    }

    def 'writability handling is protocol-specific'() {
        given:
        int emitted = 0
        boolean blockWrites = true
        def ch = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (blockWrites && msg instanceof HttpContent) {
                    ctx.channel().unsafe().outboundBuffer().setUserDefinedWritability(1, false)
                }
                ctx.write(msg, promise)
            }
        }, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                // Exceed the 8 KiB aggregation window so the producer must observe backpressure.
                def content = Flux.range(0, 32)
                    .map { Unpooled.wrappedBuffer(new byte[1024]) }
                    .doOnNext { emitted++ }
                outboundAccess.write(response, new NettyByteBodyFactory(ctx.channel()).adaptNetty(content))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                throw cause
            }
        }, quic))
        def outboundBuffer = ch.unsafe().outboundBuffer()

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        int initiallyEmitted = emitted

        then:
        initiallyEmitted > 0
        initiallyEmitted < 32
        !ch.isWritable()

        when:
        blockWrites = false
        // QUIC can signal writability while draining queued writes, then exhaust its capacity.
        outboundBuffer.setUserDefinedWritability(1, true)
        ch.pipeline().fireChannelWritabilityChanged()
        outboundBuffer.setUserDefinedWritability(1, false)

        then:
        emitted == (quic ? initiallyEmitted : 32)

        when:
        ch.runPendingTasks()

        then:
        emitted == (quic ? initiallyEmitted : 32)

        when:
        outboundBuffer.setUserDefinedWritability(1, true)
        ch.runPendingTasks()

        then:
        emitted == 32

        cleanup:
        ch.finishAndReleaseAll()

        where:
        quic << [false, true]
    }

    def 'requests that come in a single packet are accumulated'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                assert body instanceof AvailableByteBody
                assert new String(body.toByteArray(), StandardCharsets.UTF_8) == "foobar"
                outboundAccess.write(resp, NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        expect:
        mon.read == 1
        mon.flush == 0

        when:
        def req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 6)
        ch.writeInbound(
                req,
                new DefaultHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8))),
                new DefaultLastHttpContent(Unpooled.wrappedBuffer("bar".getBytes(StandardCharsets.UTF_8)))
        )
        then:
        ch.checkException()
        mon.read == 2
        mon.flush == 1
        ch.readOutbound() == toFull(resp)
        ch.readOutbound() == null
    }

    static FullHttpResponse toFull(HttpResponse response) {
        return new DefaultFullHttpResponse(response.protocolVersion, response.status, Unpooled.EMPTY_BUFFER, response.headers(), EmptyHttpHeaders.INSTANCE)
    }

    def 'continue support'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).collectList().subscribe { outboundAccess.write(resp, NettyByteBodyFactory.empty()) }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        expect:
        mon.read == 1
        mon.flush == 0

        when:
        def req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        req.headers().add(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        ch.writeInbound(req)
        then:
        HttpResponse cont = ch.readOutbound()
        cont.status() == HttpResponseStatus.CONTINUE
        ch.readOutbound() == null

        when:
        ch.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8))))
        then:
        ch.readOutbound() == toFull(resp)
        ch.readOutbound() == null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9366')
    def 'nested write'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                super.write(ctx, msg, promise)
                ctx.fireChannelWritabilityChanged()
            }
        }, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(resp, NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        expect:
        mon.read == 1
        mon.flush == 0

        when:
        def req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER)
        ch.writeInbound(req)
        then:
        ch.checkException()
        mon.read == 2
        mon.flush == 1
        ch.readOutbound() == toFull(resp)
        ch.readOutbound() == null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9366')
    def 'responseWritten not called on close'(boolean completeOnCancel) {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<ByteBuf>onBackpressureBuffer()
        def cleaned = 0
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                assert request instanceof FullHttpRequest
                body.close()
                outboundAccess.write(resp, new NettyByteBodyFactory(ctx.channel()).adaptNetty(sink.asFlux().doOnCancel {
                    // optional extra weirdness: onComplete *after* cancel. Could lead to double call to responseWritten, if I was an idiot.
                    if (completeOnCancel) sink.tryEmitComplete()
                }))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                cleaned++
            }
        }))

        when:
        def req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER)
        ch.writeOneInbound(req)
        ch.flushInbound()
        def c1 = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        sink.emitNext(c1, Sinks.EmitFailureHandler.FAIL_FAST)
        ch.runPendingTasks()
        then:
        ch.checkException()
        ch.readOutbound() == resp
        ch.readOutbound() == new DefaultHttpContent(c1)

        when:
        ch.close()
        then:
        ch.checkException()
        ch.readOutbound() == null
        cleaned == 1

        where:
        completeOnCancel << [true, false]
    }

    def 'read backpressure for streaming requests'() {
        given:
        def mon = new MonitorHandler()
        Subscription subscription = null
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.toByteArrayPublisher().subscribe(new Subscriber<byte[]>() {
                    @Override
                    void onSubscribe(Subscription s) {
                        subscription = s
                    }

                    @Override
                    void onNext(byte[] httpContent) {
                    }

                    @Override
                    void onError(Throwable t) {
                        t.printStackTrace()
                    }

                    @Override
                    void onComplete() {
                        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
                    }
                })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        expect:
        mon.read == 1
        mon.flush == 0

        when:
        def req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        ch.writeInbound(req)
        then:
        // no read call until request
        // ok i reconsidered this. similar to http/2, PipeliningServerHandler now accepts 64K before downstream demand happens.
        mon.read == 2

        when:
        subscription.request(1)
        then:
        mon.read == 2

        when:
        ch.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8))))
        then:
        // read call for the next request
        mon.read == 3
        ch.checkException()
    }

    def 'decompression parts to full'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        HttpRequest req = null
        CloseableAvailableByteBody ibb = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                req = request
                ibb = body
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))
        def compChannel = new EmbeddedChannel(compressor)
        byte[] uncompressed = new byte[1024]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        compChannel.writeOutbound(Unpooled.copiedBuffer(uncompressed))
        compChannel.finish()

        when:
        def r = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        r.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeOneInbound(r)
        while (true) {
            ByteBuf o = compChannel.readOutbound()
            if (o == null) {
                break
            }
            ch.writeOneInbound(new DefaultHttpContent(o))
        }
        ch.writeOneInbound(LastHttpContent.EMPTY_LAST_CONTENT)
        ch.flushInbound()
        then:
        !req.headers().contains(HttpHeaderNames.CONTENT_ENCODING)
        Arrays.equals(ibb.toByteArray(), uncompressed)

        cleanup:
        ibb.close()

        where:
        contentEncoding            | compressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.X_GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.X_DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.SNAPPY    | new SnappyFrameEncoder()
    }

    def 'decompression full to full'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        HttpRequest req = null
        CloseableAvailableByteBody ibb = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                req = request
                ibb = body
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))
        def compChannel = new EmbeddedChannel(compressor)
        byte[] uncompressed = new byte[1024]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        compChannel.writeOutbound(Unpooled.copiedBuffer(uncompressed))
        compChannel.finish()
        CompositeByteBuf compressed = Unpooled.compositeBuffer()
        while (true) {
            ByteBuf o = compChannel.readOutbound()
            if (o == null) {
                break
            }
            compressed.addComponent(true, o)
        }

        when:
        def r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", compressed)
        r.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeInbound(r)
        then:
        !req.headers().contains(HttpHeaderNames.CONTENT_ENCODING)
        Arrays.equals(ibb.toByteArray(), uncompressed)

        cleanup:
        ibb.close()

        where:
        contentEncoding            | compressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.X_GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.X_DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.SNAPPY    | new SnappyFrameEncoder()
    }

    def 'decompression parts to stream'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        HttpRequest req = null
        CloseableByteBody sbb = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                req = request
                sbb = body
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))
        def compChannel = new EmbeddedChannel(compressor)
        byte[] uncompressed = new byte[1024]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        compChannel.writeOutbound(Unpooled.copiedBuffer(uncompressed))
        compChannel.finish()

        when:
        def r = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        r.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeOneInbound(r)
        ch.flushInbound()
        while (true) {
            ByteBuf o = compChannel.readOutbound()
            if (o == null) {
                break
            }
            ch.writeOneInbound(new DefaultHttpContent(o))
            ch.flushInbound()
        }
        ch.writeOneInbound(LastHttpContent.EMPTY_LAST_CONTENT)
        ch.flushInbound()
        then:
        !req.headers().contains(HttpHeaderNames.CONTENT_ENCODING)
        CompositeByteBuf decompressed = Unpooled.compositeBuffer()
        for (ByteBuffer<?> c : Flux.from(sbb.toByteBufferPublisher()).toIterable()) {
            decompressed.addComponent(true, c.asNativeBuffer())
        }
        decompressed.equals(Unpooled.wrappedBuffer(uncompressed))

        cleanup:
        decompressed.release()

        where:
        contentEncoding            | compressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.X_GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.X_DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.SNAPPY    | new SnappyFrameEncoder()
    }

    def 'decompression enforces max body size before last content'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        Throwable failure = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe({ }, { failure = it })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        handler.setBodySizeLimits(new BodySizeLimits(64, Integer.MAX_VALUE))
        def ch = new EmbeddedChannel(handler)
        def compChannel = new EmbeddedChannel(compressor)
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[1024]))
        compChannel.finish()
        CompositeByteBuf compressed = Unpooled.compositeBuffer()
        ByteBuf part
        while ((part = compChannel.readOutbound()) != null) {
            compressed.addComponent(true, part)
        }

        when:
        def requestMessage = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        requestMessage.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeOneInbound(requestMessage)
        ch.writeOneInbound(new DefaultHttpContent(compressed))

        then:
        failure instanceof ContentLengthExceededException

        cleanup:
        ch.finishAndReleaseAll()
        compChannel.finishAndReleaseAll()

        where:
        contentEncoding          | compressor
        HttpHeaderValues.GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
    }

    def 'decompression limit is cumulative across chunks'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        Throwable failure = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe({ }, { failure = it })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        handler.setBodySizeLimits(new BodySizeLimits(64, Integer.MAX_VALUE))
        def ch = new EmbeddedChannel(handler)
        def compChannel = new EmbeddedChannel(compressor)
        def requestMessage = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        requestMessage.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeOneInbound(requestMessage)

        when:
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[48]))
        forwardCompressed(compChannel, ch)

        then:
        failure == null

        when:
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[48]))
        forwardCompressed(compChannel, ch)

        then:
        failure instanceof ContentLengthExceededException

        cleanup:
        ch.finishAndReleaseAll()
        compChannel.finishAndReleaseAll()

        where:
        contentEncoding          | compressor
        HttpHeaderValues.GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
    }

    def 'streaming decompression limit is cumulative across chunks'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        CloseableByteBody body = null
        Throwable failure = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody requestBody, OutboundAccess outboundAccess) {
                body = requestBody
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        // Netty 4.2.18 requests at least 512 writable bytes when growing the decompression
        // buffer, including after inflating a chunk. Allow enough headroom so this test
        // reaches the cumulative 64-byte body limit instead of the decoder allocation limit.
        // See https://github.com/netty/netty/pull/17370
        handler.setBodySizeLimits(new BodySizeLimits(64, 1024))
        def ch = new EmbeddedChannel(handler)
        def compChannel = new EmbeddedChannel(compressor)
        def requestMessage = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        requestMessage.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeOneInbound(requestMessage)
        ch.flushInbound()
        Flux.from(body.toByteArrayPublisher()).subscribe({ }, { failure = it })

        when:
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[48]))
        forwardCompressed(compChannel, ch)

        then:
        failure == null

        when:
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[48]))
        forwardCompressed(compChannel, ch)

        then:
        failure instanceof ContentLengthExceededException

        cleanup:
        body?.close()
        ch.finishAndReleaseAll()
        compChannel.finishAndReleaseAll()

        where:
        contentEncoding          | compressor
        HttpHeaderValues.GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
    }

    def 'decompression accepts body at max size'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        CloseableAvailableByteBody body = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody requestBody, OutboundAccess outboundAccess) {
                body = requestBody
            }

            @Override
            void handleUnboundError(Throwable cause) {
                throw cause
            }
        })
        handler.setBodySizeLimits(new BodySizeLimits(64, Integer.MAX_VALUE))
        def ch = new EmbeddedChannel(handler)
        def compChannel = new EmbeddedChannel(compressor)
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[64]))
        compChannel.finish()
        CompositeByteBuf compressed = Unpooled.compositeBuffer()
        ByteBuf part
        while ((part = compChannel.readOutbound()) != null) {
            compressed.addComponent(true, part)
        }

        when:
        def requestMessage = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", compressed)
        requestMessage.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeOneInbound(requestMessage)

        then:
        body.toByteArray().length == 64

        cleanup:
        body?.close()
        ch.finishAndReleaseAll()
        compChannel.finishAndReleaseAll()

        where:
        contentEncoding          | compressor
        HttpHeaderValues.GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
    }

    private static void forwardCompressed(EmbeddedChannel source, EmbeddedChannel destination) {
        ByteBuf compressed
        while ((compressed = source.readOutbound()) != null) {
            destination.writeOneInbound(new DefaultHttpContent(compressed))
        }
    }

    def 'malformed compressed request remains a decompression error'() {
        given:
        Throwable failure = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe({ }, { failure = it })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        handler.setBodySizeLimits(new BodySizeLimits(64, Integer.MAX_VALUE))
        def ch = new EmbeddedChannel(handler)

        when:
        def requestMessage = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        requestMessage.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        ch.writeOneInbound(requestMessage)
        ch.writeOneInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[32])))

        then:
        failure instanceof DecompressionException

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'malformed chunk fails the request body and closes the connection (#description, split=#split, gzip=#gzip)'() {
        given:
        Throwable failure = null
        boolean completed = false
        int received = 0
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe({ received += it.length }, {
                    failure = it
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST), NettyByteBodyFactory.empty())
                }, {
                    completed = true
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
                })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })
        def ch = new EmbeddedChannel(new HttpRequestDecoder(), handler)
        byte[] firstChunk = "hello".getBytes(StandardCharsets.UTF_8)
        if (gzip) {
            def bos = new ByteArrayOutputStream()
            new GZIPOutputStream(bos).withCloseable { it.write(firstChunk) }
            firstChunk = bos.toByteArray()
        }
        String head = "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n" +
            (gzip ? "Content-Encoding: gzip\r\n" : "") +
            "\r\n" + Integer.toHexString(firstChunk.length) + "\r\n" + new String(firstChunk, StandardCharsets.ISO_8859_1) + "\r\n"

        when:
        if (split) {
            ch.writeInbound(Unpooled.copiedBuffer(head, StandardCharsets.ISO_8859_1))
            ch.writeInbound(Unpooled.copiedBuffer(malformed, StandardCharsets.ISO_8859_1))
        } else {
            ch.writeInbound(Unpooled.copiedBuffer(head + malformed, StandardCharsets.ISO_8859_1))
        }

        then:
        !completed
        failure instanceof HttpStatusException
        ((HttpStatusException) failure).status == status
        failure.cause != null

        and: 'the response is written and the connection is closed afterwards'
        HttpResponse response = ch.readOutbound()
        response.status() == HttpResponseStatus.BAD_REQUEST
        response.headers().get(HttpHeaderNames.CONNECTION) == 'close'
        !ch.isOpen()

        cleanup:
        ch.finishAndReleaseAll()

        where:
        [description, malformed, status, split, gzip] << [
            [
                ['chunk extension too long', '5;' + 'a' * 5000 + '\r\nhello\r\n0\r\n\r\n', HttpStatus.REQUEST_ENTITY_TOO_LARGE],
                ['invalid chunk size', 'xyz\r\nhello\r\n0\r\n\r\n', HttpStatus.BAD_REQUEST],
                ['invalid trailer', '0\r\ninvalid trailer\r\n\r\n', HttpStatus.BAD_REQUEST],
            ],
            [false, true],
            [false, true],
        ].combinations().collect { it.flatten() }
    }

    def 'failed content without a request closes the connection'() {
        given:
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                throw new AssertionError()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                throw new AssertionError(cause)
            }
        }))
        def content = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER)
        content.setDecoderResult(DecoderResult.failure(new IllegalArgumentException("invalid chunk")))

        when:
        ch.writeInbound(content)

        then:
        !ch.isOpen()
        ch.checkException()

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'malformed chunk does not drop an early response that is not flushed yet'() {
        given:
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe(new Subscriber<byte[]>() {
                    Subscription subscription

                    @Override
                    void onSubscribe(Subscription s) {
                        subscription = s
                        s.request(1)
                    }

                    @Override
                    void onNext(byte[] bytes) {
                        // reject the request early and discard the rest of the body
                        subscription.cancel()
                        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED), NettyByteBodyFactory.empty())
                    }

                    @Override
                    void onError(Throwable t) {
                    }

                    @Override
                    void onComplete() {
                    }
                })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })
        def ch = new EmbeddedChannel(new HttpRequestDecoder(), handler)

        when: 'the request head arrives, and the body is streamed to the handler'
        ch.writeInbound(Unpooled.copiedBuffer("POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n", StandardCharsets.ISO_8859_1))
        then:
        ch.readOutbound() == null

        when: 'the handler responds on the first chunk, and a malformed chunk follows in the same read'
        ch.writeInbound(Unpooled.copiedBuffer("5\r\nhello\r\nxyz\r\n", StandardCharsets.ISO_8859_1))

        then: 'the response is still written, and the connection is closed afterwards'
        HttpResponse response = ch.readOutbound()
        response.status() == HttpResponseStatus.UNAUTHORIZED
        !ch.isOpen()

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'decompression limit applies across concatenated gzip members'() {
        given:
        Throwable failure = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe({ }, { failure = it })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        handler.setBodySizeLimits(new BodySizeLimits(64, Integer.MAX_VALUE))
        def ch = new EmbeddedChannel(handler)
        CompositeByteBuf compressed = Unpooled.compositeBuffer()
        3.times {
            def compChannel = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP))
            compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[48]))
            compChannel.finish()
            ByteBuf part
            while ((part = compChannel.readOutbound()) != null) {
                compressed.addComponent(true, part)
            }
            compChannel.finishAndReleaseAll()
        }

        when:
        def requestMessage = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        requestMessage.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        ch.writeOneInbound(requestMessage)
        ch.writeOneInbound(new DefaultHttpContent(compressed))

        then:
        failure instanceof ContentLengthExceededException

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'oversized final content does not drop the next request'() {
        given:
        def requests = []
        Throwable failure = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                requests << request.uri()
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        handler.setBodySizeLimits(new BodySizeLimits(2, Integer.MAX_VALUE))
        def ch = new EmbeddedChannel(handler)

        when:
        def oversized = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/oversized")
        oversized.headers().set(HttpHeaderNames.CONTENT_LENGTH, 3)
        ch.writeOneInbound(oversized)
        ch.writeOneInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[3])))
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/next"))

        then:
        failure instanceof ContentLengthExceededException
        requests == ["/oversized", "/next"]

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'oversized compressed final content does not drop the next request'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        def requests = []
        Throwable failure = null
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                requests << request.uri()
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        handler.setBodySizeLimits(new BodySizeLimits(64, Integer.MAX_VALUE))
        def ch = new EmbeddedChannel(handler)
        def compChannel = new EmbeddedChannel(compressor)
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[1024]))
        compChannel.finish()
        CompositeByteBuf compressed = Unpooled.compositeBuffer()
        ByteBuf part
        while ((part = compChannel.readOutbound()) != null) {
            compressed.addComponent(true, part)
        }

        when:
        def oversized = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/oversized", compressed)
        oversized.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        ch.writeOneInbound(oversized)
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/next"))

        then:
        failure instanceof ContentLengthExceededException
        requests == ["/oversized", "/next"]

        cleanup:
        ch.finishAndReleaseAll()
        compChannel.finishAndReleaseAll()

        where:
        contentEncoding          | compressor
        HttpHeaderValues.GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
    }

    def 'empty streaming response while in queue'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<ByteBuf>onBackpressureBuffer()
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            int i = 0

            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                if (i++ == 0) {
                    outboundAccess.write(resp, new NettyByteBodyFactory(ctx.channel()).adaptNetty(sink.asFlux()))
                } else {
                    outboundAccess.write(resp, new NettyByteBodyFactory(ctx.channel()).adaptNetty(Flux.empty()))
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        then:
        ch.readOutbound() == null

        when:
        sink.tryEmitComplete()
        ch.runPendingTasks()
        then:
        ch.readOutbound() == resp
        ch.readOutbound() == LastHttpContent.EMPTY_LAST_CONTENT
        ch.readOutbound() == resp
        ch.readOutbound() == LastHttpContent.EMPTY_LAST_CONTENT
        ch.readOutbound() == null
    }

    def 'responseWritten always called'() {
        given:
        int unwritten = 0
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                unwritten++
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
            }

            @Override
            void responseWritten(Object attachment) {
                unwritten--
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        // note: this relies on channelReadComplete never being called, which is a bit unrealistic. channelReadComplete
        // causes a flush, which for EmbeddedChannel, clears the outbound buffer and thus clears the backlog.
        while (true) {
            boolean writableBefore = ch.writable
            ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
            ch.checkException()
            ch.runPendingTasks()
            if (!writableBefore) {
                break
            }
        }
        ch.finishAndReleaseAll()
        then:
        unwritten == 0
    }

    def 'responseWritten reports the response status and body bytes'() {
        given:
        HttpStatus reportedStatus = null
        long reportedBytes = -1
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8)
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED),
                    new NettyByteBodyFactory(ctx.channel()).adapt(Unpooled.copiedBuffer(payload)))
            }

            @Override
            void responseWritten(Object attachment, HttpStatus status, long bytesWritten) {
                reportedStatus = status
                reportedBytes = bytesWritten
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))

        then:
        ch.checkException()
        reportedStatus == HttpStatus.ACCEPTED
        reportedBytes == payload.length

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'responseWritten reports post-compression bytes'() {
        given:
        HttpStatus reportedStatus = null
        long reportedBytes = -1
        byte[] payload = new byte[10_000]
        java.util.Arrays.fill(payload, (byte) 'A')
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK),
                    new NettyByteBodyFactory(ctx.channel()).adapt(Unpooled.copiedBuffer(payload)))
            }

            @Override
            void responseWritten(Object attachment, HttpStatus status, long bytesWritten) {
                reportedStatus = status
                reportedBytes = bytesWritten
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        })
        handler.setCompressor(Compressor.create(new HttpCompressionStrategy() {
            @Override
            boolean isEnabled() {
                return true
            }

            @Override
            boolean shouldCompress(HttpResponse response) {
                return true
            }

            @Override
            int getMaxZstdEncodeSize() {
                return 1 << 20
            }
        }))
        def ch = new EmbeddedChannel(handler)
        def outboundRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        outboundRequest.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)

        when:
        ch.writeInbound(outboundRequest)
        HttpResponse wireResponse = null
        long wireBytes = 0
        Object outbound
        while ((outbound = ch.readOutbound()) != null) {
            if (outbound instanceof HttpResponse) {
                wireResponse = (HttpResponse) outbound
            }
            if (outbound instanceof HttpContent) {
                def content = ((HttpContent) outbound).content()
                wireBytes += content.readableBytes()
                content.release()
            }
        }

        then:
        ch.checkException()
        wireResponse.headers().get(HttpHeaderNames.CONTENT_ENCODING) == 'gzip'
        reportedStatus == HttpStatus.OK
        reportedBytes == wireBytes
        reportedBytes > 0
        // 10_000 identical bytes compress to far less than the uncompressed payload
        reportedBytes < payload.length

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'responseWritten handles an unregistered status code'() {
        given:
        HttpStatus reportedStatus = null
        long reportedBytes = -1
        byte[] payload = "custom".getBytes(StandardCharsets.UTF_8)
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(299, "Custom")),
                    new NettyByteBodyFactory(ctx.channel()).adapt(Unpooled.copiedBuffer(payload)))
            }

            @Override
            void responseWritten(Object attachment, HttpStatus status, long bytesWritten) {
                reportedStatus = status
                reportedBytes = bytesWritten
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))

        then:
        ch.checkException()
        reportedStatus == null
        reportedBytes == payload.length

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'responseWritten reports zero bytes for a head response'() {
        given:
        HttpStatus reportedStatus = null
        long reportedBytes = -1
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.writeHeadResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
            }

            @Override
            void responseWritten(Object attachment, HttpStatus status, long bytesWritten) {
                reportedStatus = status
                reportedBytes = bytesWritten
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "/"))

        then:
        ch.checkException()
        reportedStatus == HttpStatus.OK
        reportedBytes == 0

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'responseWritten reports the status and bytes of each pipelined response'() {
        given:
        List<HttpStatus> statuses = []
        List<Long> byteCounts = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                boolean first = request.uri() == "/first"
                byte[] payload = (first ? "one" : "three").getBytes(StandardCharsets.UTF_8)
                outboundAccess.write(
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1, first ? HttpResponseStatus.OK : HttpResponseStatus.ACCEPTED),
                    new NettyByteBodyFactory(ctx.channel()).adapt(Unpooled.copiedBuffer(payload)))
            }

            @Override
            void responseWritten(Object attachment, HttpStatus status, long bytesWritten) {
                statuses << status
                byteCounts << bytesWritten
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second"))

        then:
        ch.checkException()
        statuses == [HttpStatus.OK, HttpStatus.ACCEPTED]
        byteCounts == [3L, 5L]

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'reentrant close'() {
        given:
        def resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                def split = body.split(ByteBody.SplitBackpressureMode.FASTEST)
                Flux.from(split.toByteArrayPublisher())
                    .subscribe {
                        body.close()
                        outboundAccess.writeFull(resp)
                    }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))


        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        request.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        when:
        ch.writeInbound(request)
        ch.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)))

        then:
        ch.checkException()
    }

    def 'streaming response that fails after some data is cleaned up and discarded exactly once'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<ByteBuf> onBackpressureBuffer()
        def cleaned = 0
        def discarded = 0
        def errors = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(resp, new NettyByteBodyFactory(ctx.channel())
                        .adapt(sink.asFlux(), null, () -> discarded++))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }

            @Override
            void responseWritten(Object attachment) {
                cleaned++
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER))
        def c1 = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        sink.emitNext(c1, Sinks.EmitFailureHandler.FAIL_FAST)
        ch.runPendingTasks()
        then:
        ch.checkException()
        ch.readOutbound() == resp
        ch.readOutbound() == new DefaultHttpContent(c1)
        cleaned == 0
        discarded == 0

        when:
        sink.emitError(new RuntimeException("stream failed"), Sinks.EmitFailureHandler.FAIL_FAST)
        ch.runPendingTasks()
        ch.finishAndReleaseAll()
        then:
        ch.checkException()
        // the request is cleaned up once, and the remaining response data is discarded once
        cleaned == 1
        discarded == 1
        errors.empty
    }

    def 'inbound state is reset when the request handler rejects a buffered request'() {
        given:
        def requests = []
        def errors = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            boolean fail = true

            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                requests << request.uri()
                if (fail) {
                    fail = false
                    throw new RuntimeException("accept failed")
                }
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }
        }))

        when:
        def first = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/first")
        first.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        def content = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        ch.writeInbound(
                first,
                new DefaultLastHttpContent(content),
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second", Unpooled.EMPTY_BUFFER)
        )

        then:
        // the failure is reported, the request body is released, and the next request is not
        // misinterpreted as content of the failed one: the failed request is answered with a 500
        // that closes the connection, so the request pipelined after it is dropped
        errors.size() == 1
        errors[0].message == "accept failed"
        content.refCnt() == 0
        requests == ["/first"]
        HttpResponse response = ch.readOutbound()
        response.status() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        response.headers().get(HttpHeaderNames.CONNECTION) == 'close'
        !ch.open

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a request the request handler rejects is answered and the connection closed'(String kind) {
        given:
        def errors = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                throw new IllegalArgumentException("accept failed")
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }
        }))

        when:
        HttpRequest req
        switch (kind) {
            case 'full':
                req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER)
                break
            case 'bodyless':
                req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
                break
            default:
                // a body that has not fully arrived by read complete is passed on as a stream
                req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
                req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        }
        ch.writeInbound(req)

        then:
        // the request can never be handled, so it is answered and the connection closed rather
        // than left open waiting for a response that never comes
        errors.size() == 1
        errors[0].message == "accept failed"
        HttpResponse response = ch.readOutbound()
        response.status() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        response.headers().get(HttpHeaderNames.CONNECTION) == 'close'
        !ch.open

        cleanup:
        ch.finishAndReleaseAll()

        where:
        kind << ['full', 'bodyless', 'streaming']
    }

    def 'a request handler that fails after starting a response'(boolean expectContinue, boolean respond) {
        given:
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                // demanding the body sends the 100 Continue, if one is expected
                Flux.from(body.toByteArrayPublisher()).subscribe()
                if (respond) {
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
                }
                throw new IllegalArgumentException("accept failed")
            }

            @Override
            void handleUnboundError(Throwable cause) {
            }
        }))

        when:
        def req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        if (expectContinue) {
            req.headers().add(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        }
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        ch.writeInbound(req)

        then:
        if (expectContinue) {
            assert ((HttpResponse) ch.readOutbound()).status() == HttpResponseStatus.CONTINUE
        }
        // the response the handler wrote is kept, otherwise the request is answered with a 500
        ((HttpResponse) ch.readOutbound()).status() == (respond ? HttpResponseStatus.NO_CONTENT : HttpResponseStatus.INTERNAL_SERVER_ERROR)
        !ch.outboundMessages().any { it instanceof HttpResponse }
        !ch.open

        cleanup:
        ch.finishAndReleaseAll()

        where:
        expectContinue | respond
        true           | true
        true           | false
        false          | true
    }

    def 'the responses of earlier pipelined requests are written before a rejected request closes the connection'() {
        given:
        OutboundAccess first = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                if (request.uri() == "/first") {
                    first = outboundAccess
                } else {
                    throw new IllegalArgumentException("accept failed")
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
            }
        }))

        when:
        ch.writeInbound(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first", Unpooled.EMPTY_BUFFER),
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second", Unpooled.EMPTY_BUFFER)
        )
        then:
        ch.open
        ch.readOutbound() == null

        when:
        first.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
        ch.runPendingTasks()
        then:
        ((HttpResponse) ch.readOutbound()).status() == HttpResponseStatus.NO_CONTENT
        ((HttpResponse) ch.readOutbound()).status() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        !ch.open

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'graceful shutdown sets connection close on an already prepared response'() {
        given:
        def errors = []
        def streamed = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        streamed.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<ByteBuf> onBackpressureBuffer()
        def handler = new PipeliningServerHandler(new RequestHandler() {
            int i = 0

            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                if (i++ == 0) {
                    outboundAccess.write(streamed, new NettyByteBodyFactory(ctx.channel()).adaptNetty(sink.asFlux()))
                } else {
                    def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
                    // the response was prepared for a connection that is still expected to be reused
                    resp.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                    outboundAccess.write(resp, NettyByteBodyFactory.empty())
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }
        })
        def ch = new EmbeddedChannel(handler)

        when:
        // the second response is fully prepared while the first one is still streaming
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first", Unpooled.EMPTY_BUFFER))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second", Unpooled.EMPTY_BUFFER))
        handler.shutdownGracefully()
        sink.tryEmitComplete()
        ch.runPendingTasks()

        then:
        ch.checkException()
        ch.readOutbound() == streamed
        ch.readOutbound() == LastHttpContent.EMPTY_LAST_CONTENT
        FullHttpResponse second = ch.readOutbound()
        // exactly one connection directive, the keep-alive it was prepared with is replaced
        second.headers().getAll(HttpHeaderNames.CONNECTION) == [HttpHeaderValues.CLOSE.toString()]
        ch.readOutbound() == null
        !ch.open
        errors.empty

        cleanup:
        second?.release()
        ch.finishAndReleaseAll()
    }

    def 'a queued streaming response that fails is handled when it is up for writing'() {
        given:
        def firstResp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        firstResp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def secondResp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        secondResp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<ByteBuf> onBackpressureBuffer()
        def upstream = new RecordingUpstream()
        def secondBody = null
        def cleaned = 0
        def errors = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            int i = 0

            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                def factory = new NettyByteBodyFactory(ctx.channel())
                if (i++ == 0) {
                    outboundAccess.write(firstResp, factory.adaptNetty(sink.asFlux()))
                } else {
                    secondBody = factory.createStreamingBody(BodySizeLimits.UNLIMITED, upstream)
                    outboundAccess.write(secondResp, secondBody.rootBody())
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }

            @Override
            void responseWritten(Object attachment) {
                cleaned++
            }
        }))

        when:
        // the second response is queued behind the first one, which is still streaming
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first", Unpooled.EMPTY_BUFFER))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second", Unpooled.EMPTY_BUFFER))
        secondBody.sharedBuffer().error(new RuntimeException("second failed"))
        ch.runPendingTasks()

        then:
        ch.checkException()
        // the response that is currently being written is not cut off by the failure of the
        // queued one, and the failed response is not started
        ch.open
        cleaned == 0
        upstream.starts == 0
        upstream.discards == 0

        when:
        def c1 = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        sink.emitNext(c1, Sinks.EmitFailureHandler.FAIL_FAST)
        sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)
        ch.runPendingTasks()

        then:
        ch.checkException()
        ch.readOutbound() == firstResp
        LastHttpContent completed = ch.readOutbound()
        completed.content().toString(StandardCharsets.UTF_8) == "foo"
        completed.release()
        // the failed response is discarded instead of being written, and the connection is closed
        ch.readOutbound() == null
        !ch.open
        cleaned == 2
        upstream.discards == 1
        errors.empty

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'an error after the handler was removed is ignored'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def upstream = new RecordingUpstream()
        def streamingBody = null
        def cleaned = 0
        def errors = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                streamingBody = new NettyByteBodyFactory(ctx.channel()).createStreamingBody(BodySizeLimits.UNLIMITED, upstream)
                outboundAccess.write(resp, streamingBody.rootBody())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }

            @Override
            void responseWritten(Object attachment) {
                cleaned++
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER))
        then:
        // the response is up for writing, but has not completed
        upstream.starts == 1
        cleaned == 0

        when:
        // the connection goes away while the response is still streaming
        ch.finishAndReleaseAll()
        then:
        cleaned == 1
        upstream.discards == 1

        when:
        // the publisher only finds out afterwards
        def errorsOnClose = errors.size()
        streamingBody.sharedBuffer().error(new RuntimeException("stream failed"))
        then:
        // the late error does not clean up or discard the response a second time
        ch.checkException()
        cleaned == 1
        upstream.discards == 1
        errors.size() == errorsOnClose
    }

    def 'responseWritten is called once when the final bytes arrive with the completion'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def upstream = new RecordingUpstream()
        def streamingBody = null
        PipeliningServerHandler.OutboundAccessImpl access = null
        def cleaned = 0
        def errors = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                access = outboundAccess
                streamingBody = new NettyByteBodyFactory(ctx.channel()).createStreamingBody(BodySizeLimits.UNLIMITED, upstream)
                outboundAccess.write(resp, streamingBody.rootBody())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }

            @Override
            void responseWritten(Object attachment) {
                cleaned++
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER))
        then:
        upstream.starts == 1
        cleaned == 0

        when:
        // the last bytes of the body and its completion arrive as a single signal
        streamingBody.sharedBuffer().addAndComplete(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)))
        ch.runPendingTasks()
        then:
        ch.checkException()
        ch.readOutbound() == resp
        LastHttpContent last = ch.readOutbound()
        last.content().toString(StandardCharsets.UTF_8) == "foo"
        last.release()
        ch.readOutbound() == null
        cleaned == 1

        when:
        // the response is reported as done again, as a discard of the handler would do, and then
        // the connection goes away
        access.handler.markResponseWritten()
        ch.finishAndReleaseAll()
        then:
        // the request is cleaned up only once
        cleaned == 1
        errors.empty
    }

    def 'graceful shutdown sets connection close on a queued streaming response'() {
        given:
        def firstResp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        firstResp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def secondResp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        secondResp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def first = Sinks.many().unicast().<ByteBuf> onBackpressureBuffer()
        def second = Sinks.many().unicast().<ByteBuf> onBackpressureBuffer()
        def errors = []
        def handler = new PipeliningServerHandler(new RequestHandler() {
            int i = 0

            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                def factory = new NettyByteBodyFactory(ctx.channel())
                if (i++ == 0) {
                    outboundAccess.write(firstResp, factory.adaptNetty(first.asFlux()))
                } else {
                    outboundAccess.write(secondResp, factory.adaptNetty(second.asFlux()))
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }
        })
        def ch = new EmbeddedChannel(handler)

        when:
        // the second response is prepared, but its message has not been written yet
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first", Unpooled.EMPTY_BUFFER))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second", Unpooled.EMPTY_BUFFER))
        handler.shutdownGracefully()
        first.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)
        second.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)
        ch.runPendingTasks()

        then:
        ch.checkException()
        ch.readOutbound() == firstResp
        ch.readOutbound() == LastHttpContent.EMPTY_LAST_CONTENT
        HttpResponse second2 = ch.readOutbound()
        second2.headers().getAll(HttpHeaderNames.CONNECTION) == [HttpHeaderValues.CLOSE.toString()]
        ch.readOutbound() == LastHttpContent.EMPTY_LAST_CONTENT
        ch.readOutbound() == null
        !ch.open
        errors.empty

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'graceful shutdown sets connection close on a response that is still being prepared'() {
        given:
        def errors = []
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).collectList().subscribe { outboundAccess.write(resp, NettyByteBodyFactory.empty()) }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                errors << cause
            }
        })
        def ch = new EmbeddedChannel(handler)

        when:
        // the request is accepted and the continue response written, but the response itself is
        // not prepared yet: there is nothing queued that could be marked
        def req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        req.headers().add(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        req.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        ch.writeInbound(req)
        then:
        HttpResponse cont = ch.readOutbound()
        cont.status() == HttpResponseStatus.CONTINUE
        ch.readOutbound() == null

        when:
        handler.shutdownGracefully()
        ch.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)))
        ch.runPendingTasks()

        then:
        ch.checkException()
        FullHttpResponse full = ch.readOutbound()
        full.status() == HttpResponseStatus.NO_CONTENT
        full.headers().getAll(HttpHeaderNames.CONNECTION) == [HttpHeaderValues.CLOSE.toString()]
        ch.readOutbound() == null
        !ch.open
        errors.empty

        cleanup:
        full?.release()
        ch.finishAndReleaseAll()
    }

    def 'a body that arrives in many pieces before read complete is composed without copying'() {
        given:
        int pieces = 200
        int pieceSize = 100
        def allocator = new CountingAllocator(pieceSize + 1)
        CloseableByteBody received = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                received = body
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))
        ch.config().setAllocator(allocator)
        byte[] expected = new byte[pieces * pieceSize]
        ThreadLocalRandom.current().nextBytes(expected)

        when:
        def head = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        head.headers().add(HttpHeaderNames.CONTENT_LENGTH, expected.length)
        ch.writeOneInbound(head)
        for (int i = 0; i < pieces; i++) {
            ch.writeOneInbound(new DefaultHttpContent(Unpooled.copiedBuffer(expected, i * pieceSize, pieceSize)))
        }
        ch.writeOneInbound(LastHttpContent.EMPTY_LAST_CONTENT)
        ch.flushInbound()

        then:
        received != null
        allocator.largeAllocations == 0
        ((AvailableByteBody) received).toByteArray() == expected

        cleanup:
        received?.close()
        ch.finishAndReleaseAll()
    }

    /**
     * {@link BufferConsumer.Upstream} that records the calls made to it.
     */
    static class RecordingUpstream implements BufferConsumer.Upstream {
        int starts = 0
        int discards = 0
        long consumed = 0
        /**
         * Number of {@link #onBytesConsumed} calls.
         */
        int consumptions = 0

        @Override
        void start() {
            starts++
        }

        @Override
        void onBytesConsumed(long bytesConsumed) {
            consumed += bytesConsumed
            consumptions++
        }

        @Override
        void allowDiscard() {
            discards++
        }
    }

    def 'a second response is refused and released, and abort closes the connection'(boolean streaming) {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<ByteBuf>onBackpressureBuffer()
        OutboundAccess access = null
        int responsesWritten = 0
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                access = outboundAccess
                // a response that is being written, e.g. one whose write failed half way
                outboundAccess.write(resp, new NettyByteBodyFactory(ctx.channel()).adaptNetty(sink.asFlux()))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                responsesWritten++
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        def second = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        def factory = new NettyByteBodyFactory(ch)
        def refused
        if (streaming) {
            def buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new RecordingUpstream())
            buffer.add(NettyReadBufferFactory.of(ch.alloc()).adapt(second))
            refused = new StreamingNettyByteBody(buffer)
        } else {
            refused = factory.adapt(second)
        }
        access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR), refused)
        then:
        thrown IllegalStateException
        second.refCnt() == 0
        ch.isOpen()

        when:
        access.abort()
        then:
        !ch.isOpen()
        responsesWritten == 1
        sink.currentSubscriberCount() == 0

        cleanup:
        ch.finishAndReleaseAll()

        where:
        streaming << [false, true]
    }

    def 'abort does nothing after the response was written'() {
        given:
        OutboundAccess access = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                access = outboundAccess
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        access.abort()
        then:
        ch.isOpen()
        ((FullHttpResponse) ch.readOutbound()).status() == HttpResponseStatus.NO_CONTENT

        when:
        // Once the connection was removed, a repeated abort must also be harmless.
        ch.close()
        access.abort()

        then:
        !ch.isOpen()
        ch.checkException()

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a full response that fails before it reaches the connection releases its content'() {
        given:
        def content = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        Throwable failure = null
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                def headers = new DefaultHttpHeaders() {
                    @Override
                    HttpHeaders remove(CharSequence name) {
                        throw new OutOfMemoryError("Simulated failure")
                    }
                }
                try {
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers), new NettyByteBodyFactory(ctx.channel()).adapt(content))
                } catch (Throwable t) {
                    failure = t
                    // the response did not reach the connection, so another one can be written
                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR), NettyByteBodyFactory.empty())
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        then:
        failure instanceof OutOfMemoryError
        content.refCnt() == 0
        ((FullHttpResponse) ch.readOutbound()).status() == HttpResponseStatus.INTERNAL_SERVER_ERROR

        cleanup:
        ch.finishAndReleaseAll()
    }

    /**
     * Runs a {@link Runnable} written as an inbound message. {@link EmbeddedChannel#writeOneInbound}
     * does not run pending tasks until it returns, so the task forms one event loop turn, and it
     * does not fire read complete, so the handler behind this one is not inside a read.
     */
    static class TaskRunner extends ChannelInboundHandlerAdapter {
        @Override
        void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Runnable) {
                msg.run()
            } else {
                super.channelRead(ctx, msg)
            }
        }
    }

    static class MonitorHandler extends ChannelOutboundHandlerAdapter {
        int flush = 0
        int read = 0

        @Override
        void flush(ChannelHandlerContext ctx) throws Exception {
            super.flush(ctx)
            flush++
        }

        @Override
        void read(ChannelHandlerContext ctx) throws Exception {
            super.read(ctx)
            read++
        }
    }

    /**
     * Counts allocations at least as large as the threshold, i.e. those made by
     * {@code CompositeByteBuf.consolidate0}.
     */
    static class CountingAllocator extends AbstractByteBufAllocator {
        final int threshold
        int largeAllocations

        CountingAllocator(int threshold) {
            super(false)
            this.threshold = threshold
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            count(initialCapacity)
            return UnpooledByteBufAllocator.DEFAULT.heapBuffer(initialCapacity, maxCapacity)
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            count(initialCapacity)
            return UnpooledByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity)
        }

        @Override
        boolean isDirectBufferPooled() {
            return false
        }

        private void count(int initialCapacity) {
            if (initialCapacity >= threshold) {
                largeAllocations++
            }
        }
    }
}
