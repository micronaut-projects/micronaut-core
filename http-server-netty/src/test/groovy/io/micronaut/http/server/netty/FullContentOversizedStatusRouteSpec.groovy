package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * In FULL_CONTENT mode, a global status route for 413 answers an oversized request.
 */
class FullContentOversizedStatusRouteSpec extends Specification {

    void "a global 413 status route answers an oversized request"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                         : 'FullContentOversizedStatusRouteSpec',
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
        response.endsWith('too large')

        cleanup:
        socket?.close()
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'FullContentOversizedStatusRouteSpec')
    @Controller
    static class StatusController {
        @Error(status = HttpStatus.REQUEST_ENTITY_TOO_LARGE, global = true)
        HttpResponse<String> tooLarge() {
            return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE).body('too large')
        }
    }
}
