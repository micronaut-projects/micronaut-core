package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class FiltersPropagatedContextSpec15 extends Specification {

    void "test filter context propagation"() {
        given:
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FiltersPropagatedContextSpec15.simpleName])
            def applicationContext = server.applicationContext
            TheClient client = applicationContext.getBean(TheClient)

        when:
            client.asyncVoid()
        then:
            noExceptionThrown()
        when:
            client.asyncVoidIo()
        then:
            noExceptionThrown()
        when:
            client.reactiveVoid()
        then:
            noExceptionThrown()
        when:
            client.reactiveVoidIo()
        then:
            noExceptionThrown()

        cleanup:
            server.close()
    }

    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec15')
    @Client("/")
    static interface TheClient {

        @Get("/filters-async-void")
        void asyncVoid()

        @Get("/filters-async-void-io")
        void asyncVoidIo()

        @Get("/filters-async-void")
        void reactiveVoid()

        @Get("/filters-reactive-void-io")
        void reactiveVoidIo()
    }

    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec15')
    @Controller
    static class FiltersController {

        @Get("/filters-async-void")
        CompletionStage<Void> asyncVoid() {
            validateMyContextIsPresent()
            return CompletableFuture.completedStage(null)
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get("/filters-async-void-io")
        CompletionStage<Void> asyncVoidIo() {
            validateMyContextIsPresent()
            return CompletableFuture.completedStage(null)
        }

        @Get("/filters-reactive-void")
        Mono<Void> reactiveVoid() {
            validateMyContextIsPresent()
            return Mono.empty()
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get("/filters-reactive-void-io")
        Mono<Void> reactiveVoidIo() {
            validateMyContextIsPresent()
            return Mono.empty()
        }

    }

    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec15')
    static class Filter1 implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new MyContext()).propagate()) {
                return Mono.from(chain.proceed(request))
            }
        }

        @Override
        int getOrder() {
            return 1
        }
    }

    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec15')
    static class Filter2 implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            validateMyContextIsPresent()
            return Mono.from(chain.proceed(request))
        }

        @Override
        int getOrder() {
            return 2
        }
    }

    static validateMyContextIsPresent() {
        if (PropagatedContext.get().find(MyContext).isEmpty()) {
            throw new IllegalAccessException("My context element is missing!")
        }
    }

    static validateMyContextIsEmpty() {
        if (PropagatedContext.get().find(MyContext).isPresent()) {
            throw new IllegalAccessException("My context element is present!")
        }
    }

    static validateMyContextIsPresent(ContextView contextView) {
        if (ReactorPropagation.findContextElement(contextView, MyContext).isEmpty()) {
            throw new IllegalAccessException("My context element is missing!")
        }
    }

    private static class MyContext implements PropagatedContextElement {
    }

}
