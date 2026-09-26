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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Typed locators whose prefix is a template of another engine, and the typed locators of the
 * tables of located targets of a type.
 */
class RouteTemplateEngineTypedLocatorTest {

    @Test
    void aTypedLocatorOfAnotherEngineGivesItsTargetToTheTableAndItsHandlers() {
        TestLocatedRoutes<Order> orders = TestLocatedRoutes.of(Order.class, order ->
            order.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")), (request, variables, target) ->
                HttpResponse.ok(target.id() + "/" + variables.getString("item"))));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> {
                long id = variables.getLong("id");
                return id == 0 ? null : new Order(id);
            }, orders));

        assertEquals("5/3", invoke(router, HttpRequest.GET("/orders/5/items/3")));
        assertNull(router.findClosest(HttpRequest.GET("/orders/0/items/3")));
    }

    @Test
    void theTablesOfTypedTargetsLocateAgainWithTemplatesOfAnotherEngine() {
        Node tree = new Node("root", Map.of("a", new Node("a", Map.of("b", new Node("b", Map.of())))));
        List<TestLocatedRoutes<Node>> nodes = new ArrayList<>(1);
        nodes.add(TestLocatedRoutes.of(Node.class, node -> {
            node.handle(HttpMethod.GET, "/name", (request, variables, target) -> HttpResponse.ok(target.name()));
            node.locate(ColonRouteTemplateEngine.template("/:child"), (request, variables, parent) ->
                parent.children().get(variables.getString("child")), child -> nodes.get(0));
            node.locateAsync(ColonRouteTemplateEngine.template("/async/:child"), (request, variables, parent) ->
                CompletableFuture.completedFuture(parent.children().get(variables.getString("child"))), child -> nodes.get(0));
        }));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locateAsync(ColonRouteTemplateEngine.template("/tree/:root"), (request, variables) ->
                CompletableFuture.completedFuture(tree), node -> nodes.get(0)));

        assertEquals("b", invoke(router, HttpRequest.GET("/tree/r/a/b/name")));
        assertEquals("b", invoke(router, HttpRequest.GET("/tree/r/a/async/b/name")));
        assertNull(router.findClosest(HttpRequest.GET("/tree/r/a/c/name")));
    }

    @Test
    void aTargetOfAnotherTypeFails() {
        TestLocatedRoutes<Order> orders = TestLocatedRoutes.of(Order.class, order ->
            order.handle(HttpMethod.GET, "/id", (request, variables, target) -> HttpResponse.ok(target.id())));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> orders));
        assertThrows(IllegalStateException.class, () -> router.findClosest(HttpRequest.GET("/orders/1/id")));
    }

    private static Object invoke(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        Object target = ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target();
        HandlerMethod<?> handler = (HandlerMethod<?>) ((DefaultUrlRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod();
        HttpResponse<?> response = (HttpResponse<?>) handler.invoke(new Object[]{request,
            new DefaultPathVariables(match.getVariableValues(), ConversionService.SHARED, target)});
        return response.body();
    }

    record Order(long id) {
    }

    record Node(String name, Map<String, Node> children) {
    }
}
