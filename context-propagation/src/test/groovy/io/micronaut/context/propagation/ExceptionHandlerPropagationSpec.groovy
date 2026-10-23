package io.micronaut.context.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.ThreadPropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ExceptionHandlerPropagationSpec extends Specification {

    private static final ThreadLocal<String> TRACE = new ThreadLocal<>()

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ExceptionHandlerPropagationSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(server.URL)

    void 'propagates context from #execution routes to #handler handlers'() {
        expect:
        3.times {
            String traceId = UUID.randomUUID().toString()
            String response = client.toBlocking().retrieve(HttpRequest.GET("/context-errors/$execution/$handler")
                .header('traceId', traceId))
            assert response == "$traceId:$traceId:$traceId"
            ExecutorService executor = server.applicationContext.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.IO))
            assert executor.submit({ -> [MDC.get('traceId'), TRACE.get()] } as Callable).get() == [null, null]
        }

        where:
        [execution, handler] << [['direct', 'io'], ['bean', 'offloaded-bean', 'route']].combinations()
    }

    private static String contextValues() {
        "${MDC.get('traceId')}:${TRACE.get()}:${PropagatedContext.get().find(TraceContext).map { it.traceId }.orElse(null)}"
    }

    // Use an uninstrumented executor so executor instrumentation cannot mask missing
    // propagation in the HTTP request lifecycle.
    @Factory
    @Requires(property = 'spec.name', value = 'ExceptionHandlerPropagationSpec')
    static class ExecutorFactory {
        @Singleton
        @Named(TaskExecutors.IO)
        @Replaces(value = ExecutorService, named = TaskExecutors.IO)
        @Bean(preDestroy = 'shutdown')
        ThreadPoolExecutor ioExecutor() {
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>())
        }
    }

    static class TraceContext implements ThreadPropagatedContextElement<String> {
        final String traceId

        TraceContext(String traceId) {
            this.traceId = traceId
        }

        @Override
        String updateThreadContext() {
            String previous = TRACE.get()
            TRACE.set(traceId)
            previous
        }

        @Override
        void restoreThreadContext(String oldState) {
            if (oldState == null) {
                TRACE.remove()
            } else {
                TRACE.set(oldState)
            }
        }
    }

    @Filter('/context-errors/**')
    @Requires(property = 'spec.name', value = 'ExceptionHandlerPropagationSpec')
    static class ContextFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            assert MDC.get('traceId') == null
            assert TRACE.get() == null
            String traceId = request.headers.get('traceId')
            PropagatedContext context = PropagatedContext.get()
                .plus(new MdcPropagationContext(['traceId': traceId]))
                .plus(new TraceContext(traceId))
            try (PropagatedContext.Scope ignored = context.propagate()) {
                return chain.proceed(request)
            }
        }
    }

    @Controller('/context-errors')
    @Requires(property = 'spec.name', value = 'ExceptionHandlerPropagationSpec')
    static class ErrorController {
        @Get('/direct/{handler}')
        String direct(String handler) {
            fail(handler)
        }

        @Get('/io/{handler}')
        @ExecuteOn(TaskExecutors.IO)
        String io(String handler) {
            fail(handler)
        }

        private static String fail(String handler) {
            assert TRACE.get() != null
            assert MDC.get('traceId') == TRACE.get()
            switch (handler) {
                case 'bean': throw new HandlerFailure()
                case 'offloaded-bean': throw new OffloadedHandlerFailure()
                default: throw new RouteFailure()
            }
        }
    }

    @Controller
    @Requires(property = 'spec.name', value = 'ExceptionHandlerPropagationSpec')
    static class GlobalErrorController {
        @Error(global = true, exception = RouteFailure)
        HttpResponse<String> error() {
            HttpResponse.ok(contextValues())
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ExceptionHandlerPropagationSpec')
    static class GlobalHandler implements ExceptionHandler<HandlerFailure, HttpResponse<String>> {
        @Override
        HttpResponse<String> handle(HttpRequest request, HandlerFailure exception) {
            HttpResponse.ok(contextValues())
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ExceptionHandlerPropagationSpec')
    static class OffloadedHandler implements ExceptionHandler<OffloadedHandlerFailure, HttpResponse<String>> {
        @Override
        @ExecuteOn(TaskExecutors.IO)
        HttpResponse<String> handle(HttpRequest request, OffloadedHandlerFailure exception) {
            HttpResponse.ok(contextValues())
        }
    }

    static class HandlerFailure extends RuntimeException {
    }

    static class OffloadedHandlerFailure extends RuntimeException {
    }

    static class RouteFailure extends RuntimeException {
    }
}