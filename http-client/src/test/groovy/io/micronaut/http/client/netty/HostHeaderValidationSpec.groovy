package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.netty.resolver.AbstractAddressResolver
import io.netty.resolver.AddressResolver
import io.netty.resolver.AddressResolverGroup
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.Promise
import jakarta.inject.Named
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

class HostHeaderValidationSpec extends Specification {
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'spec.name': 'HostHeaderValidationSpec',
            // resolves every name to loopback, so that the request would reach the server below
            // if the host was not rejected
            'micronaut.http.client.address-resolver-group-name': 'host-header-validation-resolver',
    ])

    @AutoCleanup
    ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.loopbackAddress)

    def 'request to a URI whose decoded authority contains #description fails without connecting'() {
        given:
        def client = ctx.createBean(HttpClient)
        def uri = new URI("http://a${encoded}X-Injected:${serverSocket.localPort}/")

        expect: 'the URI host is taken from the decoded registry-based authority'
        uri.host == null

        when:
        client.toBlocking().exchange(HttpRequest.GET(uri))

        then:
        thrown(HttpClientException)

        when:
        client.close()

        then:
        !connectionAccepted()

        where:
        description | encoded
        'CR/LF'     | '%0d%0a'
        'LF'        | '%0a'
        'NUL'       | '%00'
        'space'     | '%20'
    }

    def 'websocket connection to a URI whose decoded authority contains CR/LF fails without connecting'() {
        given:
        def client = ctx.createBean(WebSocketClient)
        def uri = new URI("ws://a%0d%0aX-Injected:${serverSocket.localPort}/")

        when:
        Mono.from(client.connect(StubWebSocketClient, uri)).block()

        then:
        thrown(HttpClientException)

        when:
        client.close()

        then:
        !connectionAccepted()
    }

    private boolean connectionAccepted() {
        // a connection the client opened would be waiting in the backlog
        serverSocket.soTimeout = 500
        try {
            serverSocket.accept().close()
            return true
        } catch (SocketTimeoutException ignored) {
            return false
        }
    }

    @ClientWebSocket
    @Requires(property = "spec.name", value = "HostHeaderValidationSpec")
    static class StubWebSocketClient implements AutoCloseable {
        @OnOpen
        void onOpen(WebSocketSession session) {}

        @OnMessage
        void onMessage(String text) {}

        @Override
        void close() {}
    }

    @Singleton
    @Requires(property = "spec.name", value = "HostHeaderValidationSpec")
    @Named("host-header-validation-resolver")
    static class LoopbackResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
            return new AbstractAddressResolver<InetSocketAddress>(executor) {
                @Override
                protected boolean doIsResolved(InetSocketAddress address) {
                    return !address.isUnresolved()
                }

                @Override
                protected void doResolve(InetSocketAddress unresolvedAddress, Promise<InetSocketAddress> promise) throws Exception {
                    promise.trySuccess(new InetSocketAddress(InetAddress.loopbackAddress, unresolvedAddress.port))
                }

                @Override
                protected void doResolveAll(InetSocketAddress unresolvedAddress, Promise<List<InetSocketAddress>> promise) throws Exception {
                    promise.trySuccess([new InetSocketAddress(InetAddress.loopbackAddress, unresolvedAddress.port)])
                }
            }
        }
    }
}
