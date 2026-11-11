package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import io.micronaut.http.filter.FilterPatternStyle
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * Server filters scoped by the context path must run on every route the server serves.
 */
class ContextPathFilterCoverageSpec extends Specification {

    static final AtomicInteger OPENS = new AtomicInteger()

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                    : 'ContextPathFilterCoverageSpec',
            'micronaut.server.context-path': '/api'
    ])

    void 'a context path filter runs on a WebSocket upgrade outside the context path'() {
        when:
        String response = send("GET /cp-chat HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n", true)
        sleep(200)

        then:
        statusLine(response).contains('401')
        OPENS.get() == 0
    }

    void 'a REGEX filter applies to #path under the context path'() {
        expect:
        statusLine(get(path)).contains('403')

        where:
        path << ['/api/plain/x', '/api/anch/x', '/api/alt1/x', '/api/alt2/x']
    }

    void 'a filter pattern that begins with the context path string is mounted under the context path'() {
        when:
        String response = get('/api/apikeys/list')

        then:
        statusLine(response).contains('409')
        !response.contains('KEYS-LISTED')
    }

    void 'a filter pattern already under the context path is kept'() {
        expect:
        statusLine(get('/api/abs/x')).contains('418')
    }

    void 'a context path filter runs on #path'() {
        expect:
        statusLine(get(path, [:])).contains('401')

        where:
        path << ['/api/open', '/api']
    }

    void 'a request under the context path without a matching filter passes'() {
        when:
        String response = get('/api/open')

        then:
        statusLine(response).contains('200')
        response.contains('OPEN')
    }

    private String get(String path, Map<String, String> headers = ['X-Token': 'secret']) {
        def sb = new StringBuilder("GET ${path} HTTP/1.1\r\nHost: localhost\r\n")
        headers.each { k, v -> sb.append("${k}: ${v}\r\n") }
        sb.append("Connection: close\r\n\r\n")
        return send(sb.toString(), false)
    }

    private String send(String request, boolean headOnly) {
        Socket socket = new Socket("127.0.0.1", server.port)
        try {
            socket.soTimeout = 10_000
            socket.outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            def out = new ByteArrayOutputStream()
            def buf = new byte[8192]
            try {
                int n
                while ((n = socket.inputStream.read(buf)) != -1) {
                    out.write(buf, 0, n)
                    if (headOnly && out.toString(StandardCharsets.ISO_8859_1.name()).contains("\r\n\r\n")) {
                        break
                    }
                }
            } catch (SocketTimeoutException ignored) {
            }
            return out.toString(StandardCharsets.ISO_8859_1.name())
        } finally {
            socket.close()
        }
    }

    private static String statusLine(String response) {
        int i = response.indexOf("\r\n")
        return i < 0 ? response : response.substring(0, i)
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @Filter('/**')
    static class AuthFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return request.headers.get('X-Token') == 'secret' ? chain.proceed(request) : Mono.just(HttpResponse.status(HttpStatus.UNAUTHORIZED))
        }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @Filter('/apikeys/**')
    static class KeysGuard implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) { Mono.just(HttpResponse.status(HttpStatus.CONFLICT)) }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @Filter('/api/abs/**')
    static class AbsoluteGuard implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) { Mono.just(HttpResponse.status(HttpStatus.I_AM_A_TEAPOT)) }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @Filter(value = '^/anch/.*', patternStyle = FilterPatternStyle.REGEX)
    static class AnchFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) { Mono.just(HttpResponse.status(HttpStatus.FORBIDDEN)) }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @Filter(value = '/plain/.*', patternStyle = FilterPatternStyle.REGEX)
    static class PlainFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) { Mono.just(HttpResponse.status(HttpStatus.FORBIDDEN)) }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @Filter(value = '/alt1/.*|/alt2/.*', patternStyle = FilterPatternStyle.REGEX)
    static class AltFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) { Mono.just(HttpResponse.status(HttpStatus.FORBIDDEN)) }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @Controller
    static class Ctrl {
        @Get('/anch/x') String anch() { 'anch-body' }
        @Get('/plain/x') String plain() { 'plain-body' }
        @Get('/alt1/x') String alt1() { 'alt1-body' }
        @Get('/alt2/x') String alt2() { 'alt2-body' }
        @Get('/apikeys/list') String list() { 'KEYS-LISTED' }
        @Get('/abs/x') String abs() { 'ABS' }
        @Get('/open') String open() { 'OPEN' }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFilterCoverageSpec')
    @ServerWebSocket('/cp-chat')
    static class Chat {
        @OnOpen
        void open() {
            OPENS.incrementAndGet()
        }

        @OnMessage
        String message(String m) {
            return m
        }
    }
}
