package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.filter.ConditionalFilter
import io.micronaut.http.server.annotation.PreMatching
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Filters scoped to a route must run for every request that reaches it.
 */
class FilterSelectionBypassSpec extends Specification {

    @TempDir
    Path dir

    private static String raw(int port, String request) {
        try (Socket s = new Socket('127.0.0.1', port)) {
            s.soTimeout = 10_000
            s.outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
            return new String(s.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
        }
    }

    private static String firstLine(int port, String request) {
        try (Socket s = new Socket('127.0.0.1', port)) {
            s.soTimeout = 10_000
            s.outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
            return new BufferedReader(new InputStreamReader(s.inputStream, StandardCharsets.ISO_8859_1)).readLine()
        }
    }

    @Unroll
    def 'a static resource guarded by a GET/HEAD filter is not served for #method'() {
        given:
        Files.writeString(dir.resolve('report.txt'), 'confidential-report')
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                            : 'FilterSelectionBypassSpec.static',
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
        method << ['POST', 'PUT', 'DELETE', 'FOO']
    }

    def 'a @PreMatching filter runs for a WebSocket upgrade'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterSelectionBypassSpec.ws'])
        def upgrade = "GET /internal/feed HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: Upgrade\r\n" +
                "Upgrade: websocket\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n"

        expect: 'control: a plain GET is rejected'
        firstLine(server.port, "GET /internal/feed HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 401')

        and: 'the upgrade is rejected too'
        !firstLine(server.port, upgrade).startsWith('HTTP/1.1 101')

        cleanup:
        server.close()
    }

    def 'a GET-scoped filter also guards the implicit HEAD route'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterSelectionBypassSpec.head'])

        expect: 'control: GET is rejected by the filter'
        firstLine(server.port, "GET /head-guarded HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')

        and: 'the implicit HEAD is rejected too'
        firstLine(server.port, "HEAD /head-guarded HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").startsWith('HTTP/1.1 403')
        server.applicationContext.getBean(HeadGuardedController).calls.get() == 0

        cleanup:
        server.close()
    }

    def 'a disabled conditional filter stays disabled when the request carries an Origin'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterSelectionBypassSpec.conditional'])
        def gate = server.applicationContext.getBean(DisabledGate)

        when:
        def plain = raw(server.port, "GET /conditional HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        def withOrigin = raw(server.port, "GET /conditional HTTP/1.1\r\nHost: localhost\r\nOrigin: http://other.example\r\nConnection: close\r\n\r\n")

        then:
        plain.contains('ROUTE')
        !withOrigin.toLowerCase().contains('x-gate-ran')
        gate.calls.get() == 0

        cleanup:
        server.close()
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.head')
    @ServerFilter(value = '/head-guarded/**', methods = HttpMethod.GET)
    static class GetOnlyFilter {
        @RequestFilter
        HttpResponse<?> deny() {
            HttpResponse.status(HttpStatus.FORBIDDEN)
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

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.conditional')
    @ServerFilter('/conditional/**')
    static class DisabledGate implements ConditionalFilter {
        final AtomicInteger calls = new AtomicInteger()

        @RequestFilter
        void request(HttpRequest<?> request) {
            calls.incrementAndGet()
        }

        @ResponseFilter
        void response(MutableHttpResponse<?> response) {
            calls.incrementAndGet()
            response.header('X-Gate-Ran', 'yes')
        }

        @Override
        boolean isEnabled(HttpRequest<?> request) {
            request.headers.get('X-Gate') == 'open'
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.conditional')
    @Controller('/conditional')
    static class ConditionalController {
        @Get
        String get() {
            'ROUTE'
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.static')
    @ServerFilter(patterns = '/downloads/**', methods = [HttpMethod.GET, HttpMethod.HEAD])
    static class DownloadsFilter {
        @RequestFilter
        @Nullable
        HttpResponse<?> filter(HttpRequest<?> request) {
            request.headers.get('X-Token') == 'tok' ? null : HttpResponse.unauthorized()
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.ws')
    @ServerFilter('/internal/**')
    static class InternalFilter {
        @PreMatching
        @RequestFilter
        @Nullable
        HttpResponse<?> filter(HttpRequest<?> request) {
            request.headers.get('X-Token') == 'tok' ? null : HttpResponse.unauthorized()
        }
    }

    @Requires(property = 'spec.name', value = 'FilterSelectionBypassSpec.ws')
    @ServerWebSocket('/internal/feed')
    static class InternalFeed {
        @OnOpen
        void open() {
        }

        @OnMessage
        String message(String msg) {
            msg
        }
    }
}
