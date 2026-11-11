package io.micronaut.http.server.netty

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.ByteBodyHttpResponseWrapper
import io.micronaut.http.HttpAttributes
import io.micronaut.http.netty.NettyMutableHttpResponse
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.runtime.http.scope.RequestCustomScope
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

/**
 * Reading an attribute that is not set answers without creating the attribute map of the
 * message, and reads still see what was set.
 */
class AbsentAttributeReadSpec extends Specification {

    EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter())

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

    def 'a typed read of an absent request attribute does not create the attribute map'() {
        given:
        def request = request()

        expect:
        request.getAttribute("absent", String).isEmpty()
        request.@attributes == null
    }

    def 'a typed read of a request attribute converts a value that was set'() {
        given:
        def request = request()
        request.setAttribute("number", "42")

        expect:
        request.getAttribute("number", Integer).get() == 42
    }

    def 'a typed read of route metadata does not need the attribute map'() {
        given:
        def request = request()
        request.setUriTemplateMetadata("/foo/{id}")

        expect:
        request.getAttribute(HttpAttributes.URI_TEMPLATE, String).get() == "/foo/{id}"
    }

    def 'a typed read of an absent response attribute does not create the attribute map'() {
        given:
        def response = new NettyMutableHttpResponse<Object>(ConversionService.SHARED)

        expect:
        response.getAttribute("absent", Boolean).isEmpty()
        response.@attributes == null

        when:
        response.setAttribute("flag", "true")

        then:
        response.getAttribute("flag", Boolean).get()
    }

    def 'a response wrapper reads the attributes of its delegate without creating the map'() {
        given:
        def response = new NettyMutableHttpResponse<Object>(ConversionService.SHARED)
        def wrapped = ByteBodyHttpResponseWrapper.wrap(response, NettyByteBodyFactory.empty())

        expect:
        wrapped.getAttribute("absent", Boolean).isEmpty()
        wrapped.getAttribute("absent").isEmpty()
        response.@attributes == null

        when:
        response.setAttribute("flag", true)

        then:
        wrapped.getAttribute("flag", Boolean).get()
        wrapped.getAttribute("flag").get() == true

        cleanup:
        wrapped.close()
    }

    def 'checking a request for request scoped beans does not create the attribute map'() {
        given:
        def request = request()
        def scope = new RequestCustomScope()

        expect:
        !scope.supports(new io.micronaut.http.context.event.HttpRequestTerminatedEvent(request))
        request.@attributes == null
    }
}
