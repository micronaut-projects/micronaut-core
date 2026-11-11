package io.micronaut.http.server.netty.binders

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.multipart.MultipartBody
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.http.server.netty.handler.PipeliningServerHandler
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * If the route fails before it returns, a streaming body argument is discarded, even if the route
 * handed it off to other code. See also the {@code UnconsumedStreamingBodyTest} in the server TCK.
 */
class UnconsumedStreamingBodySpec extends Specification {
    private static final int FIRST_LENGTH = 1000
    private static final int REST_LENGTH = 100000
    private static final String MULTIPART_CONTENT_TYPE = "multipart/form-data; boundary=----boundary"
    private static final String MULTIPART_BODY = "------boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "hello\r\n" +
            "------boundary--\r\n"

    EmbeddedChannel serverEmbeddedChannel
    EmbeddedChannel clientEmbeddedChannel

    private ApplicationContext start() {
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'UnconsumedStreamingBodySpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        clientEmbeddedChannel = new EmbeddedChannel()
        clientEmbeddedChannel.config().setAutoRead(true)

        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))
        return ctx
    }

    private void sendHead(String path) {
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path)
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM)
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, FIRST_LENGTH + REST_LENGTH)
        clientEmbeddedChannel.writeOneOutbound(request)
        clientEmbeddedChannel.writeOneOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("x" * FIRST_LENGTH, StandardCharsets.UTF_8)))
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
    }

    private void sendRest() {
        // separate packets, so that the server only reads them while it has read credit left
        for (int i = 0; i < REST_LENGTH; i += 1000) {
            clientEmbeddedChannel.writeOneOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("y" * Math.min(1000, REST_LENGTH - i), StandardCharsets.UTF_8)))
            clientEmbeddedChannel.flushOutbound()
            EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        }
        clientEmbeddedChannel.writeOneOutbound(new DefaultLastHttpContent())
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
    }

    def 'route that throws after handing off the publisher has its body discarded'(String path) {
        given:
        ApplicationContext ctx = start()
        def holder = ctx.getBean(Holder)

        when:
        sendHead(path)

        then:
        FullHttpResponse response = clientEmbeddedChannel.readInbound()
        response != null
        response.status() == HttpResponseStatus.BAD_REQUEST
        holder.body != null

        when:
        // the request failed, so the body is gone even though the route held on to it
        def length = Flux.from(holder.body).map(b -> (long) b.length).reduce(0L, Long::sum).toFuture()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        length.isCompletedExceptionally()

        when:
        // the rest of the body is drained, so the connection can serve the next request
        sendRest()
        clientEmbeddedChannel.writeOneOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/unconsumed/ok"))
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        FullHttpResponse next = clientEmbeddedChannel.readInbound()

        then:
        next != null
        next.status() == HttpResponseStatus.OK
        next.content().toString(StandardCharsets.UTF_8) == "ok"

        cleanup:
        response?.release()
        next?.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()

        where:
        path << [
                "/unconsumed/publisher-handoff",
                // a nullable argument without a value leaves the route match unfulfilled even
                // though the route is invoked
                "/unconsumed/publisher-handoff-nullable",
        ]
    }

    def 'body is discarded if the route executor cannot be resolved'() {
        given:
        ApplicationContext ctx = start()

        when:
        sendHead("/unconsumed/missing-executor")
        FullHttpResponse response = clientEmbeddedChannel.readInbound()

        then:
        response != null
        response.status() == HttpResponseStatus.INTERNAL_SERVER_ERROR

        when:
        // the rest of the body is drained, so the connection can serve the next request
        sendRest()
        clientEmbeddedChannel.writeOneOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/unconsumed/ok"))
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        FullHttpResponse next = clientEmbeddedChannel.readInbound()

        then:
        next != null
        next.status() == HttpResponseStatus.OK

        cleanup:
        response?.release()
        next?.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()
    }

    def 'fully received body is released if the route fails'(String path, String contentType, String content) {
        given:
        ApplicationContext ctx = start()
        // hand the server a complete request, like a small request that arrives in a single read
        String pipeliningName = serverEmbeddedChannel.pipeline().toMap().find { it.value instanceof PipeliningServerHandler }.key
        serverEmbeddedChannel.pipeline().addBefore(pipeliningName, "inject", new ChannelInboundHandlerAdapter())
        ByteBuf buf = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8)
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path, buf)
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes())

        when:
        serverEmbeddedChannel.pipeline().context("inject").fireChannelRead(request)
        serverEmbeddedChannel.pipeline().context("inject").fireChannelReadComplete()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        FullHttpResponse response = clientEmbeddedChannel.readInbound()

        then:
        response != null
        response.status() == HttpResponseStatus.BAD_REQUEST
        buf.refCnt() == 0

        cleanup:
        response?.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()

        where:
        path                                    | contentType                     | content
        "/unconsumed/publisher-unsatisfied"     | MediaType.APPLICATION_OCTET_STREAM | "hello"
        "/unconsumed/publisher-throws"          | MediaType.APPLICATION_OCTET_STREAM | "hello"
        "/unconsumed/json-publisher-unsatisfied" | MediaType.APPLICATION_JSON      | '[{"a":1},{"a":2}]'
        "/unconsumed/multipart-body-unsatisfied" | MULTIPART_CONTENT_TYPE         | MULTIPART_BODY
    }

    def 'rejected request is not asked to send its body'(String path, String contentType) {
        given:
        ApplicationContext ctx = start()
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path)
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, FIRST_LENGTH + REST_LENGTH)
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)

        when:
        clientEmbeddedChannel.writeOneOutbound(request)
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        FullHttpResponse response = clientEmbeddedChannel.readInbound()

        then:
        // no 100 Continue before the rejection
        response != null
        response.status() == HttpResponseStatus.BAD_REQUEST
        clientEmbeddedChannel.readInbound() == null

        cleanup:
        response?.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()

        where:
        path                                     | contentType
        "/unconsumed/publisher-unsatisfied"      | MediaType.APPLICATION_OCTET_STREAM
        "/unconsumed/publisher-throws"           | MediaType.APPLICATION_OCTET_STREAM
        "/unconsumed/multipart-body-unsatisfied" | MULTIPART_CONTENT_TYPE
    }

    @Singleton
    @Requires(property = "spec.name", value = "UnconsumedStreamingBodySpec")
    static class Holder {
        Publisher<byte[]> body
    }

    @Controller("/unconsumed")
    @Requires(property = "spec.name", value = "UnconsumedStreamingBodySpec")
    static class MyController {
        private final Holder holder

        MyController(Holder holder) {
            this.holder = holder
        }

        @Post(uri = "/publisher-handoff", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherHandoff(@Body Publisher<byte[]> body) {
            // the body would be consumed elsewhere, later, but the route fails
            holder.body = body
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/publisher-handoff-nullable", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherHandoffNullable(@Body Publisher<byte[]> body, @Nullable @QueryValue String missing) {
            holder.body = body
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/publisher-unsatisfied", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherUnsatisfied(@Body Publisher<byte[]> body, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/publisher-throws", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherThrows(@Body Publisher<byte[]> body) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/json-publisher-unsatisfied", consumes = MediaType.APPLICATION_JSON)
        String jsonPublisherUnsatisfied(@Body Publisher<Map<String, Object>> body, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/multipart-body-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String multipartBodyUnsatisfied(@Body MultipartBody body, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/missing-executor", consumes = MediaType.APPLICATION_OCTET_STREAM)
        @ExecuteOn("missing")
        String missingExecutor(@Body Publisher<byte[]> body) {
            return "unreachable"
        }

        @Get(uri = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok"
        }
    }
}
