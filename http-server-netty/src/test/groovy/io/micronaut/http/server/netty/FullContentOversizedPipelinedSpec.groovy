package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * In FULL_CONTENT mode, the 413 for an oversized request is written after the responses to
 * earlier pipelined requests, not ahead of them.
 */
class FullContentOversizedPipelinedSpec extends Specification {

    void "the 413 for an oversized pipelined request follows the earlier response"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                         : 'FullContentOversizedPipelinedSpec',
                'micronaut.server.netty.server-type': 'FULL_CONTENT',
                'micronaut.server.max-request-size' : 1024,
        ])
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 5000

        when:
        socket.outputStream.write((
                "GET /oversized-pipelined/slow HTTP/1.1\r\nHost: a\r\n\r\n" +
                "POST /oversized-pipelined/upload HTTP/1.1\r\nHost: a\r\nContent-Length: 100000\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        socket.outputStream.flush()
        String response = new String(socket.inputStream.readAllBytes(), StandardCharsets.US_ASCII)

        then: "the slow response comes first, then the 413, then the connection closes"
        response.startsWith('HTTP/1.1 200')
        response.contains('slow-body')
        response.indexOf('slow-body') < response.indexOf('HTTP/1.1 413')

        cleanup:
        socket?.close()
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'FullContentOversizedPipelinedSpec')
    @Controller('/oversized-pipelined')
    static class SlowController {
        @Get('/slow')
        Mono<String> slow() {
            Mono.just('slow-body').delayElement(Duration.ofMillis(500))
        }
    }
}
