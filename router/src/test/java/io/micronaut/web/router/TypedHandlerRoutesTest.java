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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generic types of the handler routes: the declared type of the body of the responses, and
 * the typed locators and located targets.
 */
class TypedHandlerRoutesTest {

    private static final Argument<List<Item>> ITEMS = Argument.listOf(Item.class);

    private final RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void theDeclaredResponseTypeIsTheResponseBodyTypeOfTheRouteForEveryKindOfHandler() {
        Router router = router(routes -> {
            routes.GET("/sync", (request, pathVariables) -> HttpResponse.ok(List.of())).responseType(ITEMS);
            routes.POST("/body", Argument.of(Item.class), (request, pathVariables, item) -> HttpResponse.ok(List.of(item))).responseType(ITEMS);
            routes.PUT("/form", (request, pathVariables, form) -> HttpResponse.ok()).responseType(ITEMS);
            routes.asyncGET("/async", (request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok())).responseType(ITEMS);
            routes.handle(Set.of(HttpMethod.GET, HttpMethod.DELETE), "/methods", (request, pathVariables) -> HttpResponse.ok()).responseType(ITEMS);
            routes.handle(RouteDeclaration.of(HttpMethod.GET, "/declared"), (request, pathVariables) -> HttpResponse.ok()).responseType(ITEMS);
            routes.handle("PROPFIND", "/custom", (request, pathVariables) -> HttpResponse.ok()).responseType(ITEMS);
            routes.GET("/untyped", (request, pathVariables) -> HttpResponse.ok());
        });

        for (HttpRequest<?> request : List.of(HttpRequest.GET("/sync"), HttpRequest.POST("/body", ""), HttpRequest.PUT("/form", ""),
            HttpRequest.GET("/async"), HttpRequest.GET("/methods"), HttpRequest.DELETE("/methods"), HttpRequest.HEAD("/methods"),
            HttpRequest.GET("/declared"), HttpRequest.create(HttpMethod.CUSTOM, "/custom", "PROPFIND"))) {
            RouteInfo<?> route = route(router, request);
            assertEquals(ITEMS, route.getResponseBodyType(), request.getMethodName() + " " + request.getPath());
            assertEquals(Item.class, route.getResponseBodyType().getFirstTypeVariable().orElseThrow().getType());
        }
        // the handler still returns a response, or a stage of one
        assertEquals(HttpResponse.class, route(router, HttpRequest.GET("/sync")).getReturnType().getType());
        RouteInfo<?> async = route(router, HttpRequest.GET("/async"));
        assertEquals(CompletionStage.class, async.getReturnType().getType());
        assertEquals(HttpResponse.class, async.getReturnType().getFirstTypeVariable().orElseThrow().getType());
        assertTrue(async.isAsync());
        assertEquals(Object.class, route(router, HttpRequest.GET("/untyped")).getResponseBodyType().getType());
    }

    @Test
    void theDeclaredResponseTypeKeepsTheAnnotationsOfTheRoute() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Produces.class.getName(), Map.of("value", new String[]{"application/x-items"}));
        AnnotationMetadataProvider annotated = new AnnotationMetadataProvider() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return metadata;
            }
        };
        Router router = router(routes -> {
            routes.GET("/before", (request, pathVariables) -> HttpResponse.ok()).responseType(ITEMS).annotationMetadata(annotated);
            routes.GET("/after", (request, pathVariables) -> HttpResponse.ok()).annotationMetadata(annotated).responseType(ITEMS);
        });
        for (String path : List.of("/before", "/after")) {
            RouteInfo<?> route = route(router, HttpRequest.GET(path));
            assertEquals(ITEMS, route.getResponseBodyType(), path);
            assertTrue(route.getReturnType().getAnnotationMetadata().hasAnnotation(Produces.class), path);
        }
    }

    @Test
    void errorAndStatusRoutesHaveTheDeclaredResponseType() {
        Router router = router(routes -> {
            routes.error(IllegalStateException.class, (request, error) -> HttpResponse.ok()).responseType(ITEMS);
            routes.errorAsync(IllegalArgumentException.class, (request, error) -> CompletableFuture.completedFuture(HttpResponse.ok()))
                .responseType(ITEMS);
            routes.status(HttpStatus.CONFLICT, request -> HttpResponse.ok()).responseType(ITEMS);
            routes.statusAsync(HttpStatus.GONE, request -> CompletableFuture.completedFuture(HttpResponse.ok())).responseType(ITEMS);
        });
        HttpRequest<?> request = HttpRequest.GET("/");
        assertEquals(ITEMS, router.findErrorRoute(new IllegalStateException(), request).orElseThrow().getRouteInfo().getResponseBodyType());
        assertEquals(ITEMS, router.findErrorRoute(new IllegalArgumentException(), request).orElseThrow().getRouteInfo().getResponseBodyType());
        assertEquals(ITEMS, router.findStatusRoute(HttpStatus.CONFLICT, request).orElseThrow().getRouteInfo().getResponseBodyType());
        assertEquals(ITEMS, router.findStatusRoute(HttpStatus.GONE, request).orElseThrow().getRouteInfo().getResponseBodyType());
    }

    @Test
    void aLocatorRouteHasNoResponseType() {
        HandlerMethod<?> locator = HandlerMethod.of(new RouteLocator((io.micronaut.web.router.builder.LocatorHandler<String>) (request, pathVariables) -> "target", target -> RouteTable.empty()));
        assertThrows(IllegalStateException.class, () -> locator.responseType(ITEMS));
    }

    @Test
    void theHandlersOfATypedTableReceiveTheTarget() throws Exception {
        RouteTable orders = tables.buildLocatedHttpRoutes(Order.class, order -> {
            assertEquals(Argument.of(Order.class), order.targetType());
            order.handle(HttpMethod.GET, "/id", (request, pathVariables, target) -> HttpResponse.ok(target.id()));
            order.handle(HttpMethod.POST, "/items", Argument.of(Item.class), (request, pathVariables, target, item) ->
                HttpResponse.ok(target.id() + " " + item.name()));
            order.handleAsync(HttpMethod.GET, "/async", (request, pathVariables, target) ->
                CompletableFuture.completedFuture(HttpResponse.ok("async " + target.id())));
            order.handle(RouteDeclaration.of(HttpMethod.GET, "/declared"), (request, pathVariables, target) ->
                HttpResponse.ok("declared " + target.id()));
            // the untyped handlers read the target
            order.GET("/untyped", (request, pathVariables) -> HttpResponse.ok("untyped " + pathVariables.locatedTarget(Order.class).id()));
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> {
            long id = pathVariables.getLong("id");
            return id == 0 ? null : new Order(id);
        }, order -> orders));

        assertEquals(5L, invoke(router, HttpRequest.GET("/orders/5/id")).body());
        assertEquals("5 pen", invoke(router, HttpRequest.POST("/orders/5/items", ""), new Item("pen")).body());
        // the asynchronous handler of the table is routed, see the TCK for its call
        assertNotNull(router.findClosest(HttpRequest.GET("/orders/5/async")));
        assertEquals("declared 5", invoke(router, HttpRequest.GET("/orders/5/declared")).body());
        assertEquals("untyped 5", invoke(router, HttpRequest.GET("/orders/5/untyped")).body());
        // no target: no route
        assertNull(router.findClosest(HttpRequest.GET("/orders/0/id")));
    }

    @Test
    void theRouteTableFunctionReceivesTheTypeTheLocatorReturns() {
        RouteTable small = tables.buildLocatedHttpRoutes(Order.class, order -> order.GET("/size", (request, pathVariables) -> HttpResponse.ok("small")));
        RouteTable big = tables.buildLocatedHttpRoutes(Order.class, order -> order.GET("/size", (request, pathVariables) -> HttpResponse.ok("big")));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> new Order(pathVariables.getLong("id")),
            order -> order.id() > 100 ? big : small));
        assertEquals("big", invoke(router, HttpRequest.GET("/orders/101/size")).body());
        assertEquals("small", invoke(router, HttpRequest.GET("/orders/1/size")).body());
    }

    @Test
    void theTablesOfTypedTargetsLocateAgainFromTheirTarget() throws Exception {
        Node tree = new Node("root", Map.of("a", new Node("a", Map.of("b", new Node("b", Map.of())))));
        RouteTable[] nodes = new RouteTable[1];
        nodes[0] = tables.buildLocatedHttpRoutes(Argument.of(Node.class), node -> {
            node.handle(HttpMethod.GET, "/name", (request, pathVariables, target) -> HttpResponse.ok(target.name()));
            node.locate("/{child}", (request, pathVariables, parent) -> parent.children().get(pathVariables.getString("child")), child -> nodes[0]);
            node.locateAsync("/async/{child}", (request, pathVariables, parent) ->
                CompletableFuture.completedFuture(parent.children().get(pathVariables.getString("child"))), child -> nodes[0]);
        });
        Router router = router(routes -> routes.locate("/tree", (request, pathVariables) -> tree, node -> nodes[0]));

        assertEquals("b", invoke(router, HttpRequest.GET("/tree/a/b/name")).body());
        assertEquals("b", invoke(router, HttpRequest.GET("/tree/a/async/b/name")).body());
        assertEquals("root", invoke(router, HttpRequest.GET("/tree/name")).body());
        assertNull(router.findClosest(HttpRequest.GET("/tree/a/c/name")));
        assertNull(router.findClosest(HttpRequest.GET("/tree/async/c/name")));
    }

    @Test
    void anAsynchronousTypedLocatorLocatesTheTarget() throws Exception {
        RouteTable orders = tables.buildLocatedHttpRoutes(Order.class, order ->
            order.handle(HttpMethod.GET, "/id", (request, pathVariables, target) -> HttpResponse.ok(target.id())));
        Router router = router(routes -> routes.locateAsync("/orders/{id}", (request, pathVariables) -> {
            long id = pathVariables.getLong("id");
            return CompletableFuture.completedFuture(id == 0 ? null : new Order(id));
        }, order -> orders));
        assertEquals(7L, invoke(router, HttpRequest.GET("/orders/7/id")).body());
        assertNull(router.findClosest(HttpRequest.GET("/orders/0/id")));
    }

    @Test
    void aTargetThatIsNotOfTheTypeOfTheTableFails() {
        RouteTable orders = tables.buildLocatedHttpRoutes(Order.class, order ->
            order.handle(HttpMethod.GET, "/id", (request, pathVariables, target) -> HttpResponse.ok(target.id())));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order " + pathVariables.getString("id"),
            target -> orders));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> router.findClosest(HttpRequest.GET("/orders/1/id")));
        assertTrue(error.getMessage().contains(Order.class.getName()), error.getMessage());
        assertTrue(error.getMessage().contains("order 1"), error.getMessage());
    }

    @Test
    void aTypedHandlerOutsideOfALocatedRouteFails() throws Exception {
        RouteTable orders = tables.buildLocatedHttpRoutes(Order.class, order ->
            order.handle(HttpMethod.GET, "/id", (request, pathVariables, target) -> HttpResponse.ok(target.id())));
        UriRouteMatch<Object, Object> match = ((DefaultRouteTable) orders).router(null).findClosest(HttpRequest.GET("/id"));
        assertNotNull(match);
        HandlerMethod<?> handler = (HandlerMethod<?>) ((DefaultUrlRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod();
        PathVariables pathVariables = new DefaultPathVariables(Map.of(), ConversionService.SHARED);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> handler.invoke(new Object[]{HttpRequest.GET("/id"), pathVariables}));
        assertTrue(error.getMessage().contains(Order.class.getName()), error.getMessage());
    }

    @Test
    void thePathVariablesConvertToGenericTypes() {
        PathVariables pathVariables = new DefaultPathVariables(Map.of("ids", "1,2,3", "id", "4"), ConversionService.SHARED);
        assertEquals(List.of(1, 2, 3), pathVariables.get("ids", Argument.listOf(Integer.class)));
        assertEquals(Optional.of(List.of(1L, 2L, 3L)), pathVariables.find("ids", Argument.listOf(Long.class)));
        assertEquals(Optional.empty(), pathVariables.find("missing", Argument.listOf(Long.class)));
        assertEquals(4, pathVariables.get("id", Argument.INT));
        assertEquals(4, pathVariables.get("id", Integer.class));
        ConversionErrorException error = assertThrows(ConversionErrorException.class, () -> pathVariables.get("ids", Argument.of(Integer.class)));
        assertEquals("ids", error.getArgument().getName());
    }

    private static RouteInfo<?> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return match.getRouteInfo();
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

    record Item(String name) {
    }

    record Node(String name, Map<String, Node> children) {
    }
}
