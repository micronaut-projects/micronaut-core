package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class StrictPathCheckWebSocketSpec extends Specification {

    def "the strict path check applies to a websocket upgrade"() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StrictPathCheckWebSocketSpec', 'micronaut.server.strict-path-check': strict])
        def server = ctx.getBean(EmbeddedServer).start()

        expect:
        upgradeStatus(server, path) == status

        cleanup:
        ctx.close()

        where:
        strict | path                    | status
        true   | '/strict-ws/a'          | 101
        true   | '/strict-ws/a%2F..%2Fb' | 400
        true   | '/strict-ws/%2e%2e'     | 400
        false  | '/strict-ws/a%2F..%2Fb' | 101
    }

    private static int upgradeStatus(EmbeddedServer server, String path) {
        new Socket(server.host, server.port).withCloseable { socket ->
            socket.soTimeout = 10_000
            socket.outputStream.write(("GET $path HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "\r\n").getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            def statusLine = new BufferedReader(new InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII)).readLine()
            Integer.parseInt(statusLine.split(' ')[1])
        }
    }

    @Requires(property = 'spec.name', value = 'StrictPathCheckWebSocketSpec')
    @ServerWebSocket('/strict-ws/{name}')
    static class Socket1 {
        @OnMessage
        String onMessage(String message) {
            message
        }
    }
}
