package io.micronaut.http.server.netty.path

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * a query-only request target serves the root route while a methods-scoped '/**'
 * filter is skipped.
 */
class QueryOnlyRequestTargetSpec extends Specification {

    void "a query-only request target does not bypass the root route's filter"(String target) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'QueryOnlyRequestTargetSpec'])

        when:
        String control = send(server, '/')
        String response = send(server, target)

        then:
        control.startsWith('HTTP/1.1 403')
        !response.startsWith('HTTP/1.1 200')
        !response.contains('root-secret')

        cleanup:
        server?.close()

        where:
        target << ['?x', '?']
    }

    static String send(EmbeddedServer server, String target) {
        new Socket(server.host, server.port).withCloseable { s ->
            s.soTimeout = 10_000
            s.outputStream.write("GET $target HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            s.outputStream.flush()
            return new String(s.inputStream.readAllBytes(), StandardCharsets.UTF_8)
        }
    }

    @Requires(property = 'spec.name', value = 'QueryOnlyRequestTargetSpec')
    @Controller
    static class RootController {
        @Get('/')
        String root() {
            'root-secret'
        }
    }

    @Requires(property = 'spec.name', value = 'QueryOnlyRequestTargetSpec')
    @ServerFilter(value = '/**', methods = [HttpMethod.GET, HttpMethod.OPTIONS])
    static class GuardFilter {
        @RequestFilter
        MutableHttpResponse<?> filter(HttpRequest<?> request) {
            HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
        }
    }
}
