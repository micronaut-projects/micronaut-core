package io.micronaut.http.server.netty.status

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A response whose status is set by code (e.g. {@code HttpResponse.status(HttpStatus.NOT_MODIFIED)})
 * must not carry a Content-Length: RFC 9110 forbids it on 1xx and 204, and on 304 it would announce
 * the length of a representation that is not sent.
 */
class NoBodyStatusHeaderSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NoBodyStatusHeaderSpec'])

    void "a #path response over HTTP/1.1 has status #status and no Content-Length"() {
        when:
        List<String> lines = rawGet(path).split('\r\n') as List

        then:
        lines[0].startsWith("HTTP/1.1 $status ")
        !lines.any { it.toLowerCase(Locale.ROOT).startsWith('content-length:') }
        !lines.any { it.toLowerCase(Locale.ROOT).startsWith('transfer-encoding:') }

        where:
        path                         | status
        '/no-body/not-modified'      | 304
        '/no-body/not-modified-why'  | 304
        '/no-body/not-modified-code' | 304
        '/no-body/no-content'        | 204
        '/no-body/no-content-code'   | 204
        '/no-body/no-content-why'    | 204
    }

    void "a status set by code keeps its reason phrase"() {
        expect:
        rawGet('/no-body/not-modified').startsWith('HTTP/1.1 304 Not Modified\r\n')
        rawGet('/no-body/not-modified-code').startsWith('HTTP/1.1 304 Not Modified\r\n')
        rawGet('/no-body/not-modified-why').startsWith('HTTP/1.1 304 Unchanged\r\n')
    }

    private String rawGet(String path) {
        Socket socket = new Socket(server.host, server.port)
        try {
            socket.soTimeout = 10_000
            socket.outputStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            String response = new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
            return response.substring(0, response.indexOf('\r\n\r\n') + 2)
        } finally {
            socket.close()
        }
    }

    @Requires(property = 'spec.name', value = 'NoBodyStatusHeaderSpec')
    @Controller('/no-body')
    static class NoBodyController {
        @Get('/not-modified')
        HttpResponse<?> notModified() {
            return HttpResponse.status(HttpStatus.NOT_MODIFIED)
        }

        @Get('/not-modified-why')
        HttpResponse<?> notModifiedWhy() {
            return HttpResponse.status(HttpStatus.NOT_MODIFIED, 'Unchanged')
        }

        @Get('/not-modified-code')
        HttpResponse<?> notModifiedCode() {
            return HttpResponse.ok().status(HttpStatus.NOT_MODIFIED)
        }

        @Get('/no-content')
        HttpResponse<?> noContent() {
            return HttpResponse.noContent()
        }

        @Get('/no-content-code')
        HttpResponse<?> noContentCode() {
            return HttpResponse.ok().status(HttpStatus.NO_CONTENT)
        }

        @Get('/no-content-why')
        HttpResponse<?> noContentWhy() {
            return HttpResponse.status(HttpStatus.NO_CONTENT, 'Nothing')
        }
    }
}
