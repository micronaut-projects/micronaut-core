package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.AvailableByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2Stream
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Only streams whose request headers arrived in the current read batch, without their full body,
 * are devolved to streaming at read complete. This checks the ways such a stream can end before
 * the read complete.
 */
class Http2PendingStreamSpec extends Specification {
    private static class Accepted {
        HttpRequest request
        CloseableByteBody body
        OutboundAccess outboundAccess
    }

    private static class DuplexHandler extends Http2ChannelDuplexHandler {
        @Override
        void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.fireChannelRead(msg)
        }
    }

    private static class Harness {
        EmbeddedChannel server
        EmbeddedChannel client
        DuplexHandler duplexHandler
        Http2ServerHandler.ConnectionHandler connectionHandler
        final List<Accepted> accepted = []
        final List<Throwable> unboundErrors = []
        Closure<?> onAccept = { Accepted a -> }

        Http2FrameStream newStream() {
            return duplexHandler.newStream()
        }

        void writeHeaders(Http2FrameStream stream, boolean endStream) {
            def headers = new DefaultHttp2Headers()
            headers.method(HttpMethod.POST.asciiName())
            headers.scheme("http")
            headers.authority("example.com")
            headers.path("/")
            client.write(new DefaultHttp2HeadersFrame(headers, endStream).stream(stream))
        }

        void writeData(Http2FrameStream stream, String data, boolean endStream) {
            client.write(new DefaultHttp2DataFrame(Unpooled.copiedBuffer(data, StandardCharsets.UTF_8), endStream).stream(stream))
        }

        /**
         * Flush what was written so far as one read batch, i.e. one channelRead followed by one
         * channelReadComplete on the server.
         */
        void flushBatch() {
            client.flush()
            EmbeddedTestUtil.advance(server, client)
        }

        void close() {
            server.checkException()
            client.checkException()
            server.finishAndReleaseAll()
            client.finishAndReleaseAll()
            // deliver (and drop) what closing wrote, e.g. the goaway
            EmbeddedTestUtil.advance(client, server)
        }
    }

    private static Harness configure() {
        def harness = new Harness()
        harness.server = new EmbeddedChannel()
        harness.client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(harness.server, harness.client)
        harness.connectionHandler = new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                def a = new Accepted(request: request, body: body, outboundAccess: outboundAccess)
                harness.accepted.add(a)
                harness.onAccept.call(a)
            }

            @Override
            void handleUnboundError(Throwable cause) {
                harness.unboundErrors.add(cause)
            }
        }).build()
        harness.server.pipeline().addLast(harness.connectionHandler)
        harness.duplexHandler = new DuplexHandler()
        harness.client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), harness.duplexHandler)
        EmbeddedTestUtil.advance(harness.server, harness.client)
        return harness
    }

    private static String read(CloseableByteBody body) {
        try {
            return new String(((AvailableByteBody) body).toByteArray(), StandardCharsets.UTF_8)
        } finally {
            body.close()
        }
    }

    def "headers without the full body are devolved at read complete"() {
        given:
        def harness = configure()
        def stream = harness.newStream()

        when: 'headers and part of the body arrive in one batch'
        harness.writeHeaders(stream, false)
        harness.writeData(stream, "foo", false)
        harness.flushBatch()

        then: 'the request is accepted with a streaming body'
        harness.accepted.size() == 1
        !(harness.accepted[0].body instanceof AvailableByteBody)

        when: 'the rest of the body arrives later'
        def buffered = harness.accepted[0].body.buffer()
        harness.writeData(stream, "bar", true)
        harness.flushBatch()

        then:
        harness.accepted.size() == 1
        buffered.isDone()
        read(buffered.join()) == "foobar"

        cleanup:
        buffered.getNow(null)?.close()
        harness.close()
    }

    def "full body in one batch is accepted without devolving"() {
        given:
        def harness = configure()
        def stream = harness.newStream()

        when:
        harness.writeHeaders(stream, false)
        harness.writeData(stream, "foo", false)
        harness.writeData(stream, "bar", true)
        harness.flushBatch()

        then:
        harness.accepted.size() == 1
        harness.accepted[0].body instanceof AvailableByteBody
        read(harness.accepted[0].body) == "foobar"

        cleanup:
        harness.close()
    }

    def "stream reset by the client before read complete is not accepted"() {
        given:
        def harness = configure()
        def stream = harness.newStream()

        when: 'headers and the reset arrive in one batch'
        harness.writeHeaders(stream, false)
        harness.client.write(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        harness.flushBatch()

        then:
        harness.accepted.isEmpty()
        harness.unboundErrors.isEmpty()

        when: 'a later stream on the same connection'
        def stream2 = harness.newStream()
        harness.writeHeaders(stream2, false)
        harness.writeData(stream2, "later", false)
        harness.flushBatch()

        then:
        harness.accepted.size() == 1
        harness.accepted[0].request.uri() == "/"

        cleanup:
        harness.accepted.each { it.body.close() }
        harness.close()
    }

    def "stream is still devolved after a goaway from the client, as before"() {
        given:
        def harness = configure()
        def stream = harness.newStream()

        when: 'headers and a goaway arrive in one batch; the client may still finish its open streams'
        harness.writeHeaders(stream, false)
        harness.writeData(stream, "foo", false)
        harness.client.write(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR))
        harness.flushBatch()

        then:
        harness.accepted.size() == 1
        !(harness.accepted[0].body instanceof AvailableByteBody)

        cleanup:
        harness.accepted.each { it.body.close() }
        harness.close()
    }

    def "stream closed by the server during read complete is skipped"() {
        given:
        def harness = configure()
        harness.onAccept = { Accepted a ->
            // respond in full right away, which ends the server side and resets the still open request side
            a.body.close()
            a.outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
        }
        def first = harness.newStream()
        def second = harness.newStream()

        when: 'two streams are pending in the same batch'
        harness.writeHeaders(first, false)
        harness.writeData(first, "a", false)
        harness.writeHeaders(second, false)
        harness.writeData(second, "b", false)
        harness.flushBatch()

        then: 'both are accepted, the first is reset by the server without disturbing the second'
        harness.accepted.size() == 2
        harness.unboundErrors.isEmpty()
        harness.server.pipeline().get(Http2ServerHandler.ConnectionHandler).connection().stream(first.id()) == null ||
                harness.server.pipeline().get(Http2ServerHandler.ConnectionHandler).connection().stream(first.id()).state() == Http2Stream.State.CLOSED

        cleanup:
        harness.close()
    }

    def "stream closed locally before read complete is not accepted"() {
        given: 'a server fed by hand so that something can happen between the read and the read complete'
        def accepted = []
        def server = new EmbeddedChannel()
        def connectionHandler = new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted.add(request)
                body.close()
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }).build()
        server.pipeline().addLast(connectionHandler)
        def client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), new DuplexHandler())
        def stream = client.pipeline().get(DuplexHandler).newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName())
        headers.scheme("http")
        headers.authority("example.com")
        headers.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream))

        when: 'the preface and headers are read, then the stream is closed locally, then the read completes'
        ByteBuf packet
        while ((packet = client.readOutbound()) != null) {
            server.writeOneInbound(packet)
        }
        connectionHandler.connection().stream(stream.id()).close()
        server.pipeline().fireChannelReadComplete()

        then:
        accepted.isEmpty()

        when: 'the handler goes away with a stream still pending, as it does when the connection closes'
        def stream2 = client.pipeline().get(DuplexHandler).newStream()
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream2))
        while ((packet = client.readOutbound()) != null) {
            server.writeOneInbound(packet)
        }
        server.pipeline().remove(connectionHandler)
        server.pipeline().fireChannelReadComplete()

        then:
        accepted.isEmpty()
        server.checkException()

        cleanup:
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
    }
}
