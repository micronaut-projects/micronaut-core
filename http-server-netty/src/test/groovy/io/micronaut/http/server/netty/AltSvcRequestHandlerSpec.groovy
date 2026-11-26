package io.micronaut.http.server.netty

import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.server.netty.handler.OutboundAccess
import io.micronaut.http.server.netty.handler.RequestHandler
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.util.function.Supplier

class AltSvcRequestHandlerSpec extends Specification {

    def 'adds the header to final responses that do not have one'() {
        given:
        def channel = new EmbeddedChannel()
        ChannelHandlerContext ctx = Mock { channel() >> channel }
        RequestHandler next = Mock()
        OutboundAccess outbound = Mock()
        OutboundAccess wrapped = null
        int resolved = 0
        def handler = new AltSvcRequestHandler(next, { c -> resolved++; return ({ 'h3=":8443"' } as Supplier<String>) })

        when:
        handler.accept(ctx, request(), Mock(CloseableByteBody), outbound)
        handler.accept(ctx, request(), Mock(CloseableByteBody), outbound)

        then: 'resolved once per connection'
        2 * next.accept(ctx, _, _, _) >> { args -> wrapped = args[3] }
        resolved == 1

        when:
        def ok = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        ByteBody body = null // sealed, and only passed through
        wrapped.write(ok, body)

        then:
        1 * outbound.write({ it.is(ok) }, _)
        ok.headers().get('alt-svc') == 'h3=":8443"'

        when:
        def head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        head.headers().set('Alt-Svc', 'clear')
        wrapped.writeHeadResponse(head)

        then:
        1 * outbound.writeHeadResponse({ it.is(head) })
        head.headers().get('alt-svc') == 'clear'

        when:
        def interim = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)
        wrapped.write(interim, body)

        then:
        1 * outbound.write({ it.is(interim) }, _)
        !interim.headers().contains('alt-svc')

        cleanup:
        channel.close()
    }

    def 'passes everything else through'() {
        given:
        def channel = new EmbeddedChannel()
        ChannelHandlerContext ctx = Mock { channel() >> channel }
        RequestHandler next = Mock()
        OutboundAccess outbound = Mock()
        OutboundAccess wrapped = null
        def handler = new AltSvcRequestHandler(next, { c -> ({ 'h3=":8443"' } as Supplier<String>) })
        Runnable callback = Mock()
        Runnable remover = Mock()
        Runnable discard = Mock()
        ChannelFuture future = Mock()
        def cause = new RuntimeException()

        when:
        handler.accept(ctx, request(), Mock(CloseableByteBody), outbound)
        wrapped.attachment('a')
        wrapped.closeAfterWrite()
        wrapped.skipCompression()
        def removed = wrapped.whenAbandoned(callback)
        def abandoned = wrapped.isAbandoned()
        def recorded = wrapped.recordFinalWrite()
        def finalWrite = wrapped.finalWrite()
        wrapped.abort()
        wrapped.runInResponseOrder(callback, discard)
        handler.handleUnboundError(cause)
        handler.responseWritten('a')
        handler.responseFailed('a', cause)
        handler.removed()

        then:
        1 * next.accept(ctx, _, _, _) >> { args -> wrapped = args[3] }
        1 * outbound.attachment('a')
        1 * outbound.closeAfterWrite()
        1 * outbound.skipCompression()
        1 * outbound.whenAbandoned(callback) >> remover
        removed.is(remover)
        1 * outbound.isAbandoned() >> true
        abandoned
        1 * outbound.recordFinalWrite() >> false
        !recorded
        1 * outbound.finalWrite() >> future
        finalWrite.is(future)
        1 * outbound.abort()
        1 * outbound.runInResponseOrder(callback, discard)
        1 * next.handleUnboundError(cause)
        1 * next.responseWritten('a')
        1 * next.responseFailed('a', cause)
        1 * next.removed()

        cleanup:
        channel.close()
    }

    def 'does not wrap the response when there is nothing to advertise'() {
        given:
        def channel = new EmbeddedChannel()
        ChannelHandlerContext ctx = Mock { channel() >> channel }
        RequestHandler next = Mock()
        OutboundAccess outbound = Mock()
        def handler = new AltSvcRequestHandler(next, { c -> ({ null } as Supplier<String>) })

        when:
        handler.accept(ctx, request(), Mock(CloseableByteBody), outbound)

        then:
        1 * next.accept(ctx, _, _, { it.is(outbound) })

        cleanup:
        channel.close()
    }

    def 'a channel of no listener advertises nothing'() {
        given:
        def channel = new EmbeddedChannel()

        expect:
        NettyHttpServer.altSvcHeader(channel).get() == null

        cleanup:
        channel.close()
    }

    private static DefaultHttpRequest request() {
        new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')
    }
}
