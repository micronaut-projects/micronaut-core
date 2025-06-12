package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.order.Ordered
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.FilterContinuation
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import spock.lang.Specification

import java.util.concurrent.Callable

class FiltersPropagatedContextSpec8 extends Specification {

    void "test filter context propagation"() {
        given:
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FiltersPropagatedContextSpec8.simpleName])
            def applicationContext = server.applicationContext
            TheClient client = applicationContext.getBean(TheClient)

        when:
            def response1 = client.get()
        then:
            response1 == "OK"

        when:
            def response2 = client.getReactive()
        then:
            response2 == "OK"

        when:
            def response3 = client.getIO()
        then:
            response3 == "OK"

        when:
            def response4 = client.getReactiveIO()
        then:
            response4 == "OK"

        cleanup:
            server.close()
    }

    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec8')
    @Client("/")
    static interface TheClient {
        @Get("/filters-get")
        String get()

        @Get("/filters-get-io")
        String getIO()

        @Get("/filters-reactive-get")
        String getReactive()

        @Get("/filters-reactive-get-io")
        String getReactiveIO()
    }

    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec8')
    @Controller
    static class FiltersController {
        @Get("/filters-get")
        String get() {
            validateMyContextIsPresent()
            "OK"
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get("/filters-get-io")
        String getIO() {
            validateMyContextIsPresent()
            "OK"
        }

        @Get("/filters-reactive-get")
        Mono<String> getReactive() {
            validateMyContextIsPresent()
            return Mono.deferContextual {contextView ->
                Mono.fromCallable(new Callable<String>() {
                    @Override
                    String call() throws Exception {
                        validateMyContextIsPresent(contextView)
                        "OK"
                    }
                })
            }
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get("/filters-reactive-get-io")
        Mono<String> getReactiveIO() {
            validateMyContextIsPresent()
            return Mono.deferContextual {contextView ->
                Mono.fromCallable(new Callable<String>() {
                    @Override
                    String call() throws Exception {
                        validateMyContextIsPresent(contextView)
                        "OK"
                    }
                })
            }
        }

    }

    @ServerFilter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec8')
    static class Filter1 implements Ordered {

        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        MutableHttpResponse<?> myFilter(HttpRequest<?> request,
                                        FilterContinuation<MutableHttpResponse<?>> continuation,
                                        MutablePropagatedContext mutablePropagatedContext) {
            mutablePropagatedContext.add(new MyContext())
            return continuation.request(request).proceed()
        }

        @Override
        int getOrder() {
            return 1
        }
    }

    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec8')
    static class Filter2 implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            validateMyContextIsPresent()
            return chain.proceed(request)
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

    static validateMyContextIsPresent(ContextView contextView) {
        if (ReactorPropagation.findContextElement(contextView, MyContext).isEmpty()) {
            throw new IllegalAccessException("My context element is missing!")
        }
    }

    static class MyContext implements PropagatedContextElement {
    }

}
