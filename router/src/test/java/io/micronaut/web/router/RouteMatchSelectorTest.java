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

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route selector of an engine, see {@link io.micronaut.web.router.spi.RouteMatchSelector}.
 */
class RouteMatchSelectorTest {

    private static final RouteTemplate ITEM = SelectingColonRouteTemplateEngine.template("/items/:id");

    private final Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
        routes.handle(RouteDeclaration.of(HttpMethod.GET, ITEM), (request, variables) -> HttpResponse.ok()).produces(MediaType.TEXT_PLAIN_TYPE);
        routes.handle(RouteDeclaration.of(HttpMethod.GET, ITEM), (request, variables) -> HttpResponse.ok()).produces(MediaType.APPLICATION_JSON_TYPE);
        routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/single/:id")), (request, variables) -> HttpResponse.ok())
            .produces(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_PLAIN_TYPE);
        routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/mixed/:id")), (request, variables) -> HttpResponse.ok());
        routes.GET("/mixed/{id}", (request, variables) -> HttpResponse.ok());
    });

    @Test
    void theSelectorPicksByTheAcceptedType() {
        UriRouteMatch<Object, Object> text = router.findClosest(get("/items/1", "text/plain"));
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), text.getRouteInfo().getProduces());
        assertEquals(Optional.of(MediaType.TEXT_PLAIN_TYPE), text.getSelectedMediaType());
        assertEquals("1", text.getVariableValues().get("id"));

        UriRouteMatch<Object, Object> json = router.findClosest(get("/items/1", "application/json"));
        assertEquals(List.of(MediaType.APPLICATION_JSON_TYPE), json.getRouteInfo().getProduces());
        assertEquals(Optional.of(MediaType.APPLICATION_JSON_TYPE), json.getSelectedMediaType());

        // by quality
        UriRouteMatch<Object, Object> preferred = router.findClosest(get("/items/1", "text/plain;q=0.5, application/json"));
        assertEquals(Optional.of(MediaType.APPLICATION_JSON_TYPE), preferred.getSelectedMediaType());
    }

    @Test
    void withoutAnAcceptHeaderTheSelectorDecidesInsteadOfAnAmbiguity() {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/items/1"));
        assertNotNull(match);
        // the first route of the table
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), match.getRouteInfo().getProduces());
        assertEquals(1, router.findAllClosest(HttpRequest.GET("/items/1")).size());
    }

    @Test
    void theSelectorNegotiatesTheTypeOfASingleRoute() {
        assertEquals(Optional.of(MediaType.TEXT_PLAIN_TYPE), router.findClosest(get("/single/1", "text/plain")).getSelectedMediaType());
        assertEquals(Optional.of(MediaType.APPLICATION_JSON_TYPE), router.findClosest(get("/single/1", "*/*")).getSelectedMediaType());
        assertEquals(Optional.of(MediaType.APPLICATION_JSON_TYPE), router.findAllClosest(get("/single/1", "text/html;q=0.9, application/json;q=0.5")).get(0).getSelectedMediaType());
    }

    @Test
    void candidatesOfDifferentEnginesAreSelectedByTheMicronautPolicy() {
        int before = SelectingColonRouteTemplateEngine.SELECTIONS.get();
        // the Micronaut route and the colon route tie on every key of the Micronaut policy
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/mixed/1")));
        assertEquals(2, router.findAllClosest(HttpRequest.GET("/mixed/1")).size());
        assertEquals(before, SelectingColonRouteTemplateEngine.SELECTIONS.get());
    }

    @Test
    void micronautRoutesHaveNoSelectedType() {
        Router micronaut = RouteTemplateEngineOrderTest.router(null, routes -> routes.GET("/m/{id}", (request, variables) -> HttpResponse.ok()));
        assertTrue(micronaut.findClosest(HttpRequest.GET("/m/1")).getSelectedMediaType().isEmpty());
    }

    @Test
    void theSelectorCanSelectNothing() {
        // the route that produces JSON produces no image: the router removes it before the selector
        assertNull(router.findClosest(get("/single/1", "image/png")));
        assertTrue(router.findAllClosest(get("/single/1", "image/png")).isEmpty());
    }

    @Test
    void aSelectedMatchMustBeACandidate() {
        Router rogue = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, RogueSelectingEngine.TEMPLATE), (request, variables) -> HttpResponse.ok()));
        assertThrows(IllegalStateException.class, () -> rogue.findClosest(HttpRequest.GET("/rogue/1")));
    }

    @Test
    void ambiguousSelectionsAreDuplicates() {
        Router ambiguous = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, AmbiguousSelectingEngine.template("/amb/:id")), (request, variables) -> HttpResponse.ok());
            routes.handle(RouteDeclaration.of(HttpMethod.GET, AmbiguousSelectingEngine.template("/amb/:id")), (request, variables) -> HttpResponse.ok());
        });
        assertThrows(DuplicateRouteException.class, () -> ambiguous.findClosest(HttpRequest.GET("/amb/1")));
        assertEquals(2, ambiguous.findAllClosest(HttpRequest.GET("/amb/1")).size());
    }

    @Test
    void locatedRoutesKeepTheSelectedType() {
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/items/:item")), (request, variables) -> HttpResponse.ok())
                .produces(MediaType.TEXT_PLAIN_TYPE);
            routes.handle(RouteDeclaration.of(HttpMethod.GET, SelectingColonRouteTemplateEngine.template("/items/:item")), (request, variables) -> HttpResponse.ok())
                .produces(MediaType.APPLICATION_XML_TYPE);
        });
        Router located = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(SelectingColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> items));
        UriRouteMatch<Object, Object> match = located.findClosest(get("/orders/5/items/3", "application/xml"));
        assertNotNull(match);
        assertEquals("5", match.getVariableValues().get("id"));
        assertEquals("3", match.getVariableValues().get("item"));
        assertEquals(Optional.of(MediaType.APPLICATION_XML_TYPE), match.getSelectedMediaType());
    }

    private static MutableHttpRequest<Object> get(String path, String accept) {
        return HttpRequest.GET(path).header(HttpHeaders.ACCEPT, accept);
    }
}
