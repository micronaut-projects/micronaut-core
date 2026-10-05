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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the router matches a locator route: the error routes of the group of a locator route
 * answer the failure of its locator, the constraints of a located route see the path variables
 * of the prefix, and a request with a custom HTTP method is not located, not even to find the
 * allowed methods: the request is answered as before, with a {@code 405}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteLocatorMatchingTest {
    public static final String SPEC_NAME = "HandlerRouteLocatorMatchingTest";
    private static final AtomicInteger CUSTOM_LOCATED = new AtomicInteger();

    @Test
    void theGroupErrorRouteAnswersAFailedSynchronousLocator() throws IOException {
        assertGroupAnswers("/matching/fails/sync/1/items");
    }

    @Test
    void theGroupErrorRouteAnswersAFailedAsynchronousLocator() throws IOException {
        assertGroupAnswers("/matching/fails/async/1/items");
    }

    private static void assertGroupAnswers(String path) throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("group: no order")
                .build());
        }
    }

    @Test
    void aConstraintOfALocatedRouteSeesThePathVariablesOfThePrefix() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/matching/orders/1/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("items of 1")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/matching/orders/2/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void aRequestWithACustomMethodIsNotLocated() throws IOException {
        try (ServerUnderTest server = server()) {
            int located = CUSTOM_LOCATED.get();
            AssertionUtils.assertThrows(server, HttpRequest.create(HttpMethod.CUSTOM, "/matching/custom/1/items", "PROPFIND"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.METHOD_NOT_ALLOWED)
                    .build());
            assertEquals(located, CUSTOM_LOCATED.get(), "the locator is not called for a custom method");
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<String> text(HttpStatus status, String body) {
        return HttpResponse.<String>status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static final class NoOrder extends RuntimeException {
        NoOrder() {
            super("no order", null, false, false);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MatchingRoutes implements HttpRoutes {
        private final ExecutorService executor;

        MatchingRoutes(@Named(TaskExecutors.IO) ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatedRoutes<?> items = TckLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) ->
                text(HttpStatus.OK, "items")));
            routes.path("/matching/fails", group -> {
                group.locate("/sync/{id}", (request, pathVariables) -> {
                    throw new NoOrder();
                }, order -> items);
                group.locateAsync("/async/{id}", (request, pathVariables) ->
                    CompletableFuture.supplyAsync(() -> {
                        // after the router asked for the target
                        sleep();
                        throw new NoOrder();
                    }, executor), order -> items);
                group.error(NoOrder.class, (request, error) -> text(HttpStatus.CONFLICT, "group: " + error.getMessage()));
            });
            LocatedRoutes<?> constrained = TckLocatedRoutes.of(located -> located.GET("/items")
                .constrain(pathVariables -> "1".equals(pathVariables.get("id", String.class)))
                .handle((request, pathVariables) -> text(HttpStatus.OK, "items of " + pathVariables.get("id", String.class))));
            routes.locate("/matching/orders/{id}", (request, pathVariables) -> "order", order -> constrained);
            routes.locate("/matching/custom/{id}", (request, pathVariables) -> {
                CUSTOM_LOCATED.incrementAndGet();
                return "order";
            }, order -> items);
        }
    }
}
