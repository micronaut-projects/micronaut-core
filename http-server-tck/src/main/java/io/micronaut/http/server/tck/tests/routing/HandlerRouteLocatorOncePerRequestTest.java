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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A route locator, synchronous or asynchronous, runs once per request: also when the request is
 * answered with a {@code 405}, whose allowed methods are those of the table of the target.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteLocatorOncePerRequestTest {
    public static final String SPEC_NAME = "HandlerRouteLocatorOncePerRequestTest";
    private static final String REQUEST_ID = "X-Request-Id";
    private static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();

    @Test
    void aSynchronousLocatorRunsOncePerRequest() throws IOException {
        assertLocatedOnce("/once-sync/5/items");
    }

    @Test
    void anAsynchronousLocatorRunsOncePerRequest() throws IOException {
        assertLocatedOnce("/once-async/5/items");
    }

    private static void assertLocatedOnce(String path) throws IOException {
        try (ServerUnderTest server = server()) {
            String ok = UUID.randomUUID().toString();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path).header(REQUEST_ID, ok), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item")
                .build());
            assertEquals(1, calls(ok), "a matched request");

            String notAllowed = UUID.randomUUID().toString();
            AssertionUtils.assertThrows(server, HttpRequest.DELETE(path).header(REQUEST_ID, notAllowed), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
            assertEquals(1, calls(notAllowed), "a request answered with a 405");
        }
    }

    private static int calls(String requestId) {
        AtomicInteger calls = CALLS.get(requestId);
        return calls == null ? 0 : calls.get();
    }

    private static void count(HttpRequest<?> request) {
        CALLS.computeIfAbsent(request.getHeaders().get(REQUEST_ID), id -> new AtomicInteger()).incrementAndGet();
    }

    private static <T> T later(T value) {
        try {
            // after the router asked for the target
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OnceRoutes implements HttpRoutes {
        private final RouteTableFactory tables;
        private final ExecutorService executor;

        OnceRoutes(RouteTableFactory tables, @Named(TaskExecutors.IO) ExecutorService executor) {
            this.tables = tables;
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) ->
                HttpResponse.ok("item").contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.locate("/once-sync/{id}", (request, pathVariables) -> {
                count(request);
                return "order";
            }, order -> items);
            routes.locateAsync("/once-async/{id}", (request, pathVariables) -> {
                count(request);
                return CompletableFuture.supplyAsync(() -> later("order"), executor);
            }, order -> items);
        }
    }
}
