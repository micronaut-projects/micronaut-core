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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes of any method, see {@code HttpRouteBuilder.any(...)}, next to the routes of route
 * template engines. A route of any method has a Micronaut URI template: it competes with the
 * routes of another engine by the Micronaut policy, and a route of a specific method is preferred
 * among equally close matches.
 */
class RouteTemplateEngineAnyMethodTest {

    private static final RouteTemplate COLON_ITEM = ColonRouteTemplateEngine.template("/items/:id");

    @Test
    void aRouteOfAnotherEngineIsPreferredToARouteOfAnyMethodOfTheSameSpecificity() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, COLON_ITEM), (request, variables) -> HttpResponse.ok());
            routes.any("/items/{id}", (request, variables) -> HttpResponse.ok());
        });
        UriRouteMatch<Object, Object> get = router.findClosest(HttpRequest.GET("/items/5"));
        assertNotNull(get);
        assertEquals(COLON_ITEM, get.getRouteInfo().getRouteTemplate());
        assertEquals("5", get.getVariableValues().get("id"));

        UriRouteMatch<Object, Object> delete = router.findClosest(HttpRequest.DELETE("/items/5"));
        assertNotNull(delete);
        assertEquals(RouteTemplate.micronaut("/items/{id}"), delete.getRouteInfo().getRouteTemplate());
        UriRouteMatch<Object, Object> custom = router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, "/items/5", "PROPFIND"));
        assertNotNull(custom);
        assertEquals(RouteTemplate.micronaut("/items/{id}"), custom.getRouteInfo().getRouteTemplate());
    }

    @Test
    void aRouteOfAnyMethodWithMoreLiteralTextWinsOverARouteOfAnotherEngine() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, COLON_ITEM), (request, variables) -> HttpResponse.ok());
            routes.any("/items/special", (request, variables) -> HttpResponse.ok());
        });
        assertEquals(RouteTemplate.micronaut("/items/special"), router.findClosest(HttpRequest.GET("/items/special")).getRouteInfo().getRouteTemplate());
        assertEquals(COLON_ITEM, router.findClosest(HttpRequest.GET("/items/5")).getRouteInfo().getRouteTemplate());
    }

    @Test
    void theRouteSelectorOfAnEngineDoesNotSelectAmongMatchesWithARouteOfAnyMethod() {
        RouteTemplate item = SelectingColonRouteTemplateEngine.template("/items/:id");
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, item), (request, variables) -> HttpResponse.ok()).produces(MediaType.TEXT_PLAIN_TYPE);
            routes.any("/items/{id}", (request, variables) -> HttpResponse.ok());
        });
        // two engines: the Micronaut policy, which prefers the route of a specific method
        UriRouteMatch<Object, Object> get = router.findClosest(HttpRequest.GET("/items/5"));
        assertNotNull(get);
        assertEquals(item, get.getRouteInfo().getRouteTemplate());
        assertTrue(get.getSelectedMediaType().isEmpty());
        assertEquals(RouteTemplate.micronaut("/items/{id}"), router.findClosest(HttpRequest.POST("/items/5", "")).getRouteInfo().getRouteTemplate());
    }

    @Test
    void theLocatedRoutesOfALocatorOfAnotherEngineMayBeRoutesOfAnyMethod() {
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes -> routes.any("/items/{item}", (request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", items));
        for (HttpMethod method : new HttpMethod[] {HttpMethod.GET, HttpMethod.DELETE, HttpMethod.PATCH}) {
            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.create(method, "/orders/5/items/3"));
            assertNotNull(match, method.name());
            assertEquals("5", match.getVariableValues().get("id"), method.name());
            assertEquals("3", match.getVariableValues().get("item"), method.name());
        }
        // a locator routes the standard methods only, whatever the engine of its prefix
        assertNull(router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, "/orders/5/items/3", "PROPFIND")));
    }
}
