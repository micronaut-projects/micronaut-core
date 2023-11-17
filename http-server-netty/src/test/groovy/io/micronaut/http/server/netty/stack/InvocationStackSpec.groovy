package io.micronaut.http.server.netty.stack

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonBlocking
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicBoolean

class InvocationStackSpec extends Specification {

    @Unroll
    void "test stack size for #method"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName()])
        StackCheckClient client = embeddedServer.applicationContext.getBean(StackCheckClient)

        expect:
        client."$method"()

        cleanup:
        embeddedServer.close()

        where:
        method << ["blocking", "nonblocking", "executeOn",
                   "withOneReactiveFilter", "withOneReactiveFilterExecuteOn",
                   "withTwoReactiveFilters", "withTwoReactiveFiltersExecuteOn",
                   "exception", "throwsExecuteOnEx"]
    }

    @Requires(property = "spec", value = "InvocationStackSpec")
    @Client("/stack-check")
    static interface StackCheckClient {
        @Get("/blocking")
        String blocking()

        @Get("/nonblocking")
        String nonblocking()

        @Get("/with-one-reactive-filter")
        String withOneReactiveFilter()

        @Get("/with-one-reactive-filter-execute-on")
        String withOneReactiveFilterExecuteOn()

        @Get("/with-two-reactive-filters")
        String withTwoReactiveFilters()

        @Get("/with-two-reactive-filters-execute-on")
        String withTwoReactiveFiltersExecuteOn()

        @Get("/execute-on")
        String executeOn()

        @Get("/exception")
        String exception()

        @Get("/exception-execute-on")
        String throwsExecuteOnEx()
    }

    @Requires(property = "spec", value = "InvocationStackSpec")
    @Controller("/stack-check")
    static class StackCheckController {

        @Inject
        MyOneFilter oneFilter

        @Inject
        MyTwoFilter1 twoFilters1
        @Inject
        MyTwoFilter2 twoFilters2

        @Get("/blocking")
        String blocking() {
            checkInvocationStack(false)
            return "OK"
        }

        @Get("/nonblocking")
        @NonBlocking
        String nonblocking() {
            checkInvocationStack(false)
            return "OK"
        }

        @Get("/with-one-reactive-filter")
        String withOneReactiveFilter() {
            if (!oneFilter.getExecuted().get()) {
                throw new IllegalStateException()
            }
            checkInvocationStack(false)
            return "OK"
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get("/with-one-reactive-filter-execute-on")
        String withOneReactiveFilterExecuteOn() {
            if (!oneFilter.getExecuted().get()) {
                throw new IllegalStateException()
            }
            checkInvocationStack(true)
            return "OK"
        }

        @Get("/with-two-reactive-filters")
        String withTwoReactiveFilters() {
            if (!twoFilters1.getExecuted().get() || !twoFilters2.getExecuted().get()) {
                throw new IllegalStateException()
            }
            checkInvocationStack(false)
            return "OK"
        }

        @Get("/with-two-reactive-filters-execute-on")
        String withTwoReactiveFiltersExecuteOn() {
            if (!twoFilters1.getExecuted().get() || !twoFilters2.getExecuted().get()) {
                throw new IllegalStateException()
            }
            checkInvocationStack(true)
            return "OK"
        }

        @Get("/execute-on")
        @ExecuteOn(TaskExecutors.IO)
        String scheduleBlocking() {
            checkInvocationStack(true)
            return "OK"
        }

        @Get("/exception")
        String throwsEx() {
            checkInvocationStack(false)
            throw new MyException()
        }

        @Error(MyException)
        HttpResponse<?> onException(MyException e) {
            checkInvocationStack(false)
            return HttpResponse.ok("OK")
        }

        @Get("/exception-execute-on")
        String throwsExecuteOnEx() {
            checkInvocationStack(false)
            throw new MyException2()
        }

        @ExecuteOn(TaskExecutors.IO)
        @Error(MyException2)
        HttpResponse<?> onExceptionExecuteOn(MyException2 e) {
            checkInvocationStack(true)
            return HttpResponse.ok("OK")
        }

        void checkInvocationStack(boolean allowExecutor) {
            for (StackTraceElement s in new RuntimeException().getStackTrace()) {
                if (!isKnownStack(s.className, allowExecutor)) {
                    throw new RuntimeException("Unknown stack member: " + s.className);
                }
            }
        }

        boolean isKnownStack(String className, boolean allowExecutor) {
            if (className.startsWith("java.util.concurrent")) {
                return true
            }
            if (className.startsWith("io.netty")) {
                return true
            }
            if (className.startsWith("io.micronaut")) {
                return true
            }
            if (className.startsWith("jdk.internal") || className.startsWith("java.lang")) {
                return true // Java
            }
            if (className.startsWith("org.codehaus.groovy") || className.startsWith("org.apache.groovy")) {
                return true // Spock
            }
            if (className == "reactor.core.publisher.Mono" || className == "reactor.core.publisher.MonoFromPublisher") {
                return true // added for kotlin context filters
            }
            return false
        }

    }

    static class MyException extends RuntimeException {

        MyException() {
            super(Thread.currentThread().getName())
        }

    }

    static class MyException2 extends RuntimeException {

        MyException2() {
            super(Thread.currentThread().getName())
        }

    }

    @Requires(property = "spec", value = "InvocationStackSpec")
    @Filter("/stack-check/with-one-reactive-filter*")
    static class MyOneFilter implements HttpServerFilter {

        final AtomicBoolean executed = new AtomicBoolean(false)

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executed.set(true)
            return chain.proceed(request)
        }
    }

    @Requires(property = "spec", value = "InvocationStackSpec")
    @Filter("/stack-check/with-two-reactive-filters*")
    static class MyTwoFilter1 implements HttpServerFilter {

        final AtomicBoolean executed = new AtomicBoolean(false)

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executed.set(true)
            return chain.proceed(request)
        }
    }

    @Requires(property = "spec", value = "InvocationStackSpec")
    @Filter("/stack-check/with-two-reactive-filters*")
    static class MyTwoFilter2 implements HttpServerFilter {

        final AtomicBoolean executed = new AtomicBoolean(false)

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executed.set(true)
            return chain.proceed(request)
        }
    }

}
