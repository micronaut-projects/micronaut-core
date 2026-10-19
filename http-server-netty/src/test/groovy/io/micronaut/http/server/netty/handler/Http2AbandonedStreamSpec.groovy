package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2ConnectionHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import spock.lang.Specification

/**
 * The callbacks of {@link OutboundAccess#whenAbandoned} of an HTTP/2 request run when its stream
 * is reset or closes with the connection before the response is written, and not when another
 * stream of the connection is reset.
 */
class Http2AbandonedStreamSpec extends Specification {

    private static class Client extends Http2ChannelDuplexHandler {
    }

    private EmbeddedChannel server
    private EmbeddedChannel client
    private Client duplex
    private final List<OutboundAccess> accesses = []
    private final List<String> abandoned = []

    def setup() {
        server = new EmbeddedChannel()
        client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
        server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                accesses.add(outboundAccess)
                String uri = request.uri()
                outboundAccess.whenAbandoned { abandoned.add(uri) }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }).build())
        duplex = new Client()
        client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), duplex)
    }

    def cleanup() {
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
    }

    /**
     * Close the connection without waiting for the active streams: they close with it.
     */
    private void closeConnection() {
        server.pipeline().get(Http2ConnectionHandler).gracefulShutdownTimeoutMillis(0)
        server.close()
        EmbeddedTestUtil.advance(server, client)
        server.runPendingTasks()
    }

    private Http2FrameStream request(String path) {
        def stream = duplex.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.GET.asciiName()).scheme("http").authority("example.com").path(path)
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, true).stream(stream))
        EmbeddedTestUtil.advance(server, client)
        return stream
    }

    def "a reset stream abandons its request only, once"() {
        given:
        def first = request("/first")
        request("/second")

        when:
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(first))
        EmbeddedTestUtil.advance(server, client)

        then: "the connection stays open"
        abandoned == ["/first"]
        server.isActive()

        when: "the connection closes"
        closeConnection()

        then: "the other request is abandoned, the reset one not again"
        abandoned == ["/first", "/second"]
    }

    def "a request whose response was written is not abandoned when the connection closes"() {
        given:
        request("/done")

        when:
        accesses[0].write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
        EmbeddedTestUtil.advance(server, client)
        closeConnection()

        then:
        abandoned.isEmpty()
    }

    def "a removed callback does not run"() {
        given:
        def stream = request("/removed")
        def runs = 0
        def remove = accesses[0].whenAbandoned { runs++ }

        when:
        remove.run()
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        EmbeddedTestUtil.advance(server, client)

        then:
        runs == 0
        abandoned == ["/removed"]

        when: "registered after the stream was abandoned"
        accesses[0].whenAbandoned { runs++ }

        then: "it runs at once"
        runs == 1
    }
}
