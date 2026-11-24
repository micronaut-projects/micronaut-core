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

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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
        description  | encoded
        'CR/LF'      | '%0d%0a'
        'LF'         | '%0a'
        'NUL'        | '%00'
        'space'      | '%20'
        'slash'      | '%2f'
        'at sign'    | '%40'
        'colon'      | '%3a'
        'C1 control' | '%C2%85'
        'non-ASCII'  | '%C3%BC'
    }

    def 'invalid port #port after user info fails without connecting or exposing the password'() {
        given:
        def client = ctx.createBean(HttpClient)
        def uri = new URI("http://alice:secret@my_service:${port}/")

        expect:
        uri.host == null

        when:
        client.toBlocking().exchange(HttpRequest.GET(uri))

        then:
        def e = thrown(HttpClientException)
        !e.message.contains('secret')
        !e.message.contains(port)

        when:
        client.close()

        then:
        !connectionAccepted()

        where:
        port << ['+80', '-1', '70000', '8o', '0080x']
    }

    def 'registry-based host #authority is still used'() {
        given:
        def client = ctx.createBean(HttpClient)
        def received = serveOnce("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        def uri = new URI("http://${authority}:${serverSocket.localPort}/")

        expect:
        uri.host == null

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET(uri))

        then:
        response.status().code == 200
        received.get(5, TimeUnit.SECONDS).contains("host: my_service:${serverSocket.localPort}\r\n")

        cleanup:
        client.close()

        where:
        authority << ['my_service', 'alice:secret@my_service', 'my%5Fservice']
    }

    def 'redirect to a URI whose decoded authority contains CR/LF fails without connecting'() {
        given:
        def client = ctx.createBean(HttpClient)
        def location = "http://a%0d%0aX-Injected:${serverSocket.localPort}/"
        def received = serveOnce("HTTP/1.1 302 Found\r\nLocation: ${location}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

        when:
        client.toBlocking().exchange(HttpRequest.GET(new URI("http://my_service:${serverSocket.localPort}/")))

        then:
        thrown(HttpClientException)
        received.get(5, TimeUnit.SECONDS).startsWith('GET / HTTP/1.1')

        when:
        client.close()

        then:
        !connectionAccepted()
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

    /**
     * Answer the next connection with the given response, on a daemon thread.
     */
    private CompletableFuture<String> serveOnce(String response) {
        def received = new CompletableFuture<String>()
        def thread = new Thread({
            try (def socket = serverSocket.accept()) {
                socket.soTimeout = 5000
                def bytes = new ByteArrayOutputStream()
                def input = socket.inputStream
                while (!bytes.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                    int b = input.read()
                    if (b == -1) {
                        break
                    }
                    bytes.write(b)
                }
                received.complete(bytes.toString(StandardCharsets.ISO_8859_1))
                socket.outputStream.write(response.getBytes(StandardCharsets.ISO_8859_1))
                socket.outputStream.flush()
            } catch (Exception failure) {
                received.completeExceptionally(failure)
            }
        })
        thread.daemon = true
        thread.start()
        return received
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
