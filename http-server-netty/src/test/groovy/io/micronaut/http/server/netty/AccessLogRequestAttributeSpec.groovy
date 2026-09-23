package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.AttributeKey
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The channel attribute named {@code NettyHttpRequest} exposes the current request to access
 * log elements while the access logger is enabled. It must be visible to the route while the
 * request is in flight, and must not keep the request reachable after the response is written.
 */
class AccessLogRequestAttributeSpec extends Specification {
    private static final AttributeKey<NettyHttpRequest> KEY = AttributeKey.valueOf(NettyHttpRequest.class.simpleName)

    def 'attribute holds the request during routing and is cleared once the response is written'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogRequestAttributeSpec',
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'AccessLogRequestAttributeSpec',
        ])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def client = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1024))
        EmbeddedTestUtil.connect(server, client)

        expect:
        server.attr(KEY).get() == null

        when:
        String first = exchange(server, client)
        then:
        first == 'same'
        server.attr(KEY).get() == null

        when: 'a second request on the same connection'
        String second = exchange(server, client)
        then:
        second == 'same'
        server.attr(KEY).get() == null

        cleanup:
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()
    }

    def 'attribute is not set when the access logger is disabled'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'AccessLogRequestAttributeSpec'])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def client = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1024))
        EmbeddedTestUtil.connect(server, client)

        when:
        String body = exchange(server, client)
        then:
        body == 'absent'
        server.attr(KEY).get() == null

        cleanup:
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()
    }

    private static String exchange(EmbeddedChannel server, EmbeddedChannel client) {
        client.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/access-log-attribute'))
        EmbeddedTestUtil.advance(server, client)
        FullHttpResponse response = client.readInbound()
        assert response.status() == HttpResponseStatus.OK
        String body = response.content().toString(StandardCharsets.UTF_8)
        response.release()
        return body
    }

    @Requires(property = 'spec.name', value = 'AccessLogRequestAttributeSpec')
    @Controller('/access-log-attribute')
    static class Ctrl {
        @Get
        String get(HttpRequest<?> request) {
            NettyHttpRequest<?> nettyRequest = (NettyHttpRequest<?>) request
            def value = nettyRequest.channelHandlerContext.channel().attr(KEY).get()
            if (value == null) {
                return 'absent'
            }
            return value.is(nettyRequest) ? 'same' : 'other'
        }
    }
}
