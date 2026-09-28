package io.micronaut.http.netty.stream

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets

/**
 * A streaming response body can hold bytes before the response is the one being written (e.g. the
 * body of a partially received request, or a response pipelined behind another). Those bytes must
 * be written after the response headers.
 */
class HttpStreamsServerHandlerEarlyDataSpec extends Specification {

    def 'bytes a body buffered before the response is written are sent'() {
        given:
        def ch = new EmbeddedChannel(new HttpStreamsServerHandler())
        def body = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def hello = content("hello ")
        def world = content("world")

        when:
        ch.writeInbound(request("/"))
        body.tryEmitNext(hello)
        // the writer subscribes here, and gets 'hello ' right away
        ch.writeOutbound(response(body))
        body.tryEmitNext(world)
        body.tryEmitComplete()
        ch.runPendingTasks()

        then:
        ch.checkException()
        responseBodies(ch) == ["hello world"]
        hello.refCnt() == 0
        world.refCnt() == 0

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'bytes a queued response body buffered are sent after the response before it'() {
        given:
        def ch = new EmbeddedChannel(new HttpStreamsServerHandler())
        def firstBody = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def secondBody = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()

        when:
        ch.writeInbound(request("/first"))
        ch.writeInbound(request("/second"))
        ch.writeOutbound(response(firstBody))
        secondBody.tryEmitNext(content("early "))
        // queued behind the first response, which is still being written
        ch.writeOutbound(response(secondBody))
        firstBody.tryEmitNext(content("first"))
        firstBody.tryEmitComplete()
        secondBody.tryEmitNext(content("late"))
        secondBody.tryEmitComplete()
        ch.runPendingTasks()

        then:
        ch.checkException()
        responseBodies(ch) == ["first", "early late"]

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a body that completed before the writer subscribes is sent'() {
        given:
        def ch = new EmbeddedChannel(new HttpStreamsServerHandler())
        def body = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()

        when:
        ch.writeInbound(request("/"))
        body.tryEmitNext(content("complete"))
        body.tryEmitComplete()
        ch.writeOutbound(response(body))
        ch.runPendingTasks()

        then:
        ch.checkException()
        responseBodies(ch) == ["complete"]

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a queued body that completed before its response is up is sent'() {
        given:
        def ch = new EmbeddedChannel(new HttpStreamsServerHandler())
        def firstBody = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def secondBody = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()

        when:
        ch.writeInbound(request("/first"))
        ch.writeInbound(request("/second"))
        ch.writeOutbound(response(firstBody))
        secondBody.tryEmitNext(content("queued"))
        secondBody.tryEmitComplete()
        ch.writeOutbound(response(secondBody))
        firstBody.tryEmitNext(content("first"))
        firstBody.tryEmitComplete()
        ch.runPendingTasks()

        then:
        ch.checkException()
        responseBodies(ch) == ["first", "queued"]

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'early bytes of a queued response are released when the connection closes'() {
        given:
        def ch = new EmbeddedChannel(new HttpStreamsServerHandler())
        def firstBody = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def secondBody = Sinks.many().unicast().<HttpContent>onBackpressureBuffer()
        def early = content("early")
        def more = content("more")
        def late = content("late")

        when:
        ch.writeInbound(request("/first"))
        ch.writeInbound(request("/second"))
        ch.writeOutbound(response(firstBody))
        secondBody.tryEmitNext(early)
        def secondWrite = ch.newPromise()
        ch.writeOneOutbound(response(secondBody), secondWrite)
        ch.flushOutbound()
        // more bytes for the queued response, while the first one is still being written
        secondBody.tryEmitNext(more)

        then:
        early.refCnt() == 1
        more.refCnt() == 1

        when:
        ch.close()
        ch.runPendingTasks()
        // bytes that arrive after the response was discarded
        secondBody.tryEmitNext(late)
        ch.runPendingTasks()

        then:
        early.refCnt() == 0
        more.refCnt() == 0
        late.refCnt() == 0
        secondWrite.cause() instanceof ClosedChannelException

        cleanup:
        ch.finishAndReleaseAll()
    }

    private static DefaultFullHttpRequest request(String uri) {
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
        return request
    }

    private static DefaultStreamedHttpResponse response(Sinks.Many<HttpContent> body) {
        def response = new DefaultStreamedHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body.asFlux())
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked")
        return response
    }

    private static HttpContent content(String s) {
        return new DefaultHttpContent(Unpooled.copiedBuffer(s, StandardCharsets.UTF_8))
    }

    private static List<String> responseBodies(EmbeddedChannel ch) {
        List<String> bodies = []
        StringBuilder current = null
        Object message
        while ((message = ch.readOutbound()) != null) {
            if (message instanceof HttpResponse) {
                current = new StringBuilder()
            }
            if (message instanceof HttpContent) {
                current.append(((HttpContent) message).content().toString(StandardCharsets.UTF_8))
                ((HttpContent) message).release()
            }
            if (message instanceof LastHttpContent) {
                bodies.add(current.toString())
            }
        }
        return bodies
    }
}
