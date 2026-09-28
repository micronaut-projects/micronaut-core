package io.micronaut.http.server.netty.binders

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
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
 * If the route is invoked, a streaming body argument is the route's to consume, even if the route
 * fails and the error response has already been sent. See also the
 * {@code UnconsumedStreamingBodyTest} in the server TCK.
 */
class UnconsumedStreamingBodySpec extends Specification {
    private static final int FIRST_LENGTH = 1000
    private static final int REST_LENGTH = 100000

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

    def 'route that fails after handing off the publisher still lets it be consumed'() {
        given:
        ApplicationContext ctx = start()
        def holder = ctx.getBean(Holder)

        when:
        sendHead("/unconsumed/publisher-handoff")

        then:
        FullHttpResponse response = clientEmbeddedChannel.readInbound()
        response != null
        response.status() == HttpResponseStatus.BAD_REQUEST
        holder.body != null

        when:
        // only subscribe after the route failed and the error response was sent
        def length = Flux.from(holder.body).map(b -> (long) b.length).reduce(0L, Long::sum).toFuture()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        sendRest()

        then:
        length.getNow(-1L) == (long) (FIRST_LENGTH + REST_LENGTH)

        cleanup:
        response?.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()
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
            // the body is consumed elsewhere, later, even though the route fails
            holder.body = body
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }
    }
}
