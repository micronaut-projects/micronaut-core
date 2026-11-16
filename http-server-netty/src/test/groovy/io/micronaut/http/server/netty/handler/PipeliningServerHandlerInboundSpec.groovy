package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BaseSharedBuffer.IncorrectContentLengthException
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.exceptions.HttpStatusException
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.TooLongFrameException
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

class PipeliningServerHandlerInboundSpec extends Specification {
    def 'completion resets once before accepting a reentrant request for #kind'() {
        given:
        def h = new Harness()
        def completed = []
        def resets = []
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { h.errors << it }, {
                completed << request.uri()
                if (request.uri() == '/first') {
                    resets << !h.handler.isStreamingRequestBody()
                    resets << !h.handler.inbound.endBody(true, null)
                    h.channel.writeInbound(PipeliningServerHandlerInboundSpec.request('/next'))
                }
            })
        }

        when:
        def bytes = kind.startsWith('compressed') ? gzip('body') : 'body'.getBytes(StandardCharsets.UTF_8)
        if (kind.endsWith('full')) {
            def first = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/first', Unpooled.wrappedBuffer(bytes))
            if (kind.startsWith('compressed')) {
                first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
            }
            h.channel.writeInbound(first)
        } else {
            def first = request('/first')
            if (kind.startsWith('compressed')) {
                first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
            }
            h.channel.writeOneInbound(first)
            if (kind.endsWith('streaming')) {
                h.channel.flushInbound()
            }
            h.channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(bytes)))
        }

        then:
        h.errors.empty
        resets == [true, true]
        completed == ['/first']
        h.requests == ['/first', '/next']
        h.handler.isStreamingRequestBody()

        when:
        h.channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer('next', StandardCharsets.UTF_8)))

        then:
        completed == ['/first', '/next']
        !h.handler.isStreamingRequestBody()
        !h.handler.inbound.endBody(true, null)
        h.errors.empty

        cleanup:
        h.channel.finishAndReleaseAll()

        where:
        kind << ['full', 'buffered', 'streaming', 'compressed full', 'compressed buffered', 'compressed streaming']
    }

    def 'an oversized last content fails once and resets once for compressed=#compressed streaming=#streaming'() {
        given:
        def h = new Harness()
        h.handler.setBodySizeLimits(new BodySizeLimits(2, Integer.MAX_VALUE))
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { Throwable failure ->
                h.errors << failure
            })
        }
        def first = request('/first')
        if (compressed) {
            first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        }
        h.channel.writeOneInbound(first)
        if (streaming) {
            h.channel.flushInbound()
        }
        def bytes = Unpooled.wrappedBuffer(compressed ? gzip('big') : 'big'.getBytes(StandardCharsets.UTF_8))

        when:
        h.channel.writeInbound(new DefaultLastHttpContent(bytes))
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        h.requests == ['/first', '/next']
        h.errors.size() == 1
        h.errors[0] instanceof ContentLengthExceededException
        bytes.refCnt() == 0
        !h.handler.inbound.endBody(true, null)
        h.channel.checkException()

        cleanup:
        h.channel.finishAndReleaseAll()

        where:
        compressed | streaming
        false      | false
        false      | true
        true       | false
        true       | true
    }

    def 'body failure commits its state before reentrant notification for compressed=#compressed streaming=#streaming'() {
        given:
        def h = new Harness()
        def transitions = []
        def failure = new IOException('body failed')
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { Throwable cause ->
                h.errors << cause
                transitions << h.handler.inbound.state.name()
                transitions << h.handler.inbound.endBody(false, cause)
            })
        }
        def first = request('/first')
        if (compressed) {
            first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        }
        h.channel.writeOneInbound(first)
        if (streaming) {
            h.channel.flushInbound()
        }

        when:
        h.channel.pipeline().fireExceptionCaught(failure)

        then:
        h.errors == [failure]
        transitions == ['DROPPING', false]
        h.handler.inbound.decompressor == null
        !h.handler.inbound.endBody(false, failure)

        when:
        h.channel.writeInbound(new DefaultLastHttpContent())
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        h.requests == ['/first', '/next']
        h.errors == [failure]
        !h.handler.inbound.endBody(true, null)
        h.channel.checkException()

        cleanup:
        h.channel.finishAndReleaseAll()

        where:
        compressed | streaming
        false      | false
        false      | true
        true       | false
        true       | true
    }

    def 'malformed last content commits termination and close before notifying a compressed=#compressed body'() {
        given:
        def h = new Harness()
        def transitions = []
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { Throwable failure ->
                h.errors << failure
                transitions << h.handler.inbound.state.name()
                transitions << h.handler.outboundQueue.peekLast().closeAfterWrite
                transitions << h.handler.inbound.endBody(true, null)
            })
        }
        def first = request('/first')
        if (compressed) {
            first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        }
        h.channel.writeInbound(first)
        def bytes = Unpooled.buffer().writeByte(1)
        def last = new DefaultLastHttpContent(bytes)
        last.setDecoderResult(DecoderResult.failure(new TooLongFrameException('malformed trailer')))

        when:
        h.channel.writeInbound(last)

        then:
        transitions == ['MESSAGE', true, false]
        h.errors.size() == 1
        h.errors[0] instanceof HttpStatusException
        h.errors[0].status.code == 413
        bytes.refCnt() == 0
        h.handler.inbound.decompressor == null
        h.channel.checkException()

        cleanup:
        h.channel.finishAndReleaseAll()

        where:
        compressed << [false, true]
    }

    def 'discarding a compressed=#compressed body notifies its listener once after committing the state'() {
        given:
        def h = new Harness()
        CloseableByteBody currentBody = null
        def transitions = []
        h.receive = { HttpRequest request, CloseableByteBody body ->
            if (request.uri() == '/first') {
                currentBody = body
            } else {
                body.close()
            }
        }
        def first = request('/first')
        if (compressed) {
            first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        }
        h.channel.writeInbound(first)
        h.handler.onRequestBodyDiscarded {
            transitions << h.handler.inbound.state.name()
            transitions << h.handler.inbound.decompressor
            currentBody.allowDiscard()
        }

        when:
        currentBody.allowDiscard()
        currentBody.allowDiscard()
        h.channel.writeInbound(new DefaultLastHttpContent())
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        transitions == ['DROPPING', null]
        h.requests == ['/first', '/next']
        h.errors.empty

        cleanup:
        currentBody?.close()
        h.channel.finishAndReleaseAll()

        where:
        compressed << [false, true]
    }

    def 'an exception while #stage fails the body before the last content resets it'() {
        given:
        def h = new Harness()
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { h.errors << it })
        }
        def first = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/first')
        first.headers().set(HttpHeaderNames.CONTENT_LENGTH, expectedLength)
        h.channel.writeInbound(first)

        when:
        h.channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer('xx', StandardCharsets.UTF_8)))
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        h.errors.size() == 1
        h.errors[0] instanceof IncorrectContentLengthException
        h.requests == ['/first', '/next']
        !h.handler.inbound.endBody(true, null)
        h.channel.checkException()

        cleanup:
        h.channel.finishAndReleaseAll()

        where:
        stage            | expectedLength
        'reading'        | 1
        'completing'     | 3
    }

    def 'decompression follows the buffering to streaming transition in the reused context'() {
        given:
        def h = new Harness()
        def received = []
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({ received << new String(it, StandardCharsets.UTF_8) }, { h.errors << it })
        }
        def first = request('/first')
        first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        h.channel.writeOneInbound(first)
        def context = h.handler.inbound
        def decompressor = context.decompressor
        byte[] bytes = gzip('buffered then streamed')

        when:
        h.channel.writeOneInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(bytes, 0, bytes.length - 8)))
        h.channel.flushInbound()

        then:
        h.handler.isStreamingRequestBody()
        context.is(h.handler.inbound)
        context.decompressor.is(decompressor)

        when:
        h.channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(bytes, bytes.length - 8, 8)))
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        received == ['buffered then streamed', '']
        h.requests == ['/first', '/next']
        context.is(h.handler.inbound)
        context.decompressor == null
        !h.handler.isStreamingRequestBody()
        h.errors.empty

        cleanup:
        h.channel.finishAndReleaseAll()
    }

    private static HttpRequest request(String uri) {
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri)
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        request
    }

    private static byte[] gzip(String text) {
        def output = new ByteArrayOutputStream()
        new GZIPOutputStream(output).withCloseable {
            it.write(text.getBytes(StandardCharsets.UTF_8))
        }
        output.toByteArray()
    }

    private static class Harness implements RequestHandler {
        final PipeliningServerHandler handler = new PipeliningServerHandler(this)
        final EmbeddedChannel channel = new EmbeddedChannel(handler)
        final List<String> requests = []
        final List<Throwable> errors = []
        Closure receive

        @Override
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
            requests << request.uri()
            receive(request, body)
        }

        @Override
        void handleUnboundError(Throwable cause) {
            errors << cause
        }
    }
}
