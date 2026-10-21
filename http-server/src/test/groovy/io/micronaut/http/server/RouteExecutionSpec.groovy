package io.micronaut.http.server

import io.micronaut.context.BeanContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.binding.RequestArgumentSatisfier
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor
import io.micronaut.scheduling.executor.ExecutorSelector
import io.micronaut.web.router.DefaultRouteInfo
import io.micronaut.web.router.RouteInfo
import io.micronaut.web.router.Router
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * The execution a route executor prepares for a route: selected once by the declared return type
 * of the route, kept by the route for its owner, and the compatibility execution for the routes
 * that cannot keep one.
 */
class RouteExecutionSpec extends Specification {

    private RouteExecutor routeExecutor() {
        BeanContext beanContext = Mock(BeanContext)
        beanContext.findBean(CoroutineHelper) >> Optional.empty()
        ErrorResponseProcessor errorResponseProcessor = Mock(ErrorResponseProcessor)
        return new RouteExecutor(
                Mock(Router),
                beanContext,
                Mock(RequestArgumentSatisfier),
                new HttpServerConfiguration(),
                errorResponseProcessor,
                Mock(ExecutorSelector)
        )
    }

    private static DefaultRouteInfo<?> route(ReturnType<?> returnType) {
        return new DefaultRouteInfo<>(returnType, Object, false, false)
    }

    void "the adapter of the result is selected by the declared return type"() {
        given:
        RouteExecutor executor = routeExecutor()

        expect:
        RouteResult.prepare(executor, route(returnType)).getClass() == adapter

        where:
        returnType                                                                              | adapter
        ReturnType.of(String)                                                                   | RouteResult.Imperative
        ReturnType.of(Optional, Argument.STRING)                                                | RouteResult.Imperative
        ReturnType.of(HttpResponse, Argument.STRING)                                            | RouteResult.Imperative
        ReturnType.of(HttpResponse, Argument.of(Mono, Argument.STRING))                         | RouteResult.Imperative
        ReturnType.of(CompletionStage, Argument.STRING)                                         | RouteResult.Stage
        ReturnType.of(CompletionStage, Argument.of(HttpResponse, Argument.STRING))              | RouteResult.Stage
        ReturnType.of(Mono, Argument.STRING)                                                    | RouteResult.Reactive
        ReturnType.of(Flux, Argument.STRING)                                                    | RouteResult.Reactive
        ReturnType.of(Mono, Argument.of(HttpResponse, Argument.of(Flux, Argument.STRING)))      | RouteResult.Reactive
        ReturnType.of(Object)                                                                   | RouteResult.PreparedDynamic
    }

    void "the facts of the nested shapes are read once"() {
        given:
        RouteExecutor executor = routeExecutor()

        when:
        RouteResult.Prepared facts = (RouteResult.Prepared) RouteResult.prepare(executor, route(ReturnType.of(Mono, Argument.of(HttpResponse, Argument.of(Flux, Argument.STRING)))))

        then:
        !facts.responseBodyAsyncOrReactive
        facts.nestedResponseBodyAsyncOrReactive
        !facts.nestedResponseBodyAsync
        facts.declaredSingle
        facts.streamOfResponses
        !facts.hasExecutor

        when:
        facts = (RouteResult.Prepared) RouteResult.prepare(executor, route(ReturnType.of(CompletionStage, Argument.of(HttpResponse, Argument.of(CompletionStage, Argument.STRING)))))

        then:
        facts.nestedResponseBodyAsync
        facts.nestedResponseBodyAsyncOrReactive

        when:
        facts = (RouteResult.Prepared) RouteResult.prepare(executor, route(ReturnType.of(HttpResponse, Argument.of(Flux, Argument.STRING))))

        then:
        facts.responseBodyAsyncOrReactive
    }

    void "a route keeps the execution its owner prepared"() {
        given:
        RouteExecutor executor = routeExecutor()
        DefaultRouteInfo<?> route = route(ReturnType.of(String))

        when:
        RouteExecution first = executor.execution(route)

        then:
        first.getClass().simpleName == "Direct"
        executor.execution(route).is(first)
        route.findPreparedExecution(executor).is(first)
    }

    void "the executions of two owners are their own"() {
        given:
        RouteExecutor one = routeExecutor()
        RouteExecutor other = routeExecutor()
        DefaultRouteInfo<?> route = route(ReturnType.of(String))

        when:
        RouteExecution ofOne = one.execution(route)
        RouteExecution ofOther = other.execution(route)

        then:
        ofOne.owner.is(one)
        ofOther.owner.is(other)
        route.findPreparedExecution(other).is(ofOther)
        // the route keeps the execution of the last owner: the first owner prepares it again
        route.findPreparedExecution(one) == null
        one.execution(route).owner.is(one)
    }

    void "a custom route and a forced compatibility are executed by the compatibility execution"() {
        given:
        RouteExecutor executor = routeExecutor()
        RouteInfo<?> custom = Mock(RouteInfo)
        DefaultRouteInfo<?> route = route(ReturnType.of(String))

        expect:
        executor.execution(custom).getClass().simpleName == "Compatibility"

        when:
        executor.forceCompatibility = true

        then:
        executor.execution(route).getClass().simpleName == "Compatibility"
        route.findPreparedExecution(executor) == null
    }

    void "a failed preparation is not kept"() {
        given:
        RouteExecutor executor = routeExecutor()
        RouteInfo<?> failing = Spy(route(ReturnType.of(String)))
        int calls = 0
        failing.getExecutor(_) >> {
            if (calls++ == 0) {
                throw new IllegalStateException("no executor")
            }
            return null
        }

        when:
        executor.execution(failing)

        then:
        thrown(IllegalStateException)
        ((DefaultRouteInfo<?>) failing).findPreparedExecution(executor) == null

        when:
        RouteExecution execution = executor.execution(failing)

        then:
        ((DefaultRouteInfo<?>) failing).findPreparedExecution(executor).is(execution)
    }

    void "concurrent first requests all get an execution of the route"() {
        given:
        RouteExecutor executor = routeExecutor()
        DefaultRouteInfo<?> route = route(ReturnType.of(Mono, Argument.STRING))
        def pool = Executors.newFixedThreadPool(8)
        CountDownLatch start = new CountDownLatch(1)

        when:
        List<Future<RouteExecution>> futures = (1..8).collect {
            pool.submit({ start.await(); executor.execution(route) } as Callable<RouteExecution>)
        }
        start.countDown()
        List<RouteExecution> executions = futures*.get()

        then:
        executions.every { it.getClass().simpleName == "Direct" && it.owner.is(executor) }
        executor.execution(route).is(route.findPreparedExecution(executor))

        cleanup:
        pool.shutdown()
    }
}
