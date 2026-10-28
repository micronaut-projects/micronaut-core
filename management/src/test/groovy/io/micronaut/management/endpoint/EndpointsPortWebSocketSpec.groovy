package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.Retry
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * WebSocket upgrades must respect the listener a request arrived on.
 */
class EndpointsPortWebSocketSpec extends Specification {

    private static String upgrade(int port, String path) {
        try (Socket s = new Socket('127.0.0.1', port)) {
            s.soTimeout = 10_000
            s.outputStream.write(("GET $path HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
            def reader = new BufferedReader(new InputStreamReader(s.inputStream, StandardCharsets.ISO_8859_1))
            return reader.readLine()
        }
    }

    @Retry
    void "websocket endpoints are not upgraded on the management port"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'            : getClass().simpleName,
                'endpoints.all.port'   : port,
                'endpoints.all.enabled': true
        ])

        expect: 'the main listener upgrades'
        upgrade(server.port, '/ws/orders').startsWith('HTTP/1.1 101')

        and: 'the management listener does not'
        !upgrade(port, '/ws/orders').startsWith('HTTP/1.1 101')

        cleanup:
        server.close()
    }

    @Requires(property = 'spec.name', value = 'EndpointsPortWebSocketSpec')
    @ServerWebSocket('/ws/orders')
    static class OrdersSocket {
        @OnOpen
        void open() {
        }

        @OnMessage
        String message(String msg) {
            msg
        }
    }
}
