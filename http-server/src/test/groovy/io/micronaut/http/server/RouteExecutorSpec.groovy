package io.micronaut.http.server

import io.micronaut.context.BeanContext
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.execution.ImperativeExecutionFlow
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.context.ServerHttpRequestContext
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor
import io.micronaut.scheduling.executor.ExecutorSelector
import io.micronaut.web.router.RouteInfo
import io.micronaut.web.router.Router
import io.micronaut.http.server.binding.RequestArgumentSatisfier
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RouteExecutorSpec extends Specification {

    private RouteExecutor routeExecutor() {
        Router router = Mock(Router)
        BeanContext beanContext = Mock(BeanContext)
        RequestArgumentSatisfier requestArgumentSatisfier = Mock(RequestArgumentSatisfier)
        HttpServerConfiguration serverConfiguration = new HttpServerConfiguration()
        ErrorResponseProcessor errorResponseProcessor = Mock(ErrorResponseProcessor)
        errorResponseProcessor.processResponse(_, _) >> { errorContext, response -> response }
        ExecutorSelector executorSelector = Mock(ExecutorSelector)

        return new RouteExecutor(
                router,
                beanContext,
                requestArgumentSatisfier,
                serverConfiguration,
                errorResponseProcessor,
                executorSelector
        )
    }

    /**
     * The route info of a controller method returning {@code Mono<bodyType>}.
     */
    private RouteInfo<?> monoRouteInfo(Class<?> bodyType) {
        RouteInfo<?> routeInfo = Mock(RouteInfo)
        routeInfo.isReactive() >> true
        routeInfo.isSingleResult() >> true
        routeInfo.getReturnType() >> ReturnType.of(Mono, Argument.of(bodyType))
        routeInfo.findStatus(_) >> { HttpStatus defaultStatus -> defaultStatus ?: HttpStatus.OK }
        return routeInfo
    }

    void "test duplicate content type header in processPublisherBody"() {
        given:
        RouteExecutor routeExecutor = routeExecutor()

        HttpRequest<?> request = Mock(HttpRequest)
        MutableHttpResponse<?> response = HttpResponse.ok().contentType(MediaType.APPLICATION_JSON_TYPE)
        RouteInfo<?> routeInfo = Mock(RouteInfo)
        routeInfo.isReactive() >> true

        // A publisher that is NOT a single publisher to trigger the bug
        Flux<String> bodyPublisher = Flux.just("item1", "item2")

        when:
        // Use Groovy's private method access
        MutableHttpResponse<?> result = routeExecutor.processPublisherBody(
                PropagatedContext.getOrEmpty(),
                request,
                response,
                false, // isSinglePublisher = false
                bodyPublisher,
                routeInfo
        ).tryCompleteValue()

        then:
        result.getHeaders().getAll(HttpHeaders.CONTENT_TYPE) == ["application/json"]
    }

    void "a route returning a synchronous Mono yields an imperative flow"() {
        given:
        RouteExecutor routeExecutor = routeExecutor()
        HttpRequest<?> request = HttpRequest.GET("/")

        when:
        ExecutionFlow<HttpResponse<?>> flow = routeExecutor.createResponseForBody(PropagatedContext.getOrEmpty(), request, body, monoRouteInfo(String), null)

        then:
        flow instanceof ImperativeExecutionFlow
        flow.tryCompleteValue().status() == HttpStatus.OK
        flow.tryCompleteValue().body() == "foo"

        where:
        body << [
                Mono.just("foo"),
                Mono.just("f").map { it + "oo" },
                Mono.fromCallable { "foo" },
                Mono.just(HttpResponse.ok("foo")),
                Mono.just(Optional.of("foo")),
        ]
    }

    void "an empty Mono yields an imperative not found response"() {
        given:
        RouteExecutor routeExecutor = routeExecutor()
        HttpRequest<?> request = HttpRequest.GET("/")

        when:
        ExecutionFlow<HttpResponse<?>> flow = routeExecutor.createResponseForBody(PropagatedContext.getOrEmpty(), request, body, monoRouteInfo(String), null)

        then:
        flow instanceof ImperativeExecutionFlow
        flow.tryCompleteValue().status() == HttpStatus.NOT_FOUND

        where:
        body << [
                Mono.empty(),
                Mono.just(Optional.empty()),
                Mono.fromCallable { null },
        ]
    }

    void "a route returning an asynchronous Mono completes the flow later"() {
        given:
        RouteExecutor routeExecutor = routeExecutor()
        HttpRequest<?> request = HttpRequest.GET("/")
        CompletableFuture<String> future = new CompletableFuture<>()

        when:
        ExecutionFlow<HttpResponse<?>> flow = routeExecutor.createResponseForBody(PropagatedContext.getOrEmpty(), request, Mono.fromFuture(future), monoRouteInfo(String), null)
        CompletableFuture<HttpResponse<?>> result = new CompletableFuture<>()
        flow.onComplete { r, e -> e == null ? result.complete(r) : result.completeExceptionally(e) }

        then:
        !(flow instanceof ImperativeExecutionFlow)
        !result.isDone()

        when:
        future.complete("foo")

        then:
        result.get(5, TimeUnit.SECONDS).body() == "foo"
    }

    void "the request is visible inside a Mono route body"() {
        given:
        RouteExecutor routeExecutor = routeExecutor()
        HttpRequest<?> request = HttpRequest.GET("/")
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(request))
        def seen = []
        Mono<String> body = Mono.fromCallable {
            seen.add(ServerRequestContext.currentRequest().orElse(null))
            "foo"
        }.flatMap { value ->
            Mono.deferContextual { ctx ->
                seen.add(ctx.get(ServerRequestContext.KEY))
                seen.add(ServerRequestContext.currentRequest(ctx).orElse(null))
                Mono.just(value)
            }
        }

        when:
        ExecutionFlow<HttpResponse<?>> flow = routeExecutor.createResponseForBody(propagatedContext, request, body, monoRouteInfo(String), null)

        then:
        flow instanceof ImperativeExecutionFlow
        flow.tryCompleteValue().body() == "foo"
        seen == [request, request, request]
    }

    void "the request is visible in the steps after a Mono completing on another thread"() {
        given:
        RouteExecutor routeExecutor = routeExecutor()
        HttpRequest<?> request = HttpRequest.GET("/")
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(request))
        CompletableFuture<String> future = new CompletableFuture<>()
        Mono<String> body = Mono.fromFuture(future)

        when:
        ExecutionFlow<HttpResponse<?>> flow = routeExecutor.createResponseForBody(propagatedContext, request, body, monoRouteInfo(String), null)
        CompletableFuture<HttpRequest<?>> seen = new CompletableFuture<>()
        flow.onComplete { r, e -> seen.complete(ServerRequestContext.currentRequest().orElse(null)) }
        // completes on another thread, outside the propagated context
        Thread.start { future.complete("foo") }.join()

        then:
        !(flow instanceof ImperativeExecutionFlow)
        seen.get(5, TimeUnit.SECONDS) == request
    }
}
