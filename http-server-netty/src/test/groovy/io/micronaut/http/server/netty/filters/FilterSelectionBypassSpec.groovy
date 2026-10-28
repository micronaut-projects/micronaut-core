package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.context.annotation.Replaces
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.resource.StaticResourceResolver
import io.micronaut.web.router.resource.TrackingStaticResourceResolver
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Filters scoped to a route must run for every request that reaches it.
 */
class FilterSelectionBypassSpec extends Specification {

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

    private static String raw(int port, String request) {
        try (Socket s = new Socket('127.0.0.1', port)) {
            s.soTimeout = 10_000
            s.outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
            return new String(s.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
        }
    }

    @Unroll
    def 'a static resource guarded by a GET/HEAD filter is not served for #method'() {
        given:
        Files.write(dir.resolve('report.txt'), 'confidential-report'.bytes)
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                          : 'FilterSelectionBypassSpec.static',
                'micronaut.router.static-resources.downloads.paths'  : 'file:' + dir.toString(),
                'micronaut.router.static-resources.downloads.mapping': '/downloads/**',
        ])

        expect: 'control: GET is rejected by the filter'
        raw(server.port, "GET /downloads/report.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                .startsWith('HTTP/1.1 401')

        when:
        def resp = raw(server.port, "$method /downloads/report.txt HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

        then:
        !resp.contains('confidential-report')
        !resp.startsWith('HTTP/1.1 200')

        cleanup:
        server.close()

        where:
        method << ['POST', 'PUT', 'DELETE']
    }

    def 'a GET-scoped filter also guards the HEAD route of a @Get method'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterSelectionBypassSpec.head'])

        expect:
        firstLine(server.port, "GET /head-guarded HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        firstLine(server.port, "HEAD /head-guarded HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        server.applicationContext.getBean(HeadGuardedController).calls.get() == 0

        cleanup:
        server.close()
    }

    def 'a filter chain that proceeds twice does not skip the next filter'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterSelectionBypassSpec.proceed'])

        when:
        def line = firstLine(server.port, "GET /proceed-once/secret HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")

        then:
        !line.startsWith('HTTP/1.1 200')
        server.applicationContext.getBean(ProceedController).calls.get() == 0

        cleanup:
        server.close()
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.static')
    @Filter(patterns = '/downloads/**', methods = [HttpMethod.GET, HttpMethod.HEAD])
    static class DownloadsFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            request.headers.get('X-Token') == 'tok' ? chain.proceed(request) : Flux.just(HttpResponse.unauthorized())
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.head')
    @Filter(value = '/head-guarded/**', methods = HttpMethod.GET)
    static class GetOnlyFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            Flux.just(HttpResponse.status(HttpStatus.FORBIDDEN))
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.head')
    @Controller('/head-guarded')
    static class HeadGuardedController {
        final AtomicInteger calls = new AtomicInteger()

        @Get
        String get() {
            calls.incrementAndGet()
            'secret-body'
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.proceed')
    @Filter('/proceed-once/**')
    static class Hedge implements HttpServerFilter {
        @Override
        int getOrder() {
            -100
        }

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            Mono.from(chain.proceed(request)).then(Mono.defer { Mono.from(chain.proceed(request)) })
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.proceed')
    @Filter('/proceed-once/**')
    static class Gate implements HttpServerFilter {
        @Override
        int getOrder() {
            0
        }

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            Mono.delay(Duration.ofMillis(150)).map { HttpResponse.status(HttpStatus.UNAUTHORIZED) }
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.proceed')
    @Controller('/proceed-once')
    static class ProceedController {
        final AtomicInteger calls = new AtomicInteger()

        @Get('/secret')
        String secret() {
            calls.incrementAndGet()
            'secret'
        }
    }

    @Unroll
    def 'a static resource stream opened for a rejected #method is closed'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterSelectionBypassSpec.streamed'])
        def resolver = server.applicationContext.getBean(TrackingResourceResolver)

        when:
        def resp = raw(server.port, "$method /tracked/report.txt HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

        then:
        resp.startsWith('HTTP/1.1 405')
        resolver.opened.get() == 1
        resolver.closed.get() == 1

        cleanup:
        server.close()

        where:
        method << ['POST', 'PUT']
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.streamed')
    @Singleton
    @Replaces(StaticResourceResolver)
    static class TrackingResourceResolver extends TrackingStaticResourceResolver {
        TrackingResourceResolver() {
            super('/tracked/report.txt')
        }
    }

}
