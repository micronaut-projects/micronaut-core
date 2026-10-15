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
package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request abandoned while its asynchronous locator is pending stops waiting for the stage,
 * without cancelling it: a stage the locator shares with other requests locates their target.
 */
class AsyncLocatorAbandonmentTest {

    @Test
    void anAbandonedRequestStopsWaitingWithoutCancellingTheStage() {
        CompletableFuture<Object> shared = new CompletableFuture<>();
        Router router = router(shared);

        HttpRequest<?> abandoned = HttpRequest.GET("/orders/1/items");
        CompletionStage<?> pending = pending(() -> router.findClosest(abandoned));
        HttpRequest<?> waiting = HttpRequest.GET("/orders/1/items");
        CompletionStage<?> waitingPending = pending(() -> router.findClosest(waiting));

        RouteLocator.abandonPendingLocations(abandoned);
        assertTrue(pending.toCompletableFuture().isDone(), "the abandoned request stops waiting");
        assertFalse(shared.isDone(), "the stage is not cancelled");
        RuntimeException error = assertThrows(RuntimeException.class, () -> router.findClosest(abandoned));
        assertTrue(RouteLocator.isAbandonment(error), error::toString);
        // the error routes of the locator do not answer the abandonment
        assertNull(GroupErrorRoutes.findErrorRoute(abandoned, null, error));

        shared.complete("order");
        assertTrue(waitingPending.toCompletableFuture().isDone());
        assertNotNull(router.findClosest(waiting));
        // the outcome of the abandoned request does not change when the stage completes
        assertTrue(RouteLocator.isAbandonment(assertThrows(RuntimeException.class, () -> router.findClosest(abandoned))));
    }

    @Test
    void whenLocatedCompletesWithTheLocatorsOfTheRequest() {
        CompletableFuture<Object> order = new CompletableFuture<>();
        Router router = router(order);
        HttpRequest<?> request = HttpRequest.GET("/orders/1/items");
        assertNull(RouteLocator.whenLocated(request));

        // finding the routes starts the locator, which a filter may wait for before the request is matched
        assertTrue(router.findAny(request).isEmpty());
        CompletionStage<?> located = RouteLocator.whenLocated(request);
        assertNotNull(located);
        assertFalse(located.toCompletableFuture().isDone());

        order.complete("order");
        assertNotNull(located.toCompletableFuture().join(), "completes with a value");
        assertNull(RouteLocator.whenLocated(request));
        assertFalse(router.findAny(request).isEmpty());
    }

    private static Router router(CompletableFuture<Object> stage) {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        return router(routes -> routes.group(group -> {
            group.error(RuntimeException.class, (request, error) -> HttpResponse.serverError());
            group.locateAsync("/orders/{id}", (request, pathVariables) -> stage, target -> items);
        }));
    }

    private static CompletionStage<?> pending(Executable match) {
        RuntimeException error = assertThrows(RuntimeException.class, match);
        CompletionStage<?> stage = RouteLocator.pendingLocation(error);
        assertNotNull(stage, "an asynchronous locator waits");
        return stage;
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
