package io.micronaut.http.server.netty.filters;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A continuation that proceeds twice must not re-enter the filter chain past an asynchronous
 * filter that is still in flight.
 */
@MicronautTest
@Property(name = "spec.name", value = "FilterContinuationProceedOnceTest")
class FilterContinuationProceedOnceTest {
    static final AtomicInteger ROUTE_CALLS = new AtomicInteger();

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void singleProceedIsDenied() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/proceed-once/s/secret")));
        Assertions.assertEquals(401, e.code());
    }

    @Test
    void hedgedProceedIsStillDenied() {
        int before = ROUTE_CALLS.get();
        Assertions.assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/proceed-once/p/secret")));
        Assertions.assertEquals(before, ROUTE_CALLS.get(), "the route ran without the gate");
    }

    @ServerFilter("/proceed-once/**")
    @Requires(property = "spec.name", value = "FilterContinuationProceedOnceTest")
    @Order(0)
    static class Gate {
        @RequestFilter
        Publisher<MutableHttpResponse<?>> gate(HttpRequest<?> request) {
            if ("ok".equals(request.getHeaders().get("X-Token"))) {
                return Mono.empty();
            }
            return Mono.delay(Duration.ofMillis(150)).map(i -> HttpResponse.status(HttpStatus.UNAUTHORIZED).body("denied"));
        }
    }

    @ServerFilter("/proceed-once/p/**")
    @Requires(property = "spec.name", value = "FilterContinuationProceedOnceTest")
    @Order(-100)
    static class Hedge {
        @RequestFilter
        Publisher<MutableHttpResponse<?>> hedge(FilterContinuation<Publisher<MutableHttpResponse<?>>> c) {
            return Flux.merge(c.proceed(), c.proceed()).next();
        }
    }

    @Controller("/proceed-once")
    @Requires(property = "spec.name", value = "FilterContinuationProceedOnceTest")
    static class Route {
        @Get("/{kind}/secret")
        String secret(String kind) {
            ROUTE_CALLS.incrementAndGet();
            return "secret";
        }
    }
}
