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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.exceptions.HttpStatusException;
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
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the router matches a locator route: the error routes of the group of a locator route
 * answer the failure of its locator, the constraints of a located route see the path variables
 * of the prefix, and a request with a custom HTTP method is located like any other: it is
 * answered by the located route of its method, or of any method, and otherwise with a
 * {@code 405} that allows the methods of the located routes. The locator runs once per request.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteLocatorMatchingTest {
    public static final String SPEC_NAME = "HandlerRouteLocatorMatchingTest";
    private static final AtomicInteger CUSTOM_LOCATED = new AtomicInteger();
    private static final AtomicInteger ASYNC_CUSTOM_LOCATED = new AtomicInteger();
    private static final AtomicInteger THROWING_LOCATED = new AtomicInteger();

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
    void aRequestWithACustomMethodTheLocatedRoutesDoNotHaveIsAMethodNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            int located = CUSTOM_LOCATED.get();
            AssertionUtils.assertThrows(server, HttpRequest.create(HttpMethod.CUSTOM, "/matching/custom/1/items", "PROPFIND"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.METHOD_NOT_ALLOWED)
                    .assertResponse(response -> assertEquals(Set.of("GET", "HEAD", "POST"), allowed(response)))
                    .build());
            assertEquals(located + 1, CUSTOM_LOCATED.get(), "the locator is called once");
        }
    }

    @Test
    void aRequestWithACustomMethodIsAnsweredByTheLocatedRouteOfItsMethod() throws IOException {
        assertCustomMethodLocated("/matching/custom/42/props", CUSTOM_LOCATED);
    }

    @Test
    void aRequestWithACustomMethodIsAnsweredByTheLocatedRouteOfItsMethodOfAnAsynchronousLocator() throws IOException {
        assertCustomMethodLocated("/matching/async-custom/42/props", ASYNC_CUSTOM_LOCATED);
    }

    private static void assertCustomMethodLocated(String path, AtomicInteger calls) throws IOException {
        try (ServerUnderTest server = server()) {
            int located = calls.get();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.create(HttpMethod.CUSTOM, path, "PROPFIND"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("PROPFIND props of 42")
                    .build());
            assertEquals(located + 1, calls.get(), "the locator is called once");
        }
    }

    @Test
    void theLocatedRoutesOfAStandardAndACustomMethodAtTheSamePathBothAnswer() throws IOException {
        try (ServerUnderTest server = server()) {
            int located = CUSTOM_LOCATED.get();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/matching/custom/7/props"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("GET props of 7")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.create(HttpMethod.CUSTOM, "/matching/custom/7/props", "PROPFIND"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("PROPFIND props of 7")
                    .build());
            assertEquals(located + 2, CUSTOM_LOCATED.get(), "the locator is called once per request");
        }
    }

    @Test
    void aRequestWithACustomMethodIsAnsweredByALocatedRouteOfAnyMethod() throws IOException {
        try (ServerUnderTest server = server()) {
            // REPORT is a custom method, of the routes of any method, not a standard one like QUERY
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.create(HttpMethod.CUSTOM, "/matching/custom/3/all", "REPORT"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("REPORT all of 3")
                    .build());
        }
    }

    @Test
    void aConstraintOfALocatedRouteSeesTheDecodedPathVariablesOfThePrefix() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/matching/shops/north%20shop/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("items of north shop")
                .build());
        }
    }

    @Test
    void theGroupErrorRouteAnswersAnAsynchronousLocatorThatThrowsOnce() throws IOException {
        try (ServerUnderTest server = server()) {
            int located = THROWING_LOCATED.get();
            AssertionUtils.assertThrows(server, HttpRequest.GET("/matching/fails/async-throws/1/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("group: no order")
                .build());
            assertEquals(located + 1, THROWING_LOCATED.get(), "the locator is called once");
        }
    }

    @Test
    void theGroupErrorRouteAnswersTheFailureToSelectTheRoutesOfASynchronousLocator() throws IOException {
        assertGroupAnswers("/matching/fails/sync-routes/1/items");
    }

    @Test
    void theGroupErrorRouteAnswersTheFailureToSelectTheRoutesOfAnAsynchronousLocator() throws IOException {
        assertGroupAnswers("/matching/fails/async-routes/1/items");
    }

    @Test
    void theGroupErrorRouteAnswersTheFailureToBuildTheTableOfASynchronousLocator() throws IOException {
        assertGroupAnswers("/matching/fails/sync-table/1/items");
    }

    @Test
    void theGroupErrorRouteAnswersTheFailureToBuildTheTableOfAnAsynchronousLocator() throws IOException {
        assertGroupAnswers("/matching/fails/async-table/1/items");
    }

    @Test
    void theGroupStatusRouteAnswersTheStatusOfASynchronousLocator() throws IOException {
        assertGroupStatusAnswers("/matching/missing/sync/1/items");
    }

    @Test
    void theGroupStatusRouteAnswersTheStatusOfAnAsynchronousLocator() throws IOException {
        assertGroupStatusAnswers("/matching/missing/async/1/items");
    }

    private static void assertGroupStatusAnswers(String path) throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                .status(HttpStatus.GONE)
                .body("group: not found")
                .build());
        }
    }

    private static Set<String> allowed(HttpResponse<?> response) {
        return response.getHeaders().getAll(HttpHeaders.ALLOW).stream()
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .collect(Collectors.toSet());
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<String> text(HttpStatus status, String body) {
        return HttpResponse.<String>status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static HttpResponse<String> methodOf(HttpRequest<?> request, String resource, String id) {
        return text(HttpStatus.OK, request.getMethodName() + " " + resource + " of " + id);
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
                group.locateAsync("/async-throws/{id}", (request, pathVariables) -> {
                    THROWING_LOCATED.incrementAndGet();
                    throw new NoOrder();
                }, order -> items);
                group.locate("/sync-routes/{id}", (request, pathVariables) -> "order", order -> {
                    throw new NoOrder();
                });
                group.locateAsync("/async-routes/{id}", (request, pathVariables) -> CompletableFuture.completedFuture("order"), order -> {
                    throw new NoOrder();
                });
                LocatedRoutes<?> broken = TckLocatedRoutes.of(located -> {
                    throw new NoOrder();
                });
                group.locate("/sync-table/{id}", (request, pathVariables) -> "order", order -> broken);
                group.locateAsync("/async-table/{id}", (request, pathVariables) -> CompletableFuture.completedFuture("order"), order -> broken);
                group.error(NoOrder.class, (request, error) -> text(HttpStatus.CONFLICT, "group: " + error.getMessage()));
            });
            routes.path("/matching/missing", group -> {
                group.locate("/sync/{id}", (request, pathVariables) -> {
                    throw new HttpStatusException(HttpStatus.NOT_FOUND, "no order");
                }, order -> items);
                group.locateAsync("/async/{id}", (request, pathVariables) ->
                    CompletableFuture.failedFuture(new HttpStatusException(HttpStatus.NOT_FOUND, "no order")), order -> items);
                group.status(HttpStatus.NOT_FOUND, request -> text(HttpStatus.GONE, "group: not found"));
            });
            LocatedRoutes<?> shopItems = TckLocatedRoutes.of(located -> located.GET("/items")
                .constrain(pathVariables -> "north shop".equals(pathVariables.get("id", String.class)))
                .handle((request, pathVariables) -> text(HttpStatus.OK, "items of " + pathVariables.get("id", String.class))));
            routes.locate("/matching/shops/{id}", (request, pathVariables) -> "shop", shop -> shopItems);
            LocatedRoutes<?> constrained = TckLocatedRoutes.of(located -> located.GET("/items")
                .constrain(pathVariables -> "1".equals(pathVariables.get("id", String.class)))
                .handle((request, pathVariables) -> text(HttpStatus.OK, "items of " + pathVariables.get("id", String.class))));
            routes.locate("/matching/orders/{id}", (request, pathVariables) -> "order", order -> constrained);
            LocatedRoutes<?> custom = TckLocatedRoutes.of(located -> {
                located.GET("/items", (request, pathVariables) -> text(HttpStatus.OK, "items"));
                located.POST("/items", (request, pathVariables) -> text(HttpStatus.OK, "items"));
                located.GET("/props", (request, pathVariables) -> methodOf(request, "props", pathVariables.get("id", String.class)));
                located.route("PROPFIND", "/props").handle((request, pathVariables) ->
                    methodOf(request, "props", pathVariables.get("id", String.class)));
                located.any("/all").handle((request, pathVariables) -> methodOf(request, "all", pathVariables.get("id", String.class)));
            });
            routes.locate("/matching/custom/{id}", (request, pathVariables) -> {
                CUSTOM_LOCATED.incrementAndGet();
                return "order";
            }, order -> custom);
            routes.locateAsync("/matching/async-custom/{id}", (request, pathVariables) -> {
                ASYNC_CUSTOM_LOCATED.incrementAndGet();
                return CompletableFuture.supplyAsync(() -> {
                    sleep();
                    return "order";
                }, executor);
            }, order -> custom);
        }
    }
}
