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
package io.micronaut.web.router.builder;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A route declared by a creator of the builder, e.g. {@code GET(uri)}, is added by its terminal:
 * a route left without one fails the startup, with a message that names the route and the
 * {@link HttpRoutes} bean that declared it, instead of being dropped.
 */
class MissingTerminalTest {

    @Test
    void aRouteWithoutATerminalFailsTheStartupNamingTheRouteAndItsBean() {
        HttpRoutes pets = new PetRoutes();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assembly(pets));

        assertEquals("The route GET /pets/{id} declared by MissingTerminalTest.PetRoutes has no handler: "
            + "end it with handle, handleAsync or respond", failure.getMessage());
    }

    @Test
    void everyRouteWithoutATerminalIsNamedWithItsBean() {
        HttpRoutes pets = new PetRoutes();
        HttpRoutes items = new ItemRoutes();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assembly(pets, items));

        assertEquals("The routes GET /pets/{id} declared by MissingTerminalTest.PetRoutes, PUT, PATCH /items/{id} declared by "
            + "MissingTerminalTest.ItemRoutes, PROPFIND /items declared by MissingTerminalTest.ItemRoutes, any /anything declared by "
            + "MissingTerminalTest.ItemRoutes have no handler: end each with handle, handleAsync or respond", failure.getMessage());
    }

    @Test
    void aRouteOfAGroupWithoutATerminalFailsWhenTheLambdaOfTheGroupReturns() {
        HttpRoutes grouped = new GroupRoutes();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assembly(grouped));

        assertEquals("The route POST /api/items declared by MissingTerminalTest.GroupRoutes has no handler: "
            + "end it with handle, handleAsync or respond", failure.getMessage());
    }

    @Test
    void theRouteOfAGroupIsCheckedBeforeTheRoutesAfterTheGroup() {
        List<String> declared = new ArrayList<>();
        HttpRoutes routes = builder -> {
            builder.group(group -> group.GET("/grouped").body(String.class));
            declared.add("after the group");
        };
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assembly(routes));

        assertEquals(List.of(), declared);
        assertEquals("GET /grouped", failure.getMessage().substring("The route ".length(), "The route GET /grouped".length()));
    }

    @Test
    void theBuilderFailsWhenItIsClosedWithARouteWithoutATerminal() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        builder.GET("/ended", MissingTerminalTest::ok);
        builder.POST("/items").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class);

        IllegalStateException failure = assertThrows(IllegalStateException.class, builder::close);

        assertEquals("The route POST /items has no handler: end it with handle, handleAsync or respond", failure.getMessage());
        // the route with a terminal was added, the other one was not
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        assertNotNull(router.findClosest(HttpRequest.GET("/ended")));
        assertNull(router.findClosest(HttpRequest.POST("/items", "text").contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void aTerminalEndsTheRouteOnce() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        HttpRouteSpec route = builder.GET("/once");
        route.handle(MissingTerminalTest::ok);

        HttpResponse<?> ok = HttpResponse.ok();
        IllegalStateException again = assertThrows(IllegalStateException.class, () -> route.respond(ok));
        assertEquals("The route GET /once was already ended: give its settings before its one terminal, handle, handleAsync or respond",
            again.getMessage());
        assertThrows(IllegalStateException.class, () -> route.produces(MediaType.TEXT_PLAIN_TYPE));
        assertThrows(IllegalStateException.class, () -> route.body(String.class).handle((request, pathVariables, body) -> HttpResponse.ok()));
        builder.close();
    }

    @Test
    void aTerminalWithoutAHandlerDropsTheRoute() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);

        NullPointerException handler = assertThrows(NullPointerException.class, () -> builder.GET("/dropped", null));
        assertEquals("handler", handler.getMessage());
        HttpRouteSpec dropped = builder.GET("/dropped");
        NullPointerException response = assertThrows(NullPointerException.class, () -> dropped.respond((HttpResponse<?>) null));
        assertEquals("response", response.getMessage());

        // the failed declarations left no route without a terminal
        builder.close();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        assertNull(router.findClosest(HttpRequest.GET("/dropped")));
    }

    @Test
    void aLocatedRouteWithoutATerminalFailsWhenTheLocatedRoutesAreDeclared() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultLocatedHttpRouteBuilder<String> builder = new DefaultLocatedHttpRouteBuilder<>(assembly, Argument.of(String.class), PetRoutes.class);
        builder.GET("/items", MissingTerminalTest::ok);
        builder.POST("/items").body(String.class);

        IllegalStateException failure = assertThrows(IllegalStateException.class, builder::close);

        assertEquals("The route POST /items declared by MissingTerminalTest.PetRoutes has no handler: "
            + "end it with handle, handleAsync or respond", failure.getMessage());
    }

    private static HttpRoutesAssembly assembly(HttpRoutes... routes) {
        return new HttpRoutesAssembly(ExecutionHandleLocator.EMPTY, ConversionService.SHARED, List.of(routes), null);
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    static final class PetRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/pets", MissingTerminalTest::ok);
            routes.GET("/pets/{id}").produces(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    static final class ItemRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.route(EnumSet.of(HttpMethod.PUT, HttpMethod.PATCH), "/items/{id}");
            routes.route("PROPFIND", "/items").form();
            routes.any("/anything").body();
        }

        @Override
        public int getOrder() {
            return 1;
        }
    }

    static final class GroupRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/api", api -> {
                api.GET("/items", MissingTerminalTest::ok);
                api.POST("/items").consumes(MediaType.APPLICATION_JSON_TYPE);
            });
        }
    }
}
