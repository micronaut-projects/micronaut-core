package io.micronaut.http.server.netty.handler


import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.body.ByteBody
import io.micronaut.http.server.netty.body.ImmediateByteBody
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PipeliningServerHandlerSpec extends Specification {
    def 'pipelined requests have their responses batched'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, ByteBody body, PipeliningServerHandler.OutboundAccess outboundAccess) {
                outboundAccess.writeFull(resp)
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
        ch.readOutbound() == resp
        ch.readOutbound() == resp
        ch.readOutbound() == null
        ch.checkException()
    }

    def 'streaming responses flush after every item'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, ByteBody body, PipeliningServerHandler.OutboundAccess outboundAccess) {
                outboundAccess.writeStreamed(resp, sink.asFlux())
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
        def c1 = new DefaultHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8)))
        sink.tryEmitNext(c1)
        then:
        mon.read == 2
        mon.flush == 1
        ch.readOutbound() instanceof HttpResponse
        ch.readOutbound() == c1
        ch.readOutbound() == null

        when:
        def c2 = new DefaultHttpContent(Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8)))
        sink.tryEmitNext(c2)
        then:
        mon.read == 2
        mon.flush == 2
        ch.readOutbound() == c2
        ch.readOutbound() == null
        ch.checkException()
    }

    def 'requests that come in a single packet are accumulated'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, ByteBody body, PipeliningServerHandler.OutboundAccess outboundAccess) {
                assert body instanceof ImmediateByteBody
                assert body.contentUnclaimed().toString(StandardCharsets.UTF_8) == "foobar"
                body.release()
                outboundAccess.writeFull(resp)
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
        ch.readOutbound() == resp
        ch.readOutbound() == null
    }

    def 'continue support'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, ByteBody body, PipeliningServerHandler.OutboundAccess outboundAccess) {
                Flux.from(body.rawContent(new HttpServerConfiguration()).asPublisher()).collectList().subscribe { outboundAccess.writeFull(resp) }
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
        ch.readOutbound() == resp
        ch.readOutbound() == null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9366')
    def 'nested write'() {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT)
        def ch = new EmbeddedChannel(mon, new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                super.write(ctx, msg, promise)
                ctx.fireChannelWritabilityChanged()
            }
        }, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, ByteBody body, PipeliningServerHandler.OutboundAccess outboundAccess) {
                assert request instanceof FullHttpRequest
                request.release()
                outboundAccess.writeFull(resp)
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
        ch.readOutbound() == resp
        ch.readOutbound() == null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9366')
    def 'responseWritten not called on close'(boolean completeOnCancel) {
        given:
        def mon = new MonitorHandler()
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def cleaned = 0
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, ByteBody body, PipeliningServerHandler.OutboundAccess outboundAccess) {
                assert request instanceof FullHttpRequest
                request.release()
                outboundAccess.writeStreamed(resp, sink.asFlux().doOnCancel {
                    // optional extra weirdness: onComplete *after* cancel. Could lead to double call to responseWritten, if I was an idiot.
                    if (completeOnCancel) sink.tryEmitComplete()
                })
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
        def c1 = new DefaultHttpContent(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8))
        sink.emitNext(c1, Sinks.EmitFailureHandler.FAIL_FAST)
        then:
        ch.checkException()
        ch.readOutbound() == resp
        ch.readOutbound() == c1

        when:
        ch.close()
        then:
        ch.checkException()
        ch.readOutbound() == null
        cleaned == 1

        where:
        completeOnCancel << [true, false]
    }

    def 'empty streaming response while in queue'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def sink = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            int i = 0

            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, ByteBody body, PipeliningServerHandler.OutboundAccess outboundAccess) {
                body.release()
                if (i++ == 0) {
                    outboundAccess.writeStreamed(resp, sink.asFlux())
                } else {
                    outboundAccess.writeStreamed(resp, Flux.empty())
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
