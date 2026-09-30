package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.sse.SseClient
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import spock.lang.Specification

class BalancedClientLifecycleSpec extends Specification {

    def 'clients created with a URL are closed when the context closes'() {
        given:
        def ctx = ApplicationContext.run()
        def url = new URL('http://localhost:1')
        def clients = [
                ctx.createBean(HttpClient, url),
                ctx.createBean(DefaultHttpClient, url),
                ctx.createBean(SseClient, url),
                ctx.createBean(StreamingHttpClient, url),
                ctx.createBean(ProxyHttpClient, url),
                ctx.createBean(WebSocketClient, url),
        ]

        expect:
        clients.every { connectionManager(it).@running.get() }

        when:
        ctx.close()

        then:
        clients.every { !connectionManager(it).@running.get() }
    }

    def 'a client closed by the user is removed from the registry'() {
        given:
        def ctx = ApplicationContext.run()
        def registry = ctx.getBean(DefaultNettyHttpClientRegistry)
        def client = ctx.createBean(HttpClient, new URL('http://localhost:1'))

        expect:
        registry.@balancedClients.size() == 1

        when:
        client.close()

        then:
        registry.@balancedClients.isEmpty()

        when:
        client.start()

        then: 'a restarted client is tracked again'
        registry.@balancedClients.size() == 1

        when:
        client.toBlocking().close()

        then:
        registry.@balancedClients.isEmpty()

        cleanup:
        ctx.close()
    }

    def 'creating and closing clients repeatedly does not grow the registry'() {
        given:
        def ctx = ApplicationContext.run()
        def registry = ctx.getBean(DefaultNettyHttpClientRegistry)
        def url = new URL('http://localhost:1')

        when:
        100.times {
            ctx.createBean(HttpClient, url).close()
        }

        then:
        registry.@balancedClients.isEmpty()

        cleanup:
        ctx.close()
    }

    def 'refresh reaches live balanced clients and does not restart closed ones'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'BalancedClientLifecycleSpec'])
        def ctx = server.applicationContext
        def live = ctx.createBean(HttpClient, server.URL)
        def closed = ctx.createBean(HttpClient, server.URL)
        closed.close()

        when:
        def firstPort = live.toBlocking().retrieve('/balanced-client/port')

        then: 'the pooled connection is reused'
        live.toBlocking().retrieve('/balanced-client/port') == firstPort

        when:
        ctx.getBean(Argument.of(ApplicationEventPublisher, RefreshEvent)).publishEvent(new RefreshEvent())

        then: 'the live client dropped its connection'
        live.toBlocking().retrieve('/balanced-client/port') != firstPort

        and: 'the closed client stays closed'
        !closed.isRunning()

        cleanup:
        live.close()
        server.close()
    }

    private static ConnectionManager connectionManager(Object client) {
        return ((DefaultHttpClient) client).connectionManager()
    }

    @Requires(property = 'spec.name', value = 'BalancedClientLifecycleSpec')
    @Controller('/balanced-client')
    static class PortController {
        @Get(value = '/port', produces = 'text/plain')
        String port(HttpRequest<?> request) {
            return String.valueOf(request.remoteAddress.port)
        }
    }
}
