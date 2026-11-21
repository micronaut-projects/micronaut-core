package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * The idle timeout closes an h2c connection before its first request head, and a legacy-multiplex
 * HTTP/2 connection.
 */
class H2IdleTimeoutSpec extends Specification {

    void "an idle connection is closed after the idle timeout"(boolean legacy) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.idle-timeout'                  : '1s',
                'micronaut.server.http-version'                  : '2.0',
                'micronaut.server.netty.legacy-multiplex-handlers': legacy,
        ])

        when: "a client connects and sends nothing"
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 4000
        boolean closed
        try {
            closed = socket.inputStream.read() == -1
        } catch (SocketTimeoutException ignored) {
            closed = false
        } catch (IOException ignored) {
            closed = true
        }

        then: "the server closed it within 4x the idle timeout"
        closed

        cleanup:
        socket?.close()
        server?.close()

        where:
        legacy << [false, true]
    }
}
