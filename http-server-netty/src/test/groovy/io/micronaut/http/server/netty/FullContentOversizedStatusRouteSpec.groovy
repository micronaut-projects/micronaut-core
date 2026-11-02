package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * In FULL_CONTENT mode, a global status route for 413 answers an oversized request with 413.
 */
class FullContentOversizedStatusRouteSpec extends Specification {

    void "a global 413 status route answers an oversized request with 413"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                         : 'FullContentOversizedStatusRouteSpec',
                'micronaut.server.netty.server-type': 'FULL_CONTENT',
                'micronaut.server.max-request-size' : 1024,
        ])
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 5000

        when:
        socket.outputStream.write((
                "POST /oversized-status-route/upload HTTP/1.1\r\nHost: a\r\nContent-Type: text/plain\r\nContent-Length: 100000\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        // the declared length alone exceeds the limit: no body is sent, so the close after the 413 is not reset
        socket.outputStream.flush()
        String response = new String(socket.inputStream.readAllBytes(), StandardCharsets.US_ASCII)

        then:
        response.startsWith('HTTP/1.1 413')
        response.contains('too-large-handled')

        cleanup:
        socket?.close()
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'FullContentOversizedStatusRouteSpec')
    @Controller('/oversized-status-route')
    static class UploadController {
        @Post('/upload')
        String upload(@Body String body) {
            'uploaded'
        }

        @Error(status = HttpStatus.REQUEST_ENTITY_TOO_LARGE, global = true)
        HttpResponse<String> tooLarge(HttpRequest<?> request) {
            HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE).body('too-large-handled')
        }
    }
}
