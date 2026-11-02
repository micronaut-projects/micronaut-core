package io.micronaut.function.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.function.FunctionBean
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

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * The HEAD route of a function runs the function like GET does, so GET-scoped filters apply to it.
 */
class HeadFunctionFilterSpec extends Specification {

    private static String firstLine(int port, String request) {
        try (Socket s = new Socket('127.0.0.1', port)) {
            s.soTimeout = 10_000
            s.outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
            return new BufferedReader(new InputStreamReader(s.inputStream, StandardCharsets.ISO_8859_1)).readLine()
        }
    }

    void "a GET-scoped filter guards the HEAD route of a supplier function"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HeadFunctionFilterSpec'])
        SecretFunction.CALLS.set(0)

        expect:
        firstLine(server.port, "GET /head-secret HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        firstLine(server.port, "HEAD /head-secret HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        SecretFunction.CALLS.get() == 0

        cleanup:
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'HeadFunctionFilterSpec')
    @FunctionBean("head-secret")
    static class SecretFunction implements Supplier<String> {
        static final AtomicInteger CALLS = new AtomicInteger()

        @Override
        String get() {
            CALLS.incrementAndGet()
            return 'secret-value'
        }
    }

    @Requires(property = 'spec.name', value = 'HeadFunctionFilterSpec')
    @Filter(value = '/head-secret/**', methods = HttpMethod.GET)
    static class GetOnlyFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN))
        }
    }
}
