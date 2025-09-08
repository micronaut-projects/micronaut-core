package io.micronaut.http.server.netty.handler

import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.body.AvailableByteBody
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableAvailableByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.compression.SnappyFrameEncoder
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
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

    def 'streaming responses flush after every item'() {
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
        then:
        mon.read == 2
        mon.flush == 1
        ch.readOutbound() instanceof HttpResponse
        ch.readOutbound() == new DefaultHttpContent(c1)
        ch.readOutbound() == null

        when:
        def c2 = Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8))
        sink.tryEmitNext(c2)
        then:
        mon.read == 2
        mon.flush == 2
        ch.readOutbound() == new DefaultHttpContent(c2)
        ch.readOutbound() == null
        ch.checkException()
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
}
