package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * In FULL_CONTENT mode, the 413 the aggregator writes before closing the connection tells the
 * client that the connection closes.
 */
class FullContentOversizedCloseSpec extends Specification {

    void "the 413 for an oversized request carries Connection: close"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                         : 'FullContentOversizedCloseSpec',
                'micronaut.server.netty.server-type': 'FULL_CONTENT',
                'micronaut.server.max-request-size' : 1024,
        ])
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 5000

        when:
        socket.outputStream.write("POST /oversized HTTP/1.1\r\nHost: a\r\nContent-Length: 100000\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
        socket.outputStream.flush()
        String response = new String(socket.inputStream.readAllBytes(), StandardCharsets.US_ASCII)

        then:
        response.startsWith('HTTP/1.1 413')
        response.toLowerCase(Locale.ROOT).contains('connection: close')

        cleanup:
        socket?.close()
        server?.close()
    }
}
