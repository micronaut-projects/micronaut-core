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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedHttpRouteGroup;
import io.micronaut.web.router.builder.LocatedHttpRouteSpec;
import io.micronaut.web.router.builder.LocatedRoutes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The groups of located routes are groups of the routes of the same targets: their routes and
 * locators receive the target, their error and status routes are local to their routes, and the
 * declarations located routes cannot have fail when they are declared.
 */
class LocatedRouteGroupTest {

    @Test
    void theRoutesOfAGroupOfLocatedRoutesReceiveTheTarget() {
        LocatedRoutes<Order> orders = TestLocatedRoutes.of(Order.class, order -> {
            order.group(items -> items.GET("/items/{item}").handle((request, pathVariables, current) ->
                HttpResponse.ok(current.id() + " " + pathVariables.getInt("item"))));
            order.path("/lines", lines -> {
                lines.GET("/{line}").handle((request, pathVariables, current) -> HttpResponse.ok("line " + pathVariables.getInt("line") + " of " + current.id()));
                lines.path("/notes", notes -> notes.GET("/").handle((request, pathVariables, current) -> HttpResponse.ok("notes of " + current.id())));
                lines.POST("/").body(String.class).handle((request, pathVariables, current, body) -> HttpResponse.ok(body + " to " + current.id()));
            });
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> new Order(pathVariables.getLong("id")), orders));

        assertEquals("5 3", invoke(router, HttpRequest.GET("/orders/5/items/3")).body());
        assertEquals("line 2 of 5", invoke(router, HttpRequest.GET("/orders/5/lines/2")).body());
        assertEquals("notes of 5", invoke(router, HttpRequest.GET("/orders/5/lines/notes")).body());
        assertEquals("pen to 5", invoke(router, HttpRequest.POST("/orders/5/lines", ""), "pen").body());
    }

    @Test
    void theLocatorsOfAGroupOfLocatedRoutesReceiveTheTarget() {
        LocatedRoutes<Line> lines = TestLocatedRoutes.of(Line.class, line -> line.GET("/name").handle((request, pathVariables, current) ->
            HttpResponse.ok(current.order().id() + "/" + current.number())));
        LocatedRoutes<Order> orders = TestLocatedRoutes.of(Order.class, order -> order.path("/lines", group -> {
            group.locate("/{line}", (request, pathVariables, current) -> new Line(current, pathVariables.getInt("line")), lines);
            group.locateAsync("/async/{line}", (request, pathVariables, current) ->
                CompletableFuture.completedFuture(new Line(current, pathVariables.getInt("line"))), lines);
        }));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> new Order(pathVariables.getLong("id")), orders));

        assertEquals("5/2", invoke(router, HttpRequest.GET("/orders/5/lines/2/name")).body());
        assertEquals("5/3", invoke(router, HttpRequest.GET("/orders/5/lines/async/3/name")).body());
    }

    @Test
    void aGroupOfLocatedRoutesHasTheTargetTypeOfTheRoutes() {
        AtomicReference<LocatedHttpRouteGroup<Order>> kept = new AtomicReference<>();
        LocatedRoutes<Order> orders = TestLocatedRoutes.of(Order.class, order -> order.group(group -> {
            kept.set(group);
            group.GET("/id").handle((request, pathVariables, current) -> HttpResponse.ok(current.id()));
        }));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> new Order(pathVariables.getLong("id")), orders));

        assertNotNull(router.findClosest(HttpRequest.GET("/orders/1/id")));
        assertSame(Order.class, kept.get().targetType().getType());
    }

    @Test
    void theErrorRoutesOfAGroupOfLocatedRoutesAnswerTheErrorsOfItsRoutes() {
        LocatedRoutes<Order> orders = TestLocatedRoutes.of(Order.class, order -> {
            order.group(group -> {
                group.error(NoSuchElementException.class, (request, error) -> HttpResponse.notFound(error.getMessage()));
                group.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound("group"));
                group.GET("/grouped").handle((request, pathVariables, current) -> HttpResponse.ok());
            });
            order.GET("/outside").handle((request, pathVariables, current) -> HttpResponse.ok());
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> new Order(pathVariables.getLong("id")), orders));

        HttpRequest<?> grouped = HttpRequest.GET("/orders/1/grouped");
        RouteInfo<?> groupedRoute = router.findClosest(grouped).getRouteInfo();
        assertNotNull(GroupErrorRoutes.findErrorRoute(grouped, groupedRoute, new NoSuchElementException("no line")));
        assertNotNull(GroupErrorRoutes.findStatusRoute(grouped, groupedRoute, HttpStatus.NOT_FOUND.getCode()));
        HttpRequest<?> outside = HttpRequest.GET("/orders/1/outside");
        RouteInfo<?> outsideRoute = router.findClosest(outside).getRouteInfo();
        assertNull(GroupErrorRoutes.findErrorRoute(outside, outsideRoute, new NoSuchElementException("no line")));
    }

    @Test
    void locatedRoutesRejectAGlobalErrorOrStatusRouteWhenItIsDeclared() {
        AtomicReference<IllegalArgumentException> error = new AtomicReference<>();
        AtomicReference<IllegalArgumentException> status = new AtomicReference<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            error.set(assertThrows(IllegalArgumentException.class, () ->
                located.error(IllegalStateException.class, (request, e) -> HttpResponse.serverError())));
            status.set(assertThrows(IllegalArgumentException.class, () ->
                located.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound())));
            // the routes stay usable
            located.GET("/items", LocatedRouteGroupTest::ok);
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));

        assertNotNull(router.findClosest(HttpRequest.GET("/orders/1/items")));
        assertTrue(error.get().getMessage().startsWith("Located routes cannot declare global error routes"), error.get().getMessage());
        assertTrue(status.get().getMessage().startsWith("Located routes cannot declare global status routes"), status.get().getMessage());
    }

    @Test
    void locatedRoutesRejectAPortWhenItIsDeclared() {
        AtomicReference<List<IllegalArgumentException>> errors = new AtomicReference<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            LocatedHttpRouteSpec<Object> number = located.GET("/number");
            LocatedHttpRouteSpec<Object> property = located.GET("/property");
            errors.set(List.of(
                assertThrows(IllegalArgumentException.class, () -> number.port(9090)),
                assertThrows(IllegalArgumentException.class, () -> property.port("${admin.port}")),
                assertThrows(IllegalArgumentException.class, () -> located.group(group -> group.port(9090)))));
            // the routes stay usable
            number.handle(LocatedRouteGroupTest::ok);
            property.handle(LocatedRouteGroupTest::ok);
            located.GET("/items", LocatedRouteGroupTest::ok);
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));

        assertNotNull(router.findClosest(HttpRequest.GET("/orders/1/items")));
        for (IllegalArgumentException error : errors.get()) {
            assertEquals("Located routes cannot expose ports: they are on the ports of their locator routes", error.getMessage());
        }
    }

    @Test
    void thePrefixOfALocatorRouteIsAPath() {
        router(routes -> {
            for (String prefix : List.of("/orders/{id}{?q}", "/orders?q", "/orders#top", "/orders{&q}", "/orders{#top}")) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                    routes.locate(prefix, (request, pathVariables) -> "order", target -> null));
                assertEquals("The prefix of a locator route is a path, without a query or a fragment: " + prefix, error.getMessage());
            }
        });
    }

    @Test
    void aSynchronousLocatorThatReturnsAStageIsToldToBeDeclaredAsynchronously() {
        LocatedRoutes<Order> orders = TestLocatedRoutes.of(Order.class, order -> order.GET("/id").handle((request, pathVariables, current) ->
            HttpResponse.ok(current.id())));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) ->
            CompletableFuture.completedFuture(new Order(pathVariables.getLong("id"))), stage -> orders));

        HttpRequest<?> request = HttpRequest.GET("/orders/1/id");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> router.findClosest(request));
        assertTrue(error.getMessage().endsWith("a locator that returns a CompletionStage of the target is declared with locateAsync"), error.getMessage());
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static HttpResponse<?> invoke(Router router, HttpRequest<?> request, Object... extra) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        Object target = ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target();
        HandlerMethod<?> handler = assertInstanceOf(HandlerMethod.class, ((DefaultUrlRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod());
        Object[] arguments = new Object[2 + extra.length];
        arguments[0] = request;
        arguments[1] = new DefaultPathVariables(match.getVariableValues(), ConversionService.SHARED, target);
        System.arraycopy(extra, 0, arguments, 2, extra.length);
        return (HttpResponse<?>) handler.invoke(arguments);
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    record Order(long id) {
    }

    record Line(Order order, int number) {
    }
}
