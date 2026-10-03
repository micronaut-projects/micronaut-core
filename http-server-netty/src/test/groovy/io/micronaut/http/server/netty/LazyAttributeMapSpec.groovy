package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.http.BasicHttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.NettyMutableHttpResponse
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A request that neither the application nor the server stores attributes in completes without
 * creating the attribute map of the request or of the response.
 */
class LazyAttributeMapSpec extends Specification {

    static final String ROUTE_WAITS_FOR = BasicHttpAttributes.name + '.ROUTE_WAITS_FOR'

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'LazyAttributeMapSpec'])

    @Shared
    @AutoCleanup
    EmbeddedServer server = ctx.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = ctx.createBean(HttpClient, server.URI)

    def "a plain GET does not create the attribute maps"() {
        when:
        def result = client.toBlocking().retrieve('/lazy-attributes/get')
        def filter = awaitCleanup()

        then:
        result == 'get'
        filter.request instanceof NettyHttpRequest
        filter.response instanceof NettyMutableHttpResponse
        attributeMap(filter.request) == null
        attributeMap(filter.response) == null
    }

    def "binding a body stores the condition the route waits for in a field"() {
        when:
        def result = client.toBlocking().retrieve(HttpRequest.POST('/lazy-attributes/post', 'foo').contentType(MediaType.TEXT_PLAIN))
        def filter = awaitCleanup()
        NettyHttpRequest<?> request = (NettyHttpRequest<?>) filter.request

        then:
        result == 'post foo'
        attributeMap(request) == null
        attributeMap(filter.response) == null
        request.routeWaitsForMetadata instanceof ExecutionFlow
        BasicHttpAttributes.getRouteWaitsFor(request).is(request.routeWaitsForMetadata)
        request.getAttribute(ROUTE_WAITS_FOR).get().is(request.routeWaitsForMetadata)
        attributeMap(request) == null

        when: 'the attribute map is created, it exposes the field'
        def attributes = request.attributes

        then:
        attributes.getValue(ROUTE_WAITS_FOR).is(request.routeWaitsForMetadata)
        attributes.names().contains(ROUTE_WAITS_FOR)

        when: 'the map writes through to the field'
        attributes.remove(ROUTE_WAITS_FOR)

        then:
        request.routeWaitsForMetadata == null
        request.getAttribute(ROUTE_WAITS_FOR).isEmpty()

        when:
        BasicHttpAttributes.addRouteWaitsFor(request, ExecutionFlow.just('a'))

        then:
        attributes.getValue(ROUTE_WAITS_FOR).is(request.routeWaitsForMetadata)
        !attributes.isEmpty()
    }

    private CapturingFilter awaitCleanup() {
        CapturingFilter filter = ctx.getBean(CapturingFilter)
        CountDownLatch disposed = filter.disposed
        assert disposed.await(10, TimeUnit.SECONDS)
        // the server checks whether to publish the terminated event right after releasing the
        // request, on the event loop of the connection: wait for that task to complete
        ((NettyHttpRequest<?>) filter.request).channelHandlerContext.executor().submit({ }).get(10, TimeUnit.SECONDS)
        return filter
    }

    private static Object attributeMap(Object message) {
        def field = message.getClass().getDeclaredField('attributes')
        field.accessible = true
        return field.get(message)
    }

    @Requires(property = 'spec.name', value = 'LazyAttributeMapSpec')
    @Controller('/lazy-attributes')
    static class TestController {
        @Get(value = '/get', produces = MediaType.TEXT_PLAIN)
        String get() {
            return 'get'
        }

        @Post(value = '/post', consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String post(@Body String body) {
            return 'post ' + body
        }
    }

    @Requires(property = 'spec.name', value = 'LazyAttributeMapSpec')
    @ServerFilter('/lazy-attributes/**')
    @Singleton
    static class CapturingFilter {
        volatile HttpRequest<?> request
        volatile MutableHttpResponse<?> response
        volatile CountDownLatch disposed

        @ResponseFilter
        void capture(HttpRequest<?> request, MutableHttpResponse<?> response) {
            CountDownLatch latch = new CountDownLatch(1)
            ((NettyHttpRequest<?>) request).addDisposalResource({ latch.countDown() } as Runnable)
            this.disposed = latch
            this.request = request
            this.response = response
        }
    }
}
