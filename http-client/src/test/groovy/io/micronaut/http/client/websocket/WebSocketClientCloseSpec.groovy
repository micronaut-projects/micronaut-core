package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * The close of a client endpoint whose close handler returns a stage, against a raw server that
 * sends its close and waits.
 */
class WebSocketClientCloseSpec extends Specification {

    void 'the close of the server is not held back by a close handler stage that does not complete'() {
        given:
        RawClosingServer server = new RawClosingServer()
        ApplicationContext ctx = ApplicationContext.run()
        WebSocketClient client = ctx.createBean(WebSocketClient, server.uri)

        when:
        StageOnCloseClient endpoint = Mono.from(client.connect(StageOnCloseClient, '/ws')).block(Duration.ofSeconds(10))
        server.sendClose()

        then: 'the client answers the close, or closes the connection, at once'
        server.awaitCloseAnswered()
        endpoint.closeReason.get(10, TimeUnit.SECONDS).code == 1000

        cleanup:
        endpoint?.stage?.complete(null)
        client?.close()
        ctx?.close()
        server?.close()
    }

    @ClientWebSocket
    static class StageOnCloseClient implements AutoCloseable {
        final CompletableFuture<Object> stage = new CompletableFuture<>()
        final CompletableFuture<CloseReason> closeReason = new CompletableFuture<>()

        @OnMessage
        void onMessage(String text) {
        }

        @OnClose
        CompletionStage<?> onClose(CloseReason reason) {
            closeReason.complete(reason)
            // never completes while the test runs
            return stage
        }

        @Override
        void close() {
        }
    }

    /**
     * Accepts one connection, answers the upgrade, and sends a close when asked.
     */
    static class RawClosingServer implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private final CompletableFuture<Socket> upgraded = new CompletableFuture<>()
        private final CompletableFuture<Boolean> closeAnswered = new CompletableFuture<>()

        RawClosingServer() {
            Thread.startDaemon('raw-closing-websocket-server') {
                try {
                    Socket socket = serverSocket.accept()
                    InputStream input = socket.inputStream
                    StringBuilder request = new StringBuilder()
                    while (!request.toString().endsWith('\r\n\r\n')) {
                        int b = input.read()
                        if (b < 0) {
                            upgraded.completeExceptionally(new EOFException())
                            return
                        }
                        request.append((char) b)
                    }
                    String key = request.readLines().find { it.toLowerCase().startsWith('sec-websocket-key:') }.substring('sec-websocket-key:'.length()).trim()
                    String accept = Base64.encoder.encodeToString(MessageDigest.getInstance('SHA-1').digest((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').getBytes(StandardCharsets.US_ASCII)))
                    socket.outputStream.write(("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: ${accept}\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
                    socket.outputStream.flush()
                    upgraded.complete(socket)
                    // the first byte of a frame of the client: a close (0x88), or the end of the connection
                    int first = input.read()
                    closeAnswered.complete(first < 0 || (first & 0x0F) == 0x8)
                } catch (IOException e) {
                    closeAnswered.complete(true)
                }
            }
        }

        URI getUri() {
            return URI.create("http://127.0.0.1:${serverSocket.localPort}")
        }

        void sendClose() {
            Socket socket = upgraded.get(10, TimeUnit.SECONDS)
            // a close of the server, unmasked: 1000
            socket.outputStream.write([0x88, 0x02, 0x03, 0xE8] as byte[])
            socket.outputStream.flush()
        }

        boolean awaitCloseAnswered() {
            return closeAnswered.get(5, TimeUnit.SECONDS)
        }

        @Override
        void close() {
            serverSocket.close()
            if (upgraded.isDone() && !upgraded.isCompletedExceptionally()) {
                upgraded.get().close()
            }
        }
    }
}
