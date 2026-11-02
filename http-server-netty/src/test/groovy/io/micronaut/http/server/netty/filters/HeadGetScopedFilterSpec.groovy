package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * A HEAD request of a static resource is answered like a GET, so the filters scoped to GET apply to it.
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

    def 'a GET-scoped filter guards HEAD of a static resource'() {
        given:
        Files.write(dir.resolve('report.txt'), 'confidential-report'.getBytes(StandardCharsets.UTF_8))
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

    @Requires(property = 'spec.name', value = 'HeadGetScopedFilterSpec.static')
    @Filter(value = '/private/**', methods = HttpMethod.GET)
    static class StaticGetOnlyFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN))
        }
    }
}
