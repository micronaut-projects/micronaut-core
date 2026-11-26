package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.PathVariables
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.RequestCondition
import io.micronaut.web.router.builder.RequestHandler
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicStreamChannel
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The remote address of a request on a QUIC stream, whose own address is a stream id, is that of
 * its connection.
 */
class Http3RemoteAddressSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
        'spec.name'                                               : 'Http3RemoteAddressSpec',
        'micronaut.server.ssl.enabled'                            : true,
        'micronaut.server.ssl.buildSelfSigned'                    : true,
        'micronaut.server.netty.listeners.a.family'               : 'QUIC',
        'micronaut.server.netty.listeners.a.host'                 : '127.0.0.1',
        'micronaut.server.netty.listeners.a.port'                 : -1,

        'micronaut.http.client.alpn-modes'                        : ['h3'],
        'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
    ])

    @Shared
    @AutoCleanup
    EmbeddedServer server = ctx.getBean(EmbeddedServer).start()

    @AutoCleanup
    HttpClient httpClient = ctx.createBean(HttpClient, new URI("https://127.0.0.1:$server.port"))

    BlockingHttpClient client = httpClient.toBlocking()

    def 'remote address'() {
        when:
        def address = client.retrieve('/h3-remote-address/remote').split(' ')

        then:
        address[0] == '127.0.0.1'
        address[1].toInteger() > 0
        address[1].toInteger() != server.port
    }

    def 'client address resolver'() {
        expect:
        client.retrieve('/h3-remote-address/resolved') == '127.0.0.1'
    }

    def 'a route that excludes the address of the client is not matched'() {
        when:
        client.retrieve('/h3-remote-address/denied')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    def 'a route that allows the address of the client is matched'() {
        expect:
        client.retrieve('/h3-remote-address/allowed') == 'allowed'
    }

    def 'the remote address is still there after the connection is closed'() {
        given:
        def ctrl = ctx.getBean(Ctrl)
        CompletableFuture.supplyAsync { client.retrieve('/h3-remote-address/held') }
        def held = ctrl.held.poll(10, TimeUnit.SECONDS)
        QuicChannel connection = ((QuicStreamChannel) held.request.channelHandlerContext.channel()).parent()
        int clientPort = ((InetSocketAddress) connection.remoteSocketAddress()).port

        when:
        connection.close().sync()

        then:
        connection.remoteSocketAddress() == null
        held.request.remoteAddress == new InetSocketAddress('127.0.0.1', clientPort)

        cleanup:
        held?.response?.complete('done')
    }

    static class Held {
        final NettyHttpRequest<?> request
        final CompletableFuture<String> response = new CompletableFuture<>()

        Held(NettyHttpRequest<?> request) {
            this.request = request
        }
    }

    @Controller('/h3-remote-address')
    @Requires(property = 'spec.name', value = 'Http3RemoteAddressSpec')
    static class Ctrl {
        final BlockingQueue<Held> held = new LinkedBlockingQueue<>()
        final HttpClientAddressResolver resolver

        Ctrl(HttpClientAddressResolver resolver) {
            this.resolver = resolver
        }

        @Get('/remote')
        String remote(HttpRequest<?> request) {
            InetSocketAddress address = request.remoteAddress
            "${address?.address?.hostAddress} ${address?.port}"
        }

        @Get('/resolved')
        String resolved(HttpRequest<?> request) {
            String.valueOf(resolver.resolve(request))
        }

        @Get('/held')
        CompletableFuture<String> held(HttpRequest<?> request) {
            def h = new Held((NettyHttpRequest<?>) request)
            held.add(h)
            h.response
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'Http3RemoteAddressSpec')
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return { HttpRouteBuilder routes ->
                routes.GET('/h3-remote-address/denied')
                    .where(RequestCondition.not(RequestCondition.peerAddress('127.0.0.0/8')))
                    .handle({ HttpRequest<?> request, PathVariables variables -> HttpResponse.ok('let through') } as RequestHandler)
                routes.GET('/h3-remote-address/allowed')
                    .where(RequestCondition.peerAddress('127.0.0.0/8'))
                    .handle({ HttpRequest<?> request, PathVariables variables -> HttpResponse.ok('allowed') } as RequestHandler)
            } as HttpRoutes
        }
    }
}
