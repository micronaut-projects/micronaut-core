package io.micronaut.http.server.netty.handler

import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.TooLongFrameException
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

/**
 * Every way the body of an HTTP/1 request ends (completed, failed, over the limit, malformed,
 * discarded by its reader, handler removed) leaves the handler ready for the next request of the
 * connection, releases what it buffered and tells the reader of the body exactly once.
 */
class PipeliningServerHandlerInboundTerminationSpec extends Specification {
    def 'a failure thrown while reading the last content of a #stage body fails the body and the next request is served'() {
        given:
        def h = new Harness()
        def outcome = []
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { outcome << 'error' }, { outcome << 'complete' })
        }
        // chunked, so a body completed without its content does not fail a length check
        h.channel.writeOneInbound(request('/first'))
        if (streaming) {
            h.channel.pipeline().fireChannelReadComplete()
        }

        when:
        h.channel.writeOneInbound(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER) {
            @Override
            ByteBuf content() {
                throw new AssertionError('fatal while reading')
            }
        })
        h.channel.pipeline().fireChannelReadComplete()
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        outcome == ['error', 'complete']
        h.requests == ['/first', '/next']

        cleanup:
        h.close()

        where:
        stage       | streaming
        'buffered'  | false
        'streaming' | true
    }

    def 'a buffered body that cannot be assembled is accepted as a failed body and the next request is served for #failure'() {
        given:
        def accepted = []
        def bodyErrors = []
        def unboundErrors = []
        ByteBufAllocator allocator = Stub() {
            compositeBuffer(_) >> { throw failure }
        }
        def channel = new EmbeddedChannel(new PipeliningServerHandler(Stub(RequestHandler) {
            accept(_, _, _, _) >> { ctx, request, CloseableByteBody body, OutboundAccess outbound ->
                accepted << request.uri()
                if (request.uri() == '/first') {
                    assert body instanceof StreamingNettyByteBody
                    body.primary(new BufferConsumer() {
                        @Override
                        void add(ReadBuffer buffer) { buffer.close() }

                        @Override
                        void complete() { throw new AssertionError('expected body failure') }

                        @Override
                        void error(Throwable cause) {
                            bodyErrors << cause
                            outbound.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR), NettyByteBodyFactory.empty())
                        }
                    }).start()
                } else {
                    body.close()
                    outbound.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
                }
            }
            handleUnboundError(_) >> { error -> unboundErrors << error }
        }))
        channel.config().setAllocator(allocator)
        def piece1 = Unpooled.buffer().writeByte(1)
        def piece2 = Unpooled.buffer().writeByte(2)

        when:
        channel.writeOneInbound(request('/first'))
        channel.writeOneInbound(new DefaultHttpContent(piece1))
        channel.writeOneInbound(new DefaultLastHttpContent(piece2))
        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        accepted == ['/first', '/next']
        bodyErrors == [failure]
        unboundErrors.empty
        piece1.refCnt() == 0
        piece2.refCnt() == 0
        channel.open

        when:
        HttpResponse failed = channel.readOutbound()
        HttpResponse next = channel.readOutbound()

        then:
        failed.status() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        next.status() == HttpResponseStatus.OK
        channel.readOutbound() == null

        cleanup:
        ReferenceCountUtil.release(failed)
        ReferenceCountUtil.release(next)
        channel.finishAndReleaseAll()

        where:
        failure << [new IllegalStateException('composite allocation failed'), new OutOfMemoryError('direct memory exhausted')]
    }

    def 'a #kind body that completes leaves the handler ready for the next request'() {
        given:
        def h = new Harness()
        def completed = []
        h.receive = { HttpRequest request, CloseableByteBody body ->
            consume(body, { h.errors << it }, { completed << request.uri() })
        }
        boolean compressed = kind.startsWith('compressed')
        boolean streaming = kind.endsWith('streaming')

        when:
        def bytes = compressed ? gzip('body') : 'body'.getBytes(StandardCharsets.UTF_8)
        if (kind.endsWith('full')) {
            def first = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/first', Unpooled.wrappedBuffer(bytes))
            if (compressed) {
                first.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
            }
            h.channel.writeInbound(first)
        } else {
            h.channel.writeOneInbound(request('/first', compressed))
            if (streaming) {
                h.channel.flushInbound()
            }
            h.channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(bytes)))
        }

        then:
        h.errors.empty
        completed == ['/first']
        h.events == (streaming ? [RequestBodyEvent.STREAMING, RequestBodyEvent.DONE] : [])

        when:
        h.channel.writeInbound(request('/next'))
        h.channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer('next', StandardCharsets.UTF_8)))

        then:
        h.requests == ['/first', '/next']
        completed == ['/first', '/next']
        h.errors.empty

        cleanup:
        h.close()

        where:
        kind << ['full', 'buffered', 'streaming', 'compressed full', 'compressed buffered', 'compressed streaming']
    }

    def 'a reader that discards a compressed=#compressed body has the rest dropped and the next request is served'() {
        given:
        def h = new Harness()
        CloseableByteBody currentBody = null
        h.receive = { HttpRequest request, CloseableByteBody body ->
            if (request.uri() == '/first') {
                currentBody = body
            } else {
                body.close()
            }
        }
        h.channel.writeInbound(request('/first', compressed))
        def rest = Unpooled.wrappedBuffer(compressed ? gzip('rest') : 'rest'.getBytes(StandardCharsets.UTF_8))

        when:
        currentBody.allowDiscard()
        currentBody.allowDiscard()

        then:
        h.events.first() == RequestBodyEvent.STREAMING
        h.events.contains(RequestBodyEvent.DONE)

        when:
        h.channel.writeInbound(new DefaultLastHttpContent(rest))
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        rest.refCnt() == 0
        h.requests == ['/first', '/next']
        h.errors.empty
        h.channel.checkException()

        cleanup:
        currentBody?.close()
        h.close()

        where:
        compressed << [false, true]
    }

    def 'removing the handler releases a compressed=#compressed body during #stage'() {
        given:
        def h = new Harness()
        h.receive = { HttpRequest request, CloseableByteBody body ->
            consume(body, { h.errors << it }, { h.errors << new AssertionError('completed') })
        }
        h.channel.writeOneInbound(request('/first', compressed))
        def bytes = Unpooled.wrappedBuffer(compressed ? gzip('body') : 'body'.getBytes(StandardCharsets.UTF_8))
        h.channel.writeOneInbound(new DefaultHttpContent(bytes))
        if (streaming) {
            h.channel.flushInbound()
        }

        when:
        h.channel.pipeline().remove(h.handler)

        then:
        bytes.refCnt() == 0
        if (streaming) {
            assert h.errors.size() == 1
            assert h.errors[0] instanceof EOFException
        } else {
            // no application body exists until buffering finishes or devolves to streaming
            assert h.requests.empty
            assert h.errors.empty
        }
        h.channel.checkException()

        cleanup:
        h.close()

        where:
        compressed | streaming | stage
        false      | false     | 'buffering'
        true       | false     | 'buffering'
        false      | true      | 'streaming after buffering'
        true       | true      | 'streaming after buffering'
    }

    def 'a last content over the size limit fails the body once and the next request is served for compressed=#compressed streaming=#streaming'() {
        given:
        def h = new Harness()
        def bodies = []
        h.handler.setBodySizeLimits(new BodySizeLimits(2, Integer.MAX_VALUE))
        h.receive = { HttpRequest request, CloseableByteBody body ->
            bodies << body
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { Throwable failure ->
                h.errors << failure
            })
        }
        h.channel.writeOneInbound(request('/first', compressed))
        if (streaming) {
            h.channel.flushInbound()
        }
        def bytes = Unpooled.wrappedBuffer(compressed ? gzip('big') : 'big'.getBytes(StandardCharsets.UTF_8))

        when:
        h.channel.writeInbound(new DefaultLastHttpContent(bytes))
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        h.requests == ['/first', '/next']
        // a body that fails while it is buffered is handed over as a streaming body that fails
        bodies[0] instanceof StreamingNettyByteBody
        h.errors.size() == 1
        h.errors[0] instanceof ContentLengthExceededException
        bytes.refCnt() == 0
        h.channel.checkException()

        cleanup:
        h.close()

        where:
        compressed | streaming
        false      | false
        false      | true
        true       | false
        true       | true
    }

    def 'a connection failure fails a compressed=#compressed streaming=#streaming body once, drops the rest and the next request is served'() {
        given:
        def h = new Harness()
        def failure = new IOException('body failed')
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { h.errors << it })
        }
        h.channel.writeOneInbound(request('/first', compressed))
        if (streaming) {
            h.channel.flushInbound()
        }

        when:
        h.channel.pipeline().fireExceptionCaught(failure)

        then:
        h.errors == [failure]

        when:
        // a second failure is not the body's anymore
        h.channel.pipeline().fireExceptionCaught(new IOException('later failure'))
        h.channel.writeInbound(new DefaultLastHttpContent())
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        h.requests == ['/first', '/next']
        h.errors.size() == 2
        h.errors[1].message == 'later failure'
        h.channel.checkException()

        cleanup:
        h.close()

        where:
        compressed | streaming
        false      | false
        false      | true
        true       | false
        true       | true
    }

    def 'a malformed last content fails a compressed=#compressed body once, and the connection is closed after its response'() {
        given:
        def h = new Harness()
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({}, { Throwable failure ->
                h.errors << failure
                h.outbounds[0].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE), NettyByteBodyFactory.empty())
            })
        }
        h.channel.writeInbound(request('/first', compressed))
        def bytes = Unpooled.buffer().writeByte(1)
        def last = new DefaultLastHttpContent(bytes)
        last.setDecoderResult(DecoderResult.failure(new TooLongFrameException('malformed trailer')))

        when:
        h.channel.writeInbound(last)
        HttpResponse response = h.channel.readOutbound()

        then:
        h.errors.size() == 1
        h.errors[0] instanceof HttpStatusException
        h.errors[0].status.code == 413
        bytes.refCnt() == 0
        response.status() == HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE
        response.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
        !h.channel.open
        h.channel.checkException()

        cleanup:
        ReferenceCountUtil.release(response)
        h.close()

        where:
        compressed << [false, true]
    }

    def 'a body that does not match its Content-Length while #stage fails once and the next request is served'() {
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
        h.errors[0].getClass().simpleName == 'IncorrectContentLengthException'
        h.requests == ['/first', '/next']
        h.channel.checkException()

        cleanup:
        h.close()

        where:
        stage        | expectedLength
        'reading'    | 1
        'completing' | 3
    }

    def 'decompression follows the body from buffering to streaming'() {
        given:
        def h = new Harness()
        def received = new StringBuilder()
        h.receive = { HttpRequest request, CloseableByteBody body ->
            Flux.from(body.toByteArrayPublisher()).subscribe({ received.append(new String(it, StandardCharsets.UTF_8)) }, { h.errors << it })
        }
        h.channel.writeOneInbound(request('/first', true))
        byte[] bytes = gzip('buffered then streamed')

        when:
        h.channel.writeOneInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(bytes, 0, bytes.length - 8)))
        h.channel.flushInbound()

        then: 'the request is handed over with the body that is still being read'
        h.requests == ['/first']
        h.events == [RequestBodyEvent.STREAMING]

        when:
        h.channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(bytes, bytes.length - 8, 8)))
        h.channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/next'))

        then:
        received.toString() == 'buffered then streamed'
        h.requests == ['/first', '/next']
        h.errors.empty

        cleanup:
        h.close()
    }

    private static void consume(CloseableByteBody body, Closure error, Closure complete) {
        if (body instanceof StreamingNettyByteBody) {
            body.primary(new BufferConsumer() {
                @Override
                void add(ReadBuffer buffer) {
                    buffer.close()
                }

                @Override
                void complete() {
                    complete.call()
                }

                @Override
                void error(Throwable failure) {
                    error.call(failure)
                }
            }).start()
        } else {
            Flux.from(body.toByteArrayPublisher()).subscribe({}, error, complete)
        }
    }

    private static HttpRequest request(String uri, boolean gzip = false) {
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri)
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        if (gzip) {
            request.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP)
        }
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
        final List<Object> events = []
        final EmbeddedChannel channel = new EmbeddedChannel(handler, new ChannelInboundHandlerAdapter() {
            @Override
            void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof RequestBodyEvent) {
                    events << evt
                }
                ctx.fireUserEventTriggered(evt)
            }
        })
        final List<String> requests = []
        final List<OutboundAccess> outbounds = []
        final List<Throwable> errors = []
        Closure receive

        @Override
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
            requests << request.uri()
            outbounds << outboundAccess
            receive(request, body)
        }

        @Override
        void handleUnboundError(Throwable cause) {
            errors << cause
        }

        void close() {
            channel.finishAndReleaseAll()
        }
    }
}
