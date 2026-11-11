package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Two listeners on the same port but different addresses keep their own default route exposure.
 */
class SamePortListenerExposureSpec extends Specification {

    void "a listener sharing the port of an exposing listener does not expose the default routes"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'SamePortListenerExposureSpec',
                'micronaut.server.netty.listeners.v4.host': '127.0.0.1',
                'micronaut.server.netty.listeners.v4.port': port,
                'micronaut.server.netty.listeners.v6.host': '::1',
                'micronaut.server.netty.listeners.v6.port': port,
                'micronaut.server.netty.listeners.v6.expose-default-routes': false,
        ])

        expect:
        statusLine('127.0.0.1', port, '/same-port-pub').contains(' 200 ')
        statusLine('::1', port, '/same-port-pub').contains(' 404 ')

        cleanup:
        server?.close()
    }

    static String statusLine(String host, int port, String path) {
        try (Socket socket = new Socket(host, port)) {
            socket.soTimeout = 5000
            socket.outputStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            return new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1).readLines()[0]
        }
    }

    @Requires(property = 'spec.name', value = 'SamePortListenerExposureSpec')
    @Controller('/same-port-pub')
    static class Pub {
        @Get
        String get() { 'pub' }
    }
}
