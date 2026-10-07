package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketPongMessage
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.exceptions.WebSocketClientException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The outcomes of a websocket connect that fails after the connection was established, and the
 * connect by URI template parameters, against a raw server that controls the upgrade response.
 */
class WebSocketConnectFailureSpec extends Specification {

    void 'connect by parameters expands the path of the client websocket'() {
        given:
        RawServer server = new RawServer(RawServer.UPGRADE)
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectFailureSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when:
        TemplateClient endpoint = Flux.from(client.connect(TemplateClient, [name: 'bob'])).blockFirst(Duration.ofSeconds(10))

        then:
        server.requestLine().startsWith('GET /ws/bob ')
        endpoint.session.open

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a response that is not an upgrade fails the connect and closes the connection'() {
        given:
        RawServer server = new RawServer('HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n')
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectFailureSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when:
        Mono.from(client.connect(TemplateClient, '/ws/x')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketClientException
        e.cause.message.startsWith('Error finishing WebSocket handshake')
        server.awaitClosed()

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a message method with two message parameters fails the connect and closes the connection'() {
        given:
        RawServer server = new RawServer(RawServer.UPGRADE)
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectFailureSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when:
        Mono.from(client.connect(TwoMessageParametersClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketClientException
        e.cause.message.contains('should define exactly 1 message parameter')
        server.awaitClosed()

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a pong method with another unbound parameter fails the connect and closes the connection'() {
        given:
        RawServer server = new RawServer(RawServer.UPGRADE)
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectFailureSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when:
        Mono.from(client.connect(BadPongClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketClientException
        e.cause.message.contains('should define exactly 1 pong message parameter')
        server.awaitClosed()

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a relative path without a server URL fails the connect when subscribed'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectFailureSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, (URI) null)

        when:
        def publisher = client.connect(TemplateClient, '/ws/x')

        then: 'nothing happens before the subscription'
        noExceptionThrown()

        when:
        Mono.from(publisher).toFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause != null

        cleanup:
        client?.close()
        ctx?.close()
    }

    @Requires(property = 'spec.name', value = 'WebSocketConnectFailureSpec')
    @ClientWebSocket('/ws/{name}')
    static class TemplateClient implements AutoCloseable {
        WebSocketSession session

        @OnOpen
        void open(WebSocketSession session) {
            this.session = session
        }

        @OnMessage
        void onMessage(String text) {
            // the server sends no message
        }

        @Override
        void close() {
            session?.close()
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketConnectFailureSpec')
    @ClientWebSocket
    static abstract class TwoMessageParametersClient implements AutoCloseable {
        @OnMessage
        void onMessage(String first, String second) {
            // never called: the connect fails
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketConnectFailureSpec')
    @ClientWebSocket
    static abstract class BadPongClient implements AutoCloseable {
        @OnMessage
        void onMessage(String text) {
            // never called: the connect fails
        }

        @OnMessage
        void onPong(WebSocketPongMessage pong, String extra) {
            // never called: the connect fails
        }
    }

    /**
     * Accepts one connection, reads the upgrade request and writes the given response, or the
     * upgrade for {@link #UPGRADE}.
     */
    static class RawServer implements AutoCloseable {
        static final String UPGRADE = 'upgrade'

        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private final CompletableFuture<Socket> accepted = new CompletableFuture<>()
        private final CompletableFuture<String> request = new CompletableFuture<>()
        private final CompletableFuture<Boolean> closed = new CompletableFuture<>()

        RawServer(String response) {
            Thread.startDaemon('raw-websocket-failure-server') {
                try {
                    Socket socket = serverSocket.accept()
                    accepted.complete(socket)
                    InputStream input = socket.inputStream
                    StringBuilder text = new StringBuilder()
                    while (!text.toString().endsWith('\r\n\r\n')) {
                        int b = input.read()
                        if (b < 0) {
                            break
                        }
                        text.append((char) b)
                    }
                    request.complete(text.toString())
                    String answer = response
                    if (response == UPGRADE) {
                        String key = text.readLines().find { it.toLowerCase().startsWith('sec-websocket-key:') }.substring('sec-websocket-key:'.length()).trim()
                        String accept = Base64.encoder.encodeToString(MessageDigest.getInstance('SHA-1').digest((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').getBytes(StandardCharsets.US_ASCII)))
                        answer = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n"
                    }
                    socket.outputStream.write(answer.getBytes(StandardCharsets.US_ASCII))
                    socket.outputStream.flush()
                    while (input.read() >= 0) {
                        // drain the close frame, if any
                    }
                    closed.complete(true)
                } catch (IOException e) {
                    closed.complete(true)
                }
            }
        }

        URI getUri() {
            return URI.create("http://127.0.0.1:${serverSocket.localPort}")
        }

        String requestLine() {
            return request.get(10, TimeUnit.SECONDS).readLines()[0]
        }

        boolean awaitClosed() {
            return closed.get(10, TimeUnit.SECONDS)
        }

        @Override
        void close() {
            serverSocket.close()
            if (accepted.isDone()) {
                accepted.get().close()
            }
        }
    }
}
