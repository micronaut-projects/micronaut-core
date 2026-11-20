package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class PipeliningServerHandlerResponseOrderSpec extends Specification {
    def 'an action in response order runs after the earlier responses and blocks later ones'() {
        given:
        List<OutboundAccess> accesses = []
        List<String> events = []
        List<Object> written = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.attachment(request.uri())
                accesses << outboundAccess
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/third"))
        accesses[1].runInResponseOrder({ events << 'action' }, { events << 'discard' })
        accesses[2].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
        ch.runPendingTasks()

        then:
        events.isEmpty()
        ch.readOutbound() == null

        when:
        accesses[0].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
        ch.runPendingTasks()

        then:
        events == ['action']
        (ch.readOutbound() as FullHttpResponse).status() == HttpResponseStatus.NO_CONTENT
        ch.readOutbound() == null
        written == ['/first']

        when:
        ch.close()

        then:
        events == ['action']
        written.contains('/third')
        !written.contains('/second')
    }

    def 'an action that never gets its turn is discarded'() {
        given:
        List<OutboundAccess> accesses = []
        List<String> events = []
        List<Object> written = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.attachment(request.uri())
                accesses << outboundAccess
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second"))
        accesses[1].runInResponseOrder({ events << 'action' }, { events << 'discard' })
        ch.runPendingTasks()
        ch.close()

        then:
        events == ['discard']
        written.contains('/second')

        when: 'the connection is gone already'
        accesses[0].runInResponseOrder({ events << 'late action' }, { events << 'late discard' })

        then:
        events == ['discard', 'late discard']
    }

    def 'an action after a response for the same request is refused'() {
        given:
        List<OutboundAccess> accesses = []
        List<String> events = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                accesses << outboundAccess
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second"))
        accesses[1].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())

        when:
        accesses[1].runInResponseOrder({ events << 'action' }, { events << 'discard' })

        then:
        thrown(IllegalStateException)
        events == ['discard']

        cleanup:
        ch.close()
    }

    def 'the default runs the action right away'() {
        given:
        OutboundAccess access = Spy(OutboundAccess)
        List<String> events = []

        when:
        access.runInResponseOrder({ events << 'action' }, { events << 'discard' })

        then:
        events == ['action']
    }
}
