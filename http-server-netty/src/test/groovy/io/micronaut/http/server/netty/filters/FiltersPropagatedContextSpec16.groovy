package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.order.Ordered
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.FilterContinuation
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * {@link ExecutionFlow} filter methods mixed with reactive, asynchronous and blocking filters and routes.
 */
class FiltersPropagatedContextSpec16 extends Specification {

    static final String REACTOR_KEY = "reactor-key"
    static final String REACTOR_VALUE = "reactor-value"

    void "test execution flow filters propagate the context"(String innerFilter, String path) {
        given:
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                    'spec.name'        : FiltersPropagatedContextSpec16.simpleName,
                    'spec.inner-filter': innerFilter
            ])
            HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
            HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET(path), String)
        then:
            response.body() == "OK"
            response.header("X-Around") == "flow"
            response.header("X-Response") == "flow"
            response.header("X-Reactive") == "reactive"

        cleanup:
            client.close()
            server.close()

        where:
            [innerFilter, path] << [
                    ["flow", "flow-execute-on", "reactive-execute-on"],
                    ["/filters-flow/sync", "/filters-flow/sync-io", "/filters-flow/async", "/filters-flow/async-io", "/filters-flow/reactive", "/filters-flow/reactive-io"]
            ].combinations()
    }

    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    @Controller("/filters-flow")
    static class FiltersController {
        @Get("/sync")
        String sync() {
            validateMyContextIsPresent()
            "OK"
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/sync-io")
        String syncIO() {
            validateMyContextIsPresent()
            "OK"
        }

        @Get("/async")
        CompletableFuture<String> async() {
            validateMyContextIsPresent()
            CompletableFuture.completedFuture("OK")
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/async-io")
        CompletableFuture<String> asyncIO() {
            validateMyContextIsPresent()
            CompletableFuture.completedFuture("OK")
        }

        @Get("/reactive")
        Mono<String> reactive() {
            validateMyContextIsPresent()
            Mono.deferContextual { contextView ->
                validateMyContextIsPresent(contextView)
                validateReactorValueIsPresent(contextView)
                Mono.just("OK")
            }
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/reactive-io")
        Mono<String> reactiveIO() {
            validateMyContextIsPresent()
            Mono.deferContextual { contextView ->
                validateMyContextIsPresent(contextView)
                validateReactorValueIsPresent(contextView)
                Mono.just("OK")
            }
        }
    }

    /**
     * Adds the context element with an {@link ExecutionFlow} continuation.
     */
    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    static class AroundFlowFilter implements Ordered {

        @RequestFilter
        ExecutionFlow<HttpResponse<?>> filter(HttpRequest<?> request,
                                              FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation,
                                              MutablePropagatedContext mutablePropagatedContext) {
            mutablePropagatedContext.add(new MyContext())
            return continuation.request(request).proceed().map { response ->
                ((MutableHttpResponse<?>) response).header("X-Around", "flow")
            }
        }

        @Override
        int getOrder() {
            return 1
        }
    }

    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    static class RequestFlowFilter implements Ordered {

        @RequestFilter
        ExecutionFlow<HttpRequest<?>> filter(HttpRequest<?> request) {
            validateMyContextIsPresent()
            return ExecutionFlow.just(request)
        }

        @ResponseFilter
        ExecutionFlow<MutableHttpResponse<?>> filterResponse(MutableHttpResponse<?> response) {
            validateMyContextIsPresent()
            return ExecutionFlow.just(response.header("X-Response", "flow"))
        }

        @Override
        int getOrder() {
            return 2
        }
    }

    /**
     * Writes a Reactor context value that must reach the reactive routes through the
     * {@link ExecutionFlow} filters below it.
     */
    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    static class ReactiveFilter implements Ordered {

        @RequestFilter
        Publisher<MutableHttpResponse<?>> filter(HttpRequest<?> request,
                                                 FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation) {
            validateMyContextIsPresent()
            return Mono.deferContextual { contextView ->
                validateMyContextIsPresent(contextView)
                Mono.from(continuation.request(request).proceed())
            }.map { response ->
                response.header("X-Reactive", "reactive")
            }.contextWrite { it.put(REACTOR_KEY, REACTOR_VALUE) }
        }

        @Override
        int getOrder() {
            return 3
        }
    }

    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    @Requires(property = 'spec.inner-filter', value = 'flow')
    static class InnerFlowFilter implements Ordered {

        @RequestFilter
        ExecutionFlow<HttpResponse<?>> filter(FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation) {
            validateMyContextIsPresent()
            return continuation.proceed()
        }

        @Override
        int getOrder() {
            return 4
        }
    }

    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    @Requires(property = 'spec.inner-filter', value = 'flow-execute-on')
    static class InnerExecuteOnFlowFilter implements Ordered {

        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        ExecutionFlow<HttpResponse<?>> filter(FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation) {
            validateMyContextIsPresent()
            return continuation.proceed()
        }

        @Override
        int getOrder() {
            return 4
        }
    }

    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    @Requires(property = 'spec.inner-filter', value = 'reactive-execute-on')
    static class InnerExecuteOnReactiveFilter implements Ordered {

        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        Publisher<MutableHttpResponse<?>> filter(FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation) {
            validateMyContextIsPresent()
            return continuation.proceed()
        }

        @Override
        int getOrder() {
            return 4
        }
    }

    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    static class CompletableFutureFilter implements Ordered {

        @RequestFilter
        CompletableFuture<HttpRequest<?>> filter(HttpRequest<?> request) {
            validateMyContextIsPresent()
            return CompletableFuture.completedFuture(request)
        }

        @Override
        int getOrder() {
            return 5
        }
    }

    @ServerFilter("/filters-flow/**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec16')
    static class BlockingFilter implements Ordered {

        @RequestFilter
        void filter() {
            validateMyContextIsPresent()
        }

        @Override
        int getOrder() {
            return 6
        }
    }

    static void validateMyContextIsPresent() {
        if (PropagatedContext.get().find(MyContext).isEmpty()) {
            throw new IllegalAccessException("My context element is missing!")
        }
    }

    static void validateMyContextIsPresent(ContextView contextView) {
        if (ReactorPropagation.findContextElement(contextView, MyContext).isEmpty()) {
            throw new IllegalAccessException("My context element is missing in the Reactor context!")
        }
    }

    static void validateReactorValueIsPresent(ContextView contextView) {
        if (contextView.getOrDefault(REACTOR_KEY, null) != REACTOR_VALUE) {
            throw new IllegalAccessException("The Reactor context value is missing!")
        }
    }

    static class MyContext implements PropagatedContextElement {
    }

}
