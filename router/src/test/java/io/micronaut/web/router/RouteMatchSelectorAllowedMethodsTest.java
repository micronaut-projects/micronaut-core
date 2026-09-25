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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes of every method of a path ({@link Router#findAny(HttpRequest)}, the allowed methods
 * of a {@code 405}) count only the matches the route selector of their engine selects.
 */
class RouteMatchSelectorAllowedMethodsTest {

    private final Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
        // the selector rejects the only route of the path
        routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/rejected/:rejected")), (request, variables) -> HttpResponse.ok());
        // the selector rejects the route of GET and keeps the route of POST
        routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/kept/:rejected")), (request, variables) -> HttpResponse.ok());
        routes.handle(RouteDeclaration.of(HttpMethod.POST, SelectingColonRouteTemplateEngine.template("/kept/:id")), (request, variables) -> HttpResponse.ok());
        // mixed: rejected routes of GET and PUT, a selected route of POST, a Micronaut route of DELETE
        routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/mixed/:rejected")), (request, variables) -> HttpResponse.ok());
        routes.handle(RouteDeclaration.of(HttpMethod.POST, SelectingColonRouteTemplateEngine.template("/mixed/:id")), (request, variables) -> HttpResponse.ok());
        routes.handle(RouteDeclaration.of(HttpMethod.PUT, SelectingColonRouteTemplateEngine.template("/mixed/:rejected")), (request, variables) -> HttpResponse.ok());
        routes.DELETE("/mixed/{id}", (request, variables) -> HttpResponse.ok());
        // of one method: a rejected route and a selected one
        routes.handle(RouteDeclaration.of(HttpMethod.POST, SelectingColonRouteTemplateEngine.template("/some/:rejected")), (request, variables) -> HttpResponse.ok());
        routes.handle(RouteDeclaration.of(HttpMethod.POST, SelectingColonRouteTemplateEngine.template("/some/:id")), (request, variables) -> HttpResponse.ok());
        // a route whose types are not compatible with the request is kept, for 415
        routes.handle(RouteDeclaration.of(HttpMethod.POST, SelectingColonRouteTemplateEngine.template("/typed/:rejected")), (request, variables) -> HttpResponse.ok())
            .consumes(MediaType.of("text/*"));
        routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/typed/:rejected")), (request, variables) -> HttpResponse.ok());
        // of different engines: the Micronaut policy selects and every route is kept
        routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/engines/:rejected")), (request, variables) -> HttpResponse.ok());
        routes.GET("/engines/{id}", (request, variables) -> HttpResponse.ok());
        // Micronaut routes are unchanged
        routes.GET("/native/{id}", (request, variables) -> HttpResponse.ok());
        routes.POST("/native/{id}", (request, variables) -> HttpResponse.ok());
    });

    @Test
    void aRouteTheSelectorRejectsIsNotFoundInsteadOfNotAllowed() {
        assertNull(router.findClosest(HttpRequest.GET("/rejected/1")));
        assertTrue(router.findAny(HttpRequest.GET("/rejected/1")).isEmpty());
        // nor is its implicit HEAD route, or the GET route for a HEAD request
        assertTrue(router.findAny(HttpRequest.HEAD("/rejected/1")).isEmpty());
        assertTrue(router.findAny(HttpRequest.POST("/rejected/1", "")).isEmpty());
    }

    @Test
    void onlyTheMethodsOfSelectedRoutesAreAllowed() {
        assertNull(router.findClosest(HttpRequest.GET("/kept/1")));
        assertEquals(Set.of("POST"), methods(router.findAny(HttpRequest.GET("/kept/1"))));
        assertNotNull(router.findClosest(HttpRequest.POST("/kept/1", "")));
        assertEquals(Set.of("POST"), methods(router.findAny(HttpRequest.POST("/kept/1", ""))));
    }

    @Test
    void mixedMethodsAndEngines() {
        assertNull(router.findClosest(HttpRequest.GET("/mixed/1")));
        assertNull(router.findClosest(HttpRequest.PUT("/mixed/1", "")));
        assertEquals(Set.of("POST", "DELETE"), methods(router.findAny(HttpRequest.GET("/mixed/1"))));
        assertEquals(Set.of("POST", "DELETE"), methods(router.findAny(HttpRequest.PUT("/mixed/1", ""))));
        assertEquals(Set.of("POST", "DELETE"), methods(router.findAny(HttpRequest.PATCH("/mixed/1", ""))));
    }

    @Test
    void theRejectedRoutesOfAMethodAreRemoved() {
        List<UriRouteMatch<Object, Object>> matches = router.findAny(HttpRequest.GET("/some/1"));
        assertEquals(1, matches.size());
        assertEquals("/some/:id", matches.get(0).getRouteInfo().getRouteTemplate().expression());
    }

    @Test
    void aRouteWhoseTypesAreNotCompatibleIsKept() {
        MutableHttpRequest<String> json = HttpRequest.POST("/typed/1", "{}").contentType(MediaType.APPLICATION_JSON_TYPE);
        assertNull(router.findClosest(json));
        // the POST route does not consume JSON: it is not given to the selector, and gives the 415
        assertEquals(Set.of("POST"), methods(router.findAny(json)));
        // its types are compatible with a request without a body: the selector rejects it
        assertTrue(router.findAny(HttpRequest.GET("/typed/1")).isEmpty());
    }

    @Test
    void routesOfDifferentEnginesAreKept() {
        assertEquals(Set.of("GET", "HEAD"), methods(router.findAny(HttpRequest.PUT("/engines/1", ""))));
        assertEquals(4, router.findAny(HttpRequest.PUT("/engines/1", "")).size());
    }

    @Test
    void micronautRoutesAreUnchanged() {
        assertEquals(Set.of("GET", "HEAD", "POST"), methods(router.findAny(HttpRequest.PUT("/native/1", ""))));
    }

    private static Set<String> methods(List<UriRouteMatch<Object, Object>> matches) {
        return matches.stream().map(match -> match.getRouteInfo().getHttpMethodName()).collect(Collectors.toSet());
    }
}
