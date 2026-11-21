package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.ThreadPropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * The terminated event runs with the context of its own request (e.g. the MDC or the trace a filter
 * added), also for pipelined requests.
 */
class TerminatedEventOwnRequestContextSpec extends Specification {

    static final Map<String, List<String>> SEEN = new ConcurrentHashMap<>()

    void 'terminated events run with the context of their own request'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'TerminatedEventOwnRequestContextSpec', 'micronaut.server.port': -1])
        EmbeddedServer server = ctx.getBean(EmbeddedServer)
        server.start()

        when:
        Socket socket = new Socket('127.0.0.1', server.port)
        socket.soTimeout = 5000
        socket.outputStream.write(("GET /f39own/slow-mono HTTP/1.1\r\nHost: localhost\r\nX-User: alice\r\n\r\n" +
                "GET /f39own/fast HTTP/1.1\r\nHost: localhost\r\nX-User: bob\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        socket.outputStream.flush()
        String response = readAll(socket.inputStream)
        socket.close()

        then:
        response.contains('slow') && response.contains('fast')
        new PollingConditions(timeout: 5).eventually {
            assert SEEN.size() == 2
        }
        SEEN['/f39own/slow-mono'] == ['alice', 'alice']
        SEEN['/f39own/fast'] == ['bob', 'bob']

        cleanup:
        ctx.close()
    }

    private static String readAll(InputStream input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        byte[] buf = new byte[8192]
        try {
            int n
            while ((n = input.read(buf)) != -1) {
                out.write(buf, 0, n)
            }
        } catch (SocketTimeoutException | SocketException ignored) {
        }
        return out.toString(StandardCharsets.ISO_8859_1)
    }

    static final ThreadLocal<String> USER = new ThreadLocal<>()

    record Tag(String user) implements ThreadPropagatedContextElement<String> {
        @Override
        String updateThreadContext() {
            String old = USER.get()
            USER.set(user)
            return old
        }

        @Override
        void restoreThreadContext(String oldState) {
            if (oldState == null) {
                USER.remove()
            } else {
                USER.set(oldState)
            }
        }
    }

    @ServerFilter('/f39own/**')
    @Requires(property = 'spec.name', value = 'TerminatedEventOwnRequestContextSpec')
    static class TagFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext context) {
            context.add(new Tag(request.headers.get('X-User')))
        }
    }

    @Controller('/f39own')
    @Requires(property = 'spec.name', value = 'TerminatedEventOwnRequestContextSpec')
    static class Ctrl {
        @Get('/slow-mono')
        Mono<String> slow(HttpRequest<?> request) {
            def loop = ((NettyHttpRequest) request).channelHandlerContext.channel().eventLoop()
            Mono.create { sink -> loop.schedule({ sink.success('slow') } as Runnable, 300, java.util.concurrent.TimeUnit.MILLISECONDS) }
        }

        @Get('/fast')
        String fast() { 'fast' }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'TerminatedEventOwnRequestContextSpec')
    static class Listener implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        @Override
        void onApplicationEvent(HttpRequestTerminatedEvent event) {
            SEEN.put(event.source.path, [USER.get(), *PropagatedContext.getOrEmpty().findAll(Tag).map { it.user() }.toList()].findAll { it != null })
        }
    }
}
