package io.micronaut.http.server.netty.filters;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A client filter can proceed again to retry the request.
 */
@MicronautTest
@Property(name = "spec.name", value = "ClientFilterRetryTest")
class ClientFilterRetryTest {
    static final AtomicInteger CALLS = new AtomicInteger();

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void clientFilterCanRetry() {
        CALLS.set(0);
        String body = client.toBlocking().retrieve(HttpRequest.GET("/client-retry"));
        Assertions.assertEquals("ok", body);
        Assertions.assertEquals(2, CALLS.get());
    }

    @ClientFilter("/client-retry/**")
    @Requires(property = "spec.name", value = "ClientFilterRetryTest")
    static class Retry {
        @RequestFilter
        Publisher<HttpResponse<?>> retry(FilterContinuation<Publisher<HttpResponse<?>>> continuation) {
            return Mono.from(continuation.proceed())
                .flatMap(response -> response.status() == HttpStatus.UNAUTHORIZED ? Mono.from(continuation.proceed()) : Mono.just(response))
                .onErrorResume(HttpClientResponseException.class,
                    e -> e.getStatus() == HttpStatus.UNAUTHORIZED ? Mono.from(continuation.proceed()) : Mono.error(e));
        }
    }

    @Controller("/client-retry")
    @Requires(property = "spec.name", value = "ClientFilterRetryTest")
    static class Route {
        @Get
        HttpResponse<String> get() {
            if (CALLS.incrementAndGet() == 1) {
                return HttpResponse.status(HttpStatus.UNAUTHORIZED);
            }
            return HttpResponse.ok("ok");
        }
    }
}
