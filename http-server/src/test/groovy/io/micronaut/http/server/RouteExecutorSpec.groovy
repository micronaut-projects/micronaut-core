package io.micronaut.http.server

import io.micronaut.context.BeanContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor
import io.micronaut.scheduling.executor.ExecutorSelector
import io.micronaut.web.router.RouteInfo
import io.micronaut.web.router.Router
import io.micronaut.http.server.binding.RequestArgumentSatisfier
import reactor.core.publisher.Flux
import spock.lang.Specification

class RouteExecutorSpec extends Specification {

    void "test duplicate content type header in processPublisherBody"() {
        given:
        Router router = Mock(Router)
        BeanContext beanContext = Mock(BeanContext)
        RequestArgumentSatisfier requestArgumentSatisfier = Mock(RequestArgumentSatisfier)
        HttpServerConfiguration serverConfiguration = new HttpServerConfiguration()
        ErrorResponseProcessor errorResponseProcessor = Mock(ErrorResponseProcessor)
        ExecutorSelector executorSelector = Mock(ExecutorSelector)

        RouteExecutor routeExecutor = new RouteExecutor(
                router,
                beanContext,
                requestArgumentSatisfier,
                serverConfiguration,
                errorResponseProcessor,
                executorSelector
        )

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
        ).block()

        then:
        result.getHeaders().getAll(HttpHeaders.CONTENT_TYPE) == ["application/json"]
    }
}
