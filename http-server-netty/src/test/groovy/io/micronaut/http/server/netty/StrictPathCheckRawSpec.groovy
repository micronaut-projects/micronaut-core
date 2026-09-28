package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Request targets that the HTTP client does not send as they are.
 */
class StrictPathCheckRawSpec extends Specification {

    def "#method #target with the strict check #strict is #status"() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StrictPathCheckRawSpec', 'micronaut.server.strict-path-check': strict])
        def server = ctx.getBean(EmbeddedServer).start()

        expect:
        status(server, method, target) == status

        cleanup:
        ctx.close()

        where:
        method    | target                          | strict | status
        'GET'     | '/strict-raw/..#'               | false  | 200
        'GET'     | '/strict-raw/..#'               | true   | 400
        'GET'     | '/strict-raw/x/%2e%2e#'         | true   | 400
        'GET'     | '/strict-raw/À®/a'    | true   | 400
        'GET'     | '/strict-raw/a\u0085'           | true   | 400
        'GET'     | '/strict-raw/a'                 | true   | 200
        'OPTIONS' | '*'                             | false  | 404
        'OPTIONS' | '*'                             | true   | 404
    }

    private static int status(EmbeddedServer server, String method, String target) {
        new Socket(server.host, server.port).withCloseable { socket ->
            socket.soTimeout = 10_000
            // ISO-8859-1 sends a char below 256 as the one raw byte
            socket.outputStream.write("$method $target HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            def statusLine = new BufferedReader(new InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII)).readLine()
            Integer.parseInt(statusLine.split(' ')[1])
        }
    }

    @Requires(property = 'spec.name', value = 'StrictPathCheckRawSpec')
    @Controller('/strict-raw')
    static class RawController {
        @Get('/{+rest}')
        String get(@PathVariable String rest) {
            'ok'
        }
    }
}
