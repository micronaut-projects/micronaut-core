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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Asynchronous locators, {@link HttpRouteBuilder#locateAsync}: the rest of the path is matched
 * with the routes of the table of the target when the stage of the locator completes.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteAsyncLocatorTest {
    public static final String SPEC_NAME = "HandlerRouteAsyncLocatorTest";

    @Test
    void theRouteOfTheTableOfTheLocatedTargetAnswers() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/async-orders/5/items/3"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("order 5 item 3")
                .build());
        }
    }

    @Test
    void aStageThatCompletesWithNullIsNotFoundAndAnotherMethodIsNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/async-orders/0/items/3"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/async-orders/5/items/3"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void aFailedStageAndALocatorThatThrowsAreAnsweredByTheErrorRoutes() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/async-orders/13/items/3", "/async-orders/14/items/3"}) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.CONFLICT)
                    .body("no such order")
                    .build());
            }
        }
    }

    @Test
    void aLocatedTableLocatesAgainAsynchronously() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/async-tree/r/sub/1/sub/2/leaf"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("leaf of node 2, root r")
                .build());
        }
    }

    @Test
    void theLocatorAndTheLocatedRouteSeeThePropagatedContextOfThePreMatchingFilters() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/async-context/1/show"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("locator=trace-1 handler=trace-1")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(HttpStatus status, String body) {
        return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static String trace() {
        return PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none");
    }

    record Trace(String id) implements PropagatedContextElement {
    }

    record Order(long id) {
    }

    static final class NoSuchOrder extends RuntimeException {
        NoSuchOrder() {
            super("no such order");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AsyncLocatorRoutes implements HttpRoutes {
        private final RouteTableFactory tables;
        private final Executor executor;

        AsyncLocatorRoutes(RouteTableFactory tables, @Named(TaskExecutors.IO) ExecutorService executor) {
            this.tables = tables;
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items/{item}", (request, pathVariables) ->
                text(HttpStatus.OK, "order " + pathVariables.locatedTarget(Order.class).id() + " item " + pathVariables.getInt("item"))));
            routes.locateAsync("/async-orders/{id}", (request, pathVariables) -> {
                long id = pathVariables.getLong("id");
                if (id == 14) {
                    throw new NoSuchOrder();
                }
                return CompletableFuture.supplyAsync(() -> {
                    if (id == 13) {
                        throw new NoSuchOrder();
                    }
                    return id == 0 ? null : new Order(id);
                }, executor);
            }, order -> items);
            routes.error(NoSuchOrder.class, (request, error) -> text(HttpStatus.CONFLICT, error.getMessage()));

            RouteTable[] tree = new RouteTable[1];
            tree[0] = tables.buildLocatedHttpRoutes(node -> {
                node.GET("/leaf", (request, pathVariables) ->
                    text(HttpStatus.OK, "leaf of " + pathVariables.locatedTarget() + ", root " + pathVariables.getString("root")));
                node.locateAsync("/sub/{n}", (request, pathVariables) ->
                    CompletableFuture.supplyAsync(() -> "node " + pathVariables.getInt("n"), executor), target -> tree[0]);
            });
            routes.locateAsync("/async-tree/{root}", (request, pathVariables) ->
                CompletableFuture.supplyAsync(() -> "root", executor), target -> tree[0]);

            RouteTable shows = tables.buildLocatedHttpRoutes(located -> located.GET("/show", (request, pathVariables) ->
                text(HttpStatus.OK, "locator=" + pathVariables.locatedTarget() + " handler=" + trace())));
            routes.locateAsync("/async-context/{id}", (request, pathVariables) -> {
                String locatorTrace = trace();
                return CompletableFuture.supplyAsync(() -> locatorTrace, executor);
            }, target -> shows);
            routes.filter("/async-context/**").preMatching().before((request, propagatedContext) -> {
                propagatedContext.add(new Trace("trace-" + request.getPath().split("/")[2]));
                return null;
            });
        }
    }
}
