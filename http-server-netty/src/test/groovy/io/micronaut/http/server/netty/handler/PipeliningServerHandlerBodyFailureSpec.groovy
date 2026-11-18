package io.micronaut.http.server.netty.handler

import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class PipeliningServerHandlerBodyFailureSpec extends Specification {
    def 'a buffered body #stage failure accepts a failed request and serves the next request for #failure'() {
        given:
        def accepted = []
        def bodyErrors = []
        def unboundErrors = []
        ByteBufAllocator allocator = Stub() {
            compositeBuffer(_) >> { int maxComponents ->
                Throwable bodyFailure = failure
                if (stage == 'composeBody') {
                    throw bodyFailure
                }
                new CompositeByteBuf(ByteBufAllocator.DEFAULT, false, maxComponents) {
                    @Override
                    int readableBytes() {
                        throw bodyFailure
                    }
                }
            }
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
        def first = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/first')
        first.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def piece1 = Unpooled.buffer().writeByte(1)
        def piece2 = Unpooled.buffer().writeByte(2)

        when:
        channel.writeOneInbound(first)
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
        FullHttpResponse failed = channel.readOutbound()
        FullHttpResponse next = channel.readOutbound()

        then:
        failed.status() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        next.status() == HttpResponseStatus.OK
        channel.readOutbound() == null

        cleanup:
        failed?.release()
        next?.release()
        channel.finishAndReleaseAll()

        where:
        stage           | failure
        'composeBody'   | new IllegalStateException('composite allocation failed')
        'composeBody'   | new OutOfMemoryError('direct memory exhausted')
        'createChecked' | new IllegalStateException('body adaptation failed')
        'createChecked' | new OutOfMemoryError('body adaptation exhausted memory')
    }
}
