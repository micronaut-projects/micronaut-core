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
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.exceptions.RoutingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A handler route names its handler by the class it is declared in, not by the generated class
 * of a lambda, so that the messages about the route identify it.
 */
class HandlerRouteDescriptionTest {

    @Test
    void aRouteNamesTheClassItsHandlerIsDeclaredIn() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/lambda", (request, pathVariables) -> HttpResponse.ok());
        routes.GET("/reference", HandlerRouteDescriptionTest::ok);
        routes.GET("/class", new ItemsHandler());
        routes.error(IllegalStateException.class, (request, error) -> HttpResponse.ok());

        String lambda = "RequestHandler lambda in HandlerRouteDescriptionTest";
        assertEquals(List.of(
            "GET /lambda -> " + lambda + " (application/json)",
            "GET /reference -> " + lambda + " (application/json)",
            "GET /class -> RequestHandler HandlerRouteDescriptionTest$ItemsHandler (application/json)"
        ), assembly.uriRoutes().stream().map(Object::toString).toList());
        assertEquals(" IllegalStateException -> ErrorRouteHandler lambda in HandlerRouteDescriptionTest",
            assembly.errorRoutes().get(0).toString());

        DefaultRouter router = new DefaultRouter(List.of(), List.of(() -> assembly));
        assertEquals("GET /lambda -> " + lambda + " (application/json)",
            router.uriRoutes().filter(route -> route.getUriMatchTemplate().toString().equals("/lambda")).map(Object::toString).collect(Collectors.joining()));
    }

    @Test
    void aDuplicateErrorRouteOfAGroupNamesItsHandler() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        new DefaultHttpRouteBuilder(assembly).group(group -> {
            group.error(IllegalStateException.class, (request, error) -> HttpResponse.ok());
            group.error(IllegalStateException.class, (request, error) -> HttpResponse.ok());
            group.GET("/x", HandlerRouteDescriptionTest::ok);
        });
        RoutingException e = assertThrows(RoutingException.class, () -> new DefaultRouter(List.of(), List.of(() -> assembly)));
        assertTrue(e.getMessage().endsWith("IllegalStateException -> ErrorRouteHandler lambda in HandlerRouteDescriptionTest"), e.getMessage());
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    static final class ItemsHandler implements RequestHandler {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok();
        }
    }
}
