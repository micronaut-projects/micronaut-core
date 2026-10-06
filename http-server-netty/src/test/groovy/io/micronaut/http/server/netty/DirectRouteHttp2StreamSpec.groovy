package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.MessageBodyHandlerRegistry
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.http.server.netty.handler.Http2ServerHandler
import io.micronaut.http.server.netty.handler.OutboundAccess
import io.micronaut.http.server.netty.handler.RequestHandler
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.util.ReferenceCountUtil
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * Direct routes on the streams of an HTTP/2 connection, driven frame by frame: a stream the
 * client resets cancels its pending route while the other streams go on, and a status without a
 * body never sends one.
 */
class DirectRouteHttp2StreamSpec extends Specification {

    def 'a status without a body sends no DATA frame and no content-length'() {
        given:
        def harness = new Harness()

        when:
        harness.get('/status/' + status)
        def frames = harness.drain()

        then:
        def headers = frames.find { it.status == status.toString() }
        headers.end
        headers.contentLength == null
        !frames.any { it.containsKey('body') }

        cleanup:
        harness.close()

        where:
        status << [204, 304]
    }

    def 'resetting a stream cancels its pending direct route, and the connection goes on'() {
        given:
        def harness = new Harness()
        def stream = harness.get('/pending')

        expect:
        !harness.routes.pending.isDone()

        when:
        harness.client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        EmbeddedTestUtil.advance(harness.server, harness.client)

        then:
        harness.routes.pending.isCancelled()

        when:
        harness.get('/status/200')

        then:
        def frames = harness.drain()
        frames.any { it.status == '200' }
        frames.any { it.body == 'status 200' }
        harness.server.isOpen()

        cleanup:
        harness.close()
    }

    def 'a connection that goes away cancels a pending direct route'() {
        given:
        def harness = new Harness()
        harness.get('/pending')

        expect:
        !harness.routes.pending.isDone()

        when:
        // the client drops the connection: closing it on the server would wait for the active
        // stream, as the HTTP/2 handler shuts down gracefully
        harness.server.pipeline().fireChannelInactive()
        EmbeddedTestUtil.advance(harness.server, harness.client)

        then:
        harness.routes.pending.isCancelled()

        cleanup:
        harness.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'DirectRouteHttp2StreamSpec')
    static class Routes implements HttpDirectRoutes {
        final CompletableFuture<io.micronaut.http.HttpResponse<?>> pending = new CompletableFuture<>()

        @Override
        void routes(DirectRouteBuilder routes) {
            routes.GET('/pending').respondAsync(direct -> pending)
            // a route that gives a body to a status that has none
            routes.GET('/status/{code}').respond(direct -> {
                String code = direct.pathVariables().getString('code')
                direct.responses().status(HttpStatus.valueOf(Integer.parseInt(code))).body('status ' + code)
            })
        }
    }

    /**
     * An HTTP/2 server connection that answers direct routes only, and a client frame codec.
     */
    private static class Harness implements AutoCloseable {
        final ApplicationContext context = ApplicationContext.run(['spec.name': 'DirectRouteHttp2StreamSpec'])
        final Routes routes = context.getBean(Routes)
        final EmbeddedChannel server = new EmbeddedChannel()
        final EmbeddedChannel client = new EmbeddedChannel()
        final Http2ChannelDuplexHandler duplex = new Http2ChannelDuplexHandler() {}

        Harness() {
            NettyDirectRoutes direct = NettyDirectRoutes.of(context, new NettyHttpServerConfiguration(), MessageBodyHandlerRegistry.EMPTY,
                { -> { Runnable task -> task.run() } as Executor } as Supplier<Executor>,
                { ctx, request, body, outbound -> throw new AssertionError('no ordinary route') } as NettyDirectRoutes.OrdinaryRequests)
            EmbeddedTestUtil.connect(server, client)
            server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
                @Override
                void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outbound) {
                    assert direct.answer(ctx, request, body, outbound)
                }

                @Override
                void handleUnboundError(Throwable cause) {
                    throw new AssertionError(cause)
                }
            }).build())
            client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), duplex)
            EmbeddedTestUtil.advance(server, client)
            drain()
        }

        Http2FrameStream get(String path) {
            def stream = duplex.newStream()
            def headers = new DefaultHttp2Headers().method('GET').scheme('http').authority('example.com').path(path)
            client.writeOutbound(new DefaultHttp2HeadersFrame(headers, true).stream(stream))
            EmbeddedTestUtil.advance(server, client)
            return stream
        }

        List<Map> drain() {
            List<Map> frames = []
            Object frame
            while ((frame = client.readInbound()) != null) {
                if (frame instanceof Http2HeadersFrame) {
                    frames.add([status: frame.headers().status()?.toString(), end: frame.isEndStream(),
                                contentLength: frame.headers().get('content-length')?.toString()])
                } else if (frame instanceof Http2DataFrame) {
                    frames.add([body: frame.content().toString(StandardCharsets.UTF_8), end: frame.isEndStream()])
                }
                ReferenceCountUtil.release(frame)
            }
            return frames
        }

        @Override
        void close() {
            server.finishAndReleaseAll()
            client.finishAndReleaseAll()
            EmbeddedTestUtil.advance(client, server)
            context.close()
        }
    }
}
