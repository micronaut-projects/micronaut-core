package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import jakarta.inject.Singleton
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Cancelling a websocket connect, and the handshake timeout, against a raw server that controls
 * when the upgrade is answered.
 */
class WebSocketConnectCancelSpec extends Specification {

    void 'cancelling the reactive connect during the handshake closes the connection'() {
        given:
        RawWebSocketServer server = new RawWebSocketServer(false)
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)
        OpenedEndpoints opened = ctx.getBean(OpenedEndpoints)

        when:
        Disposable subscription = Flux.from(client.connect(CancelClient, '/ws')).subscribe()
        server.awaitUpgradeRequest()
        subscription.dispose()

        then: 'the server sees the connection closed'
        server.awaitClosed()
        opened.endpoints.isEmpty()

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'cancelling the reactive connect while the open method runs closes the connection'() {
        given:
        RawWebSocketServer server = new RawWebSocketServer(true)
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)
        OpenedEndpoints opened = ctx.getBean(OpenedEndpoints)

        when:
        Disposable subscription = Flux.from(client.connect(SlowOpenClient, '/ws')).subscribe()
        server.awaitUpgradeRequest()
        CompletableFuture<?> openStage = opened.slowOpens.poll(10, TimeUnit.SECONDS)
        subscription.dispose()

        then: 'the server sees the connection closed'
        openStage != null
        server.awaitClosed()

        cleanup:
        openStage?.complete(null)
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'Reactor cancelling after the endpoint was delivered keeps the connection open'() {
        given:
        RawWebSocketServer server = new RawWebSocketServer(true)
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when: 'Mono.from cancels the publisher after the first element'
        CancelClient endpoint = Mono.from(Flux.from(client.connect(CancelClient, '/ws'))).block(Duration.ofSeconds(10))

        then:
        endpoint.session.open
        !server.closedWithin(500)
        endpoint.session.open

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'by default the handshake waits without a limit, whatever the read timeout'() {
        given:
        RawWebSocketServer server = new RawWebSocketServer(false)
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                        : 'WebSocketConnectCancelSpec',
                'micronaut.http.client.read-timeout': '500ms'
        ])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when:
        CompletableFuture<CancelClient> future = Mono.from(client.connect(CancelClient, '/ws')).toFuture()
        server.awaitUpgradeRequest()
        Thread.sleep(1500)

        then: 'as before 5.3, nothing fails the connect'
        !future.isDone()
        !server.closedWithin(100)

        cleanup:
        future?.cancel(true)
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'the handshake fails with the handshake timeout when the server never answers the upgrade'() {
        given:
        RawWebSocketServer server = new RawWebSocketServer(false)
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                             : 'WebSocketConnectCancelSpec',
                'micronaut.http.client.handshake-timeout': '1s'
        ])
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when:
        Mono.from(client.connect(CancelClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof ReadTimeoutException
        server.awaitClosed()

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'WebSocketConnectCancelSpec')
    static class OpenedEndpoints {
        final List<Object> endpoints = new CopyOnWriteArrayList<>()
        final java.util.concurrent.BlockingQueue<CompletableFuture<?>> slowOpens = new java.util.concurrent.LinkedBlockingQueue<>()
    }

    @ClientWebSocket
    static class CancelClient implements AutoCloseable {
        final OpenedEndpoints opened
        WebSocketSession session

        CancelClient(OpenedEndpoints opened) {
            this.opened = opened
        }

        @OnOpen
        void open(WebSocketSession session) {
            this.session = session
            opened.endpoints.add(this)
        }

        @OnMessage
        void onMessage(String text) {
        }

        @Override
        void close() {
            session?.close()
        }
    }

    @ClientWebSocket
    static class SlowOpenClient implements AutoCloseable {
        final OpenedEndpoints opened

        SlowOpenClient(OpenedEndpoints opened) {
            this.opened = opened
        }

        @OnOpen
        Mono<?> open() {
            CompletableFuture<?> stage = new CompletableFuture<>()
            opened.slowOpens.add(stage)
            return Mono.fromFuture(stage)
        }

        @OnMessage
        void onMessage(String text) {
        }

        @Override
        void close() {
            // a concrete endpoint whose close does not close the session
        }
    }

    /**
     * Accepts one connection, reads the upgrade request and either answers it or never does.
     */
    static class RawWebSocketServer implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private final CompletableFuture<Socket> accepted = new CompletableFuture<>()
        private final CompletableFuture<String> upgradeRequest = new CompletableFuture<>()
        private final CompletableFuture<Boolean> closed = new CompletableFuture<>()

        RawWebSocketServer(boolean answerUpgrade) {
            Thread.startDaemon('raw-websocket-server') {
                try {
                    Socket socket = serverSocket.accept()
                    accepted.complete(socket)
                    InputStream input = socket.inputStream
                    StringBuilder request = new StringBuilder()
                    while (!request.toString().endsWith('\r\n\r\n')) {
                        int b = input.read()
                        if (b < 0) {
                            upgradeRequest.complete(request.toString())
                            closed.complete(true)
                            return
                        }
                        request.append((char) b)
                    }
                    upgradeRequest.complete(request.toString())
                    if (answerUpgrade) {
                        String key = request.readLines().find { it.toLowerCase().startsWith('sec-websocket-key:') }.substring('sec-websocket-key:'.length()).trim()
                        String accept = Base64.encoder.encodeToString(MessageDigest.getInstance('SHA-1').digest((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').getBytes(StandardCharsets.US_ASCII)))
                        socket.outputStream.write(("HTTP/1.1 101 Switching Protocols\r\n" +
                                "Upgrade: websocket\r\n" +
                                "Connection: Upgrade\r\n" +
                                "Sec-WebSocket-Accept: ${accept}\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
                        socket.outputStream.flush()
                    }
                    // the client sends nothing else unless it closes: any frame or EOF counts
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

        void awaitUpgradeRequest() {
            upgradeRequest.get(10, TimeUnit.SECONDS)
        }

        boolean awaitClosed() {
            return closed.get(10, TimeUnit.SECONDS)
        }

        boolean closedWithin(long millis) {
            try {
                return closed.get(millis, TimeUnit.MILLISECONDS)
            } catch (java.util.concurrent.TimeoutException ignored) {
                return false
            }
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
