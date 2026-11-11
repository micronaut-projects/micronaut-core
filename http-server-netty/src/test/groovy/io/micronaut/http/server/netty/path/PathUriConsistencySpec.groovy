package io.micronaut.http.server.netty.path

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher
import io.micronaut.http.MutableHttpResponse
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

/**
 * getPath() (routing, filter selection) and getUri() (mutable request, proxying, static resources) must name the same
 * path (ANT-2026-B30UOVDU, ANT-2026-F8T5LR27).
 */
class PathUriConsistencySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'PathUriConsistencySpec'])

    private String raw(String target) {
        try (Socket s = new Socket('127.0.0.1', server.port)) {
            s.soTimeout = 10_000
            s.outputStream.write("GET $target HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            s.outputStream.flush()
            return new String(s.inputStream.bytes, StandardCharsets.ISO_8859_1)
        }
    }

    @Unroll
    def 'B30UOVDU: an allow-list filter reading the mutable request path is not bypassed by #target'() {
        when:
        def resp = raw(target)

        then:
        !resp.startsWith('HTTP/1.1 200')
        !resp.contains('top-secret')

        where:
        target << [
                'http:/public/x?://y/admin/secret',
                'http:/public/x#://y/admin/secret',
                'http:/public/://y/admin/secret',
        ]
    }

    @Unroll
    def 'B30UOVDU/F8T5LR27: getPath() and getUri() agree for #target'() {
        when:
        def resp = raw(target)
        def body = resp.substring(resp.indexOf('\r\n\r\n') + 4)

        then:
        resp.startsWith('HTTP/1.1 200')
        body.startsWith('path=')
        def parts = body.split('\\|uri=')
        parts[1] == parts[0].substring('path='.length())

        where:
        target << [
                '//x/echo/admin',
                'http://localhost/echo/%61dmin',
                'http://localhost/echo/a%2Fb',
        ]
    }

    @Requires(property = 'spec.name', value = 'PathUriConsistencySpec')
    @Filter('/**')
    static class AllowListFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            String path = ((MutableHttpRequest<?>) request.mutate()).getPath()
            if (path.startsWith('/public/') || path.startsWith('/echo/') || path.startsWith('//x/echo/')) {
                return chain.proceed(request)
            }
            return request.headers.get('X-Api-Token') == 'tok' ? chain.proceed(request) : Publishers.just(HttpResponse.unauthorized())
        }
    }

    @Requires(property = 'spec.name', value = 'PathUriConsistencySpec')
    @Controller
    static class Ctrl {
        @Get('/admin/secret')
        String secret() {
            'top-secret'
        }

        @Get('/public/status')
        String status() {
            'ok'
        }

        @Get('/{+rest}')
        String echo(String rest, HttpRequest<?> request) {
            "path=${request.path}|uri=${request.uri.rawPath}"
        }
    }
}
