package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.server.annotation.PreMatching
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * A HEAD request is answered like a GET, so the filters scoped to GET apply to it.
 */
class HeadGetScopedFilterSpec extends Specification {

    @TempDir
    Path dir

    private static String firstLine(int port, String request) {
        try (Socket s = new Socket('127.0.0.1', port)) {
            s.soTimeout = 10_000
            s.outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
            return new BufferedReader(new InputStreamReader(s.inputStream, StandardCharsets.ISO_8859_1)).readLine()
        }
    }

    def 'a GET-scoped @PreMatching filter guards the implicit HEAD route'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HeadGetScopedFilterSpec.pre'])
        def ctrl = server.applicationContext.getBean(PreGuardedController)

        expect:
        firstLine(server.port, "GET /pre-guarded HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        firstLine(server.port, "HEAD /pre-guarded HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        ctrl.calls == 0

        cleanup:
        server.close()
    }

    def 'a GET-scoped filter guards HEAD of a static resource'() {
        given:
        Files.writeString(dir.resolve('report.txt'), 'confidential-report')
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                         : 'HeadGetScopedFilterSpec.static',
                'micronaut.router.static-resources.private.paths'  : 'file:' + dir.toString(),
                'micronaut.router.static-resources.private.mapping': '/private/**',
        ])

        expect:
        firstLine(server.port, "GET /private/report.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        firstLine(server.port, "HEAD /private/report.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')

        cleanup:
        server.close()
    }

    @Requires(property = 'spec.name', value = 'HeadGetScopedFilterSpec.pre')
    @ServerFilter(value = '/pre-guarded/**', methods = HttpMethod.GET)
    static class PreGetOnlyFilter {
        @PreMatching
        @RequestFilter
        HttpResponse<?> deny() {
            HttpResponse.status(HttpStatus.FORBIDDEN)
        }
    }

    @Requires(property = 'spec.name', value = 'HeadGetScopedFilterSpec.pre')
    @Controller('/pre-guarded')
    static class PreGuardedController {
        volatile int calls

        @Get
        String get() {
            calls++
            'secret-body'
        }
    }

    @Requires(property = 'spec.name', value = 'HeadGetScopedFilterSpec.static')
    @ServerFilter(value = '/private/**', methods = HttpMethod.GET)
    static class StaticGetOnlyFilter {
        @RequestFilter
        HttpResponse<?> deny() {
            HttpResponse.status(HttpStatus.FORBIDDEN)
        }
    }
}
