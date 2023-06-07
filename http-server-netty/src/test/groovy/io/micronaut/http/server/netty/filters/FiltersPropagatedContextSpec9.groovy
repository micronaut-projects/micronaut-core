package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
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
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.Callable

class FiltersPropagatedContextSpec9 extends Specification {

    void "test filter context propagation"() {
        given:
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FiltersPropagatedContextSpec9.simpleName])
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

    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec9')
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

    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec9')
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
            return Mono.fromCallable(new Callable<String>() {
                @Override
                String call() throws Exception {
                    validateMyContextIsPresent()
                    "OK"
                }
            })
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get("/filters-reactive-get-io")
        Mono<String> getReactiveIO() {
            validateMyContextIsPresent()
            return Mono.fromCallable(new Callable<String>() {
                @Override
                String call() throws Exception {
                    validateMyContextIsPresent()
                    "OK"
                }
            })
        }

    }

    @ServerFilter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec9')
    static class Filter1 {

        def static context1 = new MyContext()
        def static context2 = new MyContext()
        def static context3 = new MyContext()

        @Order(1)
        @RequestFilter
        void myFilter1(MutablePropagatedContext mutablePropagatedContext) {
            validateMyContextIsEmpty()
            mutablePropagatedContext.add(context1)
        }

        @Order(2)
        @RequestFilter
        void myFilter2(MutablePropagatedContext mutablePropagatedContext) {
            validateMyContextIsPresent()
            mutablePropagatedContext.replace(context1, context2)
        }

        @Order(3)
        @RequestFilter
        void myFilter3(MutablePropagatedContext mutablePropagatedContext) {
            validateMyContextIsPresent()
            if (mutablePropagatedContext.getContext().get(MyContext) != context2) {
                throw new IllegalStateException("Not matching")
            }
        }

        @Order(4)
        @RequestFilter
        void myFilter4(MutablePropagatedContext mutablePropagatedContext) {
            validateMyContextIsPresent()
            mutablePropagatedContext.remove(context2).add(context3)
        }

        @Order(5)
        @RequestFilter
        void myFilter5(MutablePropagatedContext mutablePropagatedContext) {
            validateMyContextIsPresent()
            if (mutablePropagatedContext.getContext().get(MyContext) != context3) {
                throw new IllegalStateException("Not matching")
            }
        }

    }

    @Filter("/filters**")
    @Requires(property = 'spec.name', value = 'FiltersPropagatedContextSpec9')
    static class Filter6 implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            validateMyContextIsPresent()
            return chain.proceed(request)
        }

        @Override
        int getOrder() {
            return 6
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

    static class MyContext implements PropagatedContextElement {
    }

}
