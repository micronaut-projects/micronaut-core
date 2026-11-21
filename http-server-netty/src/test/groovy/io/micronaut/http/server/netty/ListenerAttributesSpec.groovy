package io.micronaut.http.server.netty

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.web.router.RouteAttributes
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

/**
 * The listener attributes of a request are answered without creating the attribute map, and
 * appear in the map once it is created.
 */
class ListenerAttributesSpec extends Specification {

    EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter())

    def setup() {
        // marks the channel as the channel of a listener
        channel.attr(NettyHttpRequest.LISTENER_EXPOSES_DEFAULT_ROUTES).set(false)
    }

    def cleanup() {
        channel.finishAndReleaseAll()
    }

    NettyHttpRequest<Object> request() {
        return new NettyHttpRequest<>(
            new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo"),
            NettyByteBodyFactory.empty(),
            channel.pipeline().firstContext(),
            ConversionService.SHARED,
            new HttpServerConfiguration()
        )
    }

    def 'the listener attributes do not create the attribute map'() {
        given:
        def request = request()

        expect:
        RouteAttributes.getListenerExposesDefaultRoutes(request).get() == false
        // an embedded channel has no port
        RouteAttributes.getListenerPort(request).get() == -1
        request.getAttribute("micronaut.router.listener-port").get() == -1
        request.@attributes == null
        // a conversion goes through the map
        request.getAttribute("micronaut.router.listener-port", String).get() == "-1"
    }

    def 'the attribute map holds the listener attributes once created'() {
        given:
        def request = request()
        request.setAttribute("other", "x")

        expect:
        request.attributes.names().containsAll(["micronaut.router.listener-port", "micronaut.router.listener-exposes-default-routes", "other"])
        RouteAttributes.getListenerPort(request).get() == -1

        when:
        RouteAttributes.setListenerPort(request, 8080)

        then:
        RouteAttributes.getListenerPort(request).get() == 8080
        request.getAttribute("micronaut.router.listener-port").get() == 8080
    }
}
