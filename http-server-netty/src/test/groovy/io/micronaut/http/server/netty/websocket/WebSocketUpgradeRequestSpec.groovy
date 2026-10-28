package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * Upgrade requests that failed to decode or are rejected by the handshaker.
 */
class WebSocketUpgradeRequestSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketUpgradeRequestSpec'])

    def setup() {
        Endpoint.OPENED.set(0)
    }

    void "an upgrade whose head failed to decode is not upgraded: #description"() {
        when:
        String response = send(upgrade('/upgrade-request', headers))

        then:
        !response.startsWith('HTTP/1.1 101')
        Endpoint.OPENED.get() == 0

        where:
        description           | headers
        'duplicate length'    | 'Content-Length: 0\r\nContent-Length: 7\r\n'
        'invalid header name' | 'Bad(Name: x\r\nX-Marker: proxy-set\r\n'
    }

    void "an upgrade the handshaker rejects is answered: #description"() {
        when:
        String response = send(request)

        then:
        response.startsWith('HTTP/1.1 400')

        where:
        description     | request
        'extra token'   | 'GET /upgrade-request HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket, foo\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n'
        'missing key'   | 'GET /upgrade-request HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n\r\n'
    }

    private static String upgrade(String path, String extraHeaders) {
        return "GET $path HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n" + extraHeaders + "\r\n"
    }

    private String send(String head, byte[] body = new byte[0]) {
        Socket socket = new Socket('127.0.0.1', server.port)
        try {
            socket.soTimeout = 2500
            ByteArrayOutputStream req = new ByteArrayOutputStream()
            req.write(head.getBytes(StandardCharsets.ISO_8859_1))
            req.write(body)
            socket.outputStream.write(req.toByteArray())
            socket.outputStream.flush()
            InputStream input = socket.inputStream
            ByteArrayOutputStream out = new ByteArrayOutputStream()
            byte[] buf = new byte[1024]
            try {
                int n
                while ((n = input.read(buf)) != -1) {
                    out.write(buf, 0, n)
                    if (out.toString('ISO-8859-1').contains('\r\n\r\n')) {
                        break
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // no (complete) response
            }
            return out.toString('ISO-8859-1')
        } finally {
            socket.close()
        }
    }

    @ServerWebSocket('/upgrade-request')
    @Requires(property = 'spec.name', value = 'WebSocketUpgradeRequestSpec')
    static class Endpoint {
        static final AtomicInteger OPENED = new AtomicInteger()

        @OnOpen
        void open() {
            OPENED.incrementAndGet()
        }

        @OnMessage
        String message(String m) {
            return m
        }
    }
}
