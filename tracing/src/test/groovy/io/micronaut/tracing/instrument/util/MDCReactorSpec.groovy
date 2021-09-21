package io.micronaut.tracing.instrument.util

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

class MDCReactorSpec extends Specification {

    static final Logger LOG = LoggerFactory.getLogger(MDCReactorSpec.class)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'mdc.reactortest.enabled': true
    ])

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void "test MDC propagates"() {
        expect:
            List<Tuple2> result = Flux.range(1, 1000)
                    .flatMap {
                        String tracingId = UUID.randomUUID().toString()
                        HttpRequest<Object> request = HttpRequest.POST("/mdc/enter", new SomeBody()).header("X-TrackingId", tracingId)
                        return Mono.from(client.retrieve(request)).map(response -> {
                            Tuples.of(tracingId, response)
                        })
                    }
                    .collectList()
                    .block()
            for (Tuple2 t : result)
                assert t.getT1() == t.getT2()
    }

    @Introspected
    static class SomeBody {

    }

    @Controller("/mdc")
    @Requires(property = 'mdc.reactortest.enabled')
    static class MDCController {

        @Inject
        @Client("/")
        private HttpClient httpClient
        @Inject
        @Client
        private MDCClient mdcClient

        @Post("/enter")
        @ExecuteOn(TaskExecutors.IO)
        String test(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            LOG.info("test1")
            checkTracing(tracingId)
            return mdcClient.test2(tracingId)
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get("/test2")
        Mono<String> test2(@Header("X-TrackingId") String tracingId) {
            LOG.info("test2")
            checkTracing(tracingId)
            return Mono<String>.fromCallable {
                checkTracing(tracingId)
                mdcClient.test3(tracingId, new SomeBody())
            }.delayElement(Duration.ofMillis(50))
        }

        @Put("/test3")
        Mono<String> test3(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            LOG.info("test3")
            checkTracing(tracingId)
            return Mono.from(
                    httpClient.retrieve(HttpRequest.POST("/mdc/test4", body)
                            .header("X-TrackingId", tracingId), String.class)
            )
        }

        @ExecuteOn(TaskExecutors.IO)
        @Post("/test4")
        String test4(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            LOG.info("test4")
            return httpClient.toBlocking().retrieve(HttpRequest.PATCH("/mdc/test5", body)
                    .header("X-TrackingId", tracingId), String.class)
        }

        @Patch("/test5")
        String test5(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            checkTracing(tracingId)
            LOG.info("test5")
            return MDC.get("trackingId")
        }
    }

    @Client("/mdc")
    @Requires(property = 'mdc.reactortest.enabled')
    static interface MDCClient {

        @Get("/test2")
        String test2(@Header("X-TrackingId") tracingId)

        @Put("/test3")
        String test3(@Header("X-TrackingId") tracingId, @Body SomeBody body)

        @Post("/test4")
        Mono<String> test4(@Header("X-TrackingId") tracingId, @Body SomeBody body)

    }

    @Filter("/**")
    @Requires(property = 'mdc.reactortest.enabled')
    static class TracingHttpServerFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            String trackingId = request.headers.get("X-TrackingId")
            MDC.put("trackingId", trackingId)
            return Mono.from(chain.proceed(request))
        }

    }

    @Filter("/mdc/test**")
    @Requires(property = 'mdc.reactortest.enabled')
    static class TracingHttpClientFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            checkTracing(request)
            return Mono.from(chain.proceed(request))
        }

        @Override
        int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE
        }
    }

    static void checkTracing(MutableHttpRequest<?> request) {
        String trackingId = request.headers.get("X-TrackingId") as String
        checkTracing(trackingId)
    }

    static void checkTracing(String trackingId) {
        String mdcTracingId = MDC.get("trackingId")
        if (trackingId != mdcTracingId) {
            throw new IllegalArgumentException("TrackingIds do not match! Request: $trackingId vs. Context: $mdcTracingId")
        }
    }

}
