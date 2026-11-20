/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.tck.tests.limit;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.limit.RequestLimit;
import io.micronaut.http.server.limit.RequestLimiter;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The request limiters: a rate limiter answers 429 with a {@code Retry-After}, a concurrency
 * limiter 503, and a permit is given back when the response is done, also after an error.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RequestLimitTest {
    public static final String SPEC_NAME = "RequestLimitTest";

    private static final Map<String, Object> CONFIG = Map.of(
        "micronaut.server.limits.hourly.type", "rate",
        "micronaut.server.limits.hourly.permits", 1,
        "micronaut.server.limits.hourly.period", "1h",
        "micronaut.server.limits.hourly.patterns", List.of("/limit/rate/**"),
        "micronaut.server.limits.single.type", "concurrency",
        "micronaut.server.limits.single.max-concurrent", 1,
        "micronaut.server.limits.single.retry-after", "2s"
    );

    @Test
    void rateLimitAnswers429WithRetryAfter() throws Exception {
        try (ServerUnderTest server = server()) {
            Assertions.assertEquals(HttpStatus.OK, server.exchange(HttpRequest.GET("/limit/rate/one"), String.class).getStatus());
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> server.exchange(HttpRequest.GET("/limit/rate/two"), String.class));
            Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
            long retryAfter = Long.parseLong(e.getResponse().getHeaders().get(HttpHeaders.RETRY_AFTER));
            Assertions.assertTrue(retryAfter > 3000 && retryAfter <= 3600, "Retry-After: " + retryAfter);
            // a path the patterns do not match is not limited
            Assertions.assertEquals(HttpStatus.OK, server.exchange(HttpRequest.GET("/limit/free"), String.class).getStatus());
            Assertions.assertEquals(HttpStatus.OK, server.exchange(HttpRequest.GET("/limit/free"), String.class).getStatus());
        }
    }

    @Test
    void concurrencyLimitOfAnAnnotatedRouteAnswers503() throws Exception {
        try (ServerUnderTest server = server()) {
            assertConcurrencyLimited(server, "/limit/slow");
        }
    }

    @Test
    void concurrencyLimitOfAFunctionalRouteAnswers503() throws Exception {
        try (ServerUnderTest server = server()) {
            assertConcurrencyLimited(server, "/limit/functional");
        }
    }

    @Test
    void aFailedRouteGivesItsPermitBack() throws Exception {
        try (ServerUnderTest server = server()) {
            RequestLimiter limiter = server.getApplicationContext().getBean(RequestLimiter.class, Qualifiers.byName("single"));
            for (int i = 0; i < 5; i++) {
                HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                    () -> server.exchange(HttpRequest.GET("/limit/fail"), String.class));
                Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
                awaitIdle(limiter);
            }
            Assertions.assertEquals(5, limiter.getAcquiredCount());
            Assertions.assertEquals(0, limiter.getRejectedCount());
        }
    }

    private static void assertConcurrencyLimited(ServerUnderTest server, String path) throws Exception {
        Gate gate = server.getApplicationContext().getBean(Gate.class);
        RequestLimiter limiter = server.getApplicationContext().getBean(RequestLimiter.class, Qualifiers.byName("single"));
        CompletableFuture<String> pending = new CompletableFuture<>();
        gate.pending.set(pending);
        // sent from the test thread without blocking it: the leak detector of the TCK only allows
        // the resources of the threads of the test
        try (HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL().orElseThrow())) {
            CompletableFuture<HttpResponse<String>> first = Mono.from(client.exchange(HttpRequest.GET(path), String.class)).toFuture();
            awaitInFlight(limiter, first);

            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> server.exchange(HttpRequest.GET(path), String.class));
            Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            Assertions.assertEquals("2", e.getResponse().getHeaders().get(HttpHeaders.RETRY_AFTER));

            pending.complete("done");
            Assertions.assertEquals("done", first.get(30, TimeUnit.SECONDS).body());
        }
        awaitIdle(limiter);
        gate.pending.set(CompletableFuture.completedFuture("again"));
        Assertions.assertEquals("again", server.exchange(HttpRequest.GET(path), String.class).body());
        awaitIdle(limiter);
    }

    private static void awaitInFlight(RequestLimiter limiter, CompletableFuture<HttpResponse<String>> first) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (limiter.getInFlight() == 0 && System.nanoTime() < deadline) {
            if (first.isDone()) {
                Assertions.fail("The first request ended before it was limited: " + first.handle((r, e) -> r != null ? r.getStatus() + " " + r.body() : String.valueOf(e)).get());
            }
            Thread.sleep(5);
        }
        Assertions.assertEquals(1, limiter.getInFlight());
    }

    /**
     * The permit is given back once the response is written, which may be just after the client
     * read it.
     */
    private static void awaitIdle(RequestLimiter limiter) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (limiter.getInFlight() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        Assertions.assertEquals(0, limiter.getInFlight(), "A permit leaked");
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, CONFIG);
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class Gate {
        final AtomicReference<CompletableFuture<String>> pending = new AtomicReference<>(CompletableFuture.completedFuture("open"));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/limit")
    static class LimitController {
        private final Gate gate;

        LimitController(Gate gate) {
            this.gate = gate;
        }

        @Get("/rate/{name}")
        String rate(String name) {
            return name;
        }

        @Get("/free")
        String free() {
            return "free";
        }

        @RequestLimit("single")
        @Get("/slow")
        CompletableFuture<String> slow() {
            return gate.pending.get();
        }

        @RequestLimit("single")
        @Get("/fail")
        String fail() {
            throw new IllegalStateException("failed on purpose");
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class LimitRoutes implements HttpRoutes {
        private final Gate gate;

        LimitRoutes(Gate gate) {
            this.gate = gate;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/limit/functional")
                .attribute(RequestLimit.ATTRIBUTE, "single")
                .handleAsync((request, pathVariables) -> gate.pending.get().thenApply(HttpResponse::ok));
        }
    }
}
