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
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes of a second, test-only route template engine next to Micronaut routes.
 */
class RouteTemplateEnginesRouterTest {

    private static final RouteTemplate COLON_ITEM = ColonRouteTemplateEngine.template("/items/:id");

    @Test
    void theSameTextUnderTwoEnginesIsTwoDifferentRoutes() {
        Router router = router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, "/items/{id:3}"), (request, variables) -> HttpResponse.ok("micronaut"));
            routes.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/{id:3}")), (request, variables) -> HttpResponse.ok("colon"));
        });
        UriRouteInfo<?, ?> micronaut = route(router, HttpMethod.GET, RouteTemplate.micronaut("/items/{id:3}"));
        UriRouteInfo<?, ?> colon = route(router, HttpMethod.GET, ColonRouteTemplateEngine.template("/items/{id:3}"));

        // {id:3} is a variable of at most three characters for Micronaut
        assertNotNull(micronaut.tryMatch("/items/abc"));
        assertEquals("abc", micronaut.tryMatch("/items/abc").getVariableValues().get("id"));
        assertNull(micronaut.tryMatch("/items/abcd"));
        // and literal text for the colon engine
        assertNull(colon.tryMatch("/items/abc"));
        assertNotNull(colon.tryMatch("/items/{id:3}"));

        assertEquals(RouteTemplate.micronaut("/items/{id:3}"), findClosest(router, "/items/abc").getRouteInfo().getRouteTemplate());
        // both GET routes have their own implicit HEAD route
        assertEquals(2, router.uriRoutes().filter(r -> r.getHttpMethod() == HttpMethod.HEAD).count());
    }

    @Test
    void aForeignRouteMatchesAndCaptures() {
        Router router = router(null, routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, COLON_ITEM), (request, variables) -> HttpResponse.ok(variables.getString("id"))));
        UriRouteMatch<Object, Object> match = findClosest(router, "/items/5");
        assertEquals(COLON_ITEM, match.getRouteInfo().getRouteTemplate());
        assertEquals("5", match.getVariableValues().get("id"));
        assertNull(findClosestOrNull(router, "/items"));
        assertNull(findClosestOrNull(router, "/items/5/6"));
        // the query and a trailing slash are not part of the matched path, as for Micronaut routes
        assertNotNull(findClosestOrNull(router, "/items/5/?x=1"));
    }

    @Test
    void aForeignRouteHasNoUriMatchTemplate() {
        Router router = router(null, routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, COLON_ITEM), (request, variables) -> HttpResponse.ok()));
        UriRouteInfo<?, ?> route = route(router, HttpMethod.GET, COLON_ITEM);
        assertInstanceOf(DefaultUrlRouteInfo.class, route);
        assertEquals(COLON_ITEM, route.getRouteTemplate());
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, route::getUriMatchTemplate);
        assertTrue(e.getMessage().contains(ColonRouteTemplateEngine.ID), e.getMessage());
    }

    @Test
    void theMicronautPolicySelectsAcrossEngines() {
        Router router = router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, COLON_ITEM), (request, variables) -> HttpResponse.ok());
            routes.GET("/items/special", (request, variables) -> HttpResponse.ok());
        });
        // more literal text wins
        assertEquals(RouteTemplate.micronaut("/items/special"), findClosest(router, "/items/special").getRouteInfo().getRouteTemplate());
        assertEquals(COLON_ITEM, findClosest(router, "/items/5").getRouteInfo().getRouteTemplate());
    }

    @Test
    void aForeignRouteIsMountedLiterallyUnderTheContextPath() {
        Router router = router("/:ctx", routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, COLON_ITEM), (request, variables) -> HttpResponse.ok()));
        UriRouteInfo<?, ?> route = route(router, HttpMethod.GET, ColonRouteTemplateEngine.template("/:ctx/items/:id"));
        // the context path is literal, not a variable of the colon language
        assertNotNull(route.tryMatch("/:ctx/items/5"));
        assertEquals(List.of("id"), List.copyOf(route.tryMatch("/:ctx/items/5").getVariableValues().keySet()));
        assertNull(route.tryMatch("/other/items/5"));
        assertNull(findClosestOrNull(router, "/items/5"));
        assertEquals(1, router.uriRoutes().filter(r -> r.getHttpMethod() == HttpMethod.HEAD).count());
    }

    @Test
    void micronautRoutesUnderTheContextPathAreUnchanged() {
        Router router = router("/ctx", routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, "/items/{id}"), (request, variables) -> HttpResponse.ok()));
        UriRouteInfo<?, ?> route = route(router, HttpMethod.GET, RouteTemplate.micronaut("/ctx/items/{id}"));
        assertEquals("/ctx/items/{id}", route.getUriMatchTemplate().toString());
        assertNotNull(route.tryMatch("/ctx/items/5"));
    }

    @Test
    void implicitHeadRoutesAreKeyedByEngineAndTemplate() {
        Router router = router(null, routes -> {
            // an explicit HEAD route with the same text, but of the Micronaut engine
            routes.handle(RouteDeclaration.of(HttpMethod.HEAD, "/items/:id"), (request, variables) -> HttpResponse.ok());
            routes.handle(RouteDeclaration.of(HttpMethod.GET, COLON_ITEM), (request, variables) -> HttpResponse.ok());
            // an explicit HEAD route of the same engine and template
            routes.handle(RouteDeclaration.of(HttpMethod.HEAD, ColonRouteTemplateEngine.template("/books/:id")), (request, variables) -> HttpResponse.ok());
            routes.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/books/:id")), (request, variables) -> HttpResponse.ok());
        });
        List<UriRouteInfo<?, ?>> heads = router.uriRoutes().filter(r -> r.getHttpMethod() == HttpMethod.HEAD).toList();
        assertEquals(3, heads.size());
        assertTrue(heads.stream().anyMatch(r -> r.isImplicitHead() && r.getRouteTemplate().equals(COLON_ITEM)));
        assertFalse(heads.stream().anyMatch(r -> r.isImplicitHead() && r.getRouteTemplate().expression().equals("/books/:id")));
    }

    @Test
    void aMissingEngineFailsWhenTheRouteIsDeclared() {
        RouteTemplate template = RouteTemplate.of("test.missing", "/x");
        // creating the declaration does not resolve the engine
        RouteDeclaration declaration = RouteDeclaration.of(HttpMethod.GET, template);
        assertEquals(template, declaration.template());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> router(null, routes ->
            routes.handle(declaration, (request, variables) -> HttpResponse.ok())));
        assertTrue(e.getMessage().contains("test.missing"), e.getMessage());
        assertTrue(e.getMessage().contains(ColonRouteTemplateEngine.ID), e.getMessage());
    }

    @Test
    void nestingGoesThroughTheEngine() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        UriRoute parent = assembly.addRoute("GET", HttpMethod.GET, ColonRouteTemplateEngine.template("/shelves/:shelf"), List.of(MediaType.APPLICATION_JSON_TYPE), handle());
        parent.nest(() -> assembly.addRoute("GET", HttpMethod.GET, ColonRouteTemplateEngine.template("/books/:book"), List.of(MediaType.APPLICATION_JSON_TYPE), handle()));
        UriRoute nested = assembly.uriRoutes().get(1);
        assertEquals(ColonRouteTemplateEngine.template("/shelves/:shelf/books/:book"), nested.getRouteTemplate());
        UriRouteMatch<Object, Object> match = nested.toRouteInfo().tryMatch("/shelves/1/books/2");
        assertNotNull(match);
        assertEquals("1", match.getVariableValues().get("shelf"));
        assertEquals("2", match.getVariableValues().get("book"));
    }

    @Test
    void nestingAcrossEnginesIsRejected() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        UriRoute micronautParent = assembly.addRoute("GET", HttpMethod.GET, "/shelves/{shelf}", List.of(MediaType.APPLICATION_JSON_TYPE), handle());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> micronautParent.nest(() ->
            assembly.addRoute("GET", HttpMethod.GET, ColonRouteTemplateEngine.template("/books/:book"), List.of(MediaType.APPLICATION_JSON_TYPE), handle())));
        assertTrue(e.getMessage().contains("across route template engines"), e.getMessage());

        UriRoute colonParent = assembly.addRoute("GET", HttpMethod.GET, ColonRouteTemplateEngine.template("/shelves/:shelf"), List.of(MediaType.APPLICATION_JSON_TYPE), handle());
        assertThrows(IllegalArgumentException.class, () -> colonParent.nest(() ->
            assembly.addRoute("GET", HttpMethod.GET, "/books/{book}", List.of(MediaType.APPLICATION_JSON_TYPE), handle())));
    }

    @Test
    void theLegacyRouteBuilderSupportsMicronautTemplatesOnly() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        assertThrows(IllegalArgumentException.class, () ->
            assembly.addRoute("GET", HttpMethod.GET, COLON_ITEM, List.of(MediaType.APPLICATION_JSON_TYPE), handle()));
    }

    @Test
    void aForeignRouteWithFewerPatternVariablesIsSelected() {
        RouteTemplate plain = ColonRouteTemplateEngine.template("/sel/:id");
        RouteTemplate pattern = ColonRouteTemplateEngine.template("/sel/:id(.+)");
        for (String contextPath : new String[] {null, "/ctx"}) {
            // declared routes without a context path, ordinary routes under it
            Router router = router(contextPath, routes -> {
                routes.handle(RouteDeclaration.of(HttpMethod.GET, pattern), (request, variables) -> HttpResponse.ok());
                routes.handle(RouteDeclaration.of(HttpMethod.GET, plain), (request, variables) -> HttpResponse.ok());
            });
            String prefix = contextPath == null ? "" : contextPath;
            RouteTemplate expected = contextPath == null ? plain : ColonRouteTemplateEngine.template(prefix + "/sel/:id");
            assertEquals(expected, findClosest(router, prefix + "/sel/1").getRouteInfo().getRouteTemplate(), prefix);
            List<UriRouteMatch<Object, Object>> all = router.findAllClosest(HttpRequest.GET(prefix + "/sel/1"));
            assertEquals(1, all.size(), prefix);
            assertEquals(expected, all.get(0).getRouteInfo().getRouteTemplate(), prefix);
        }
    }

    @Test
    void foreignRoutesThatTieOnThePatternVariableCountStayAmbiguous() {
        Router router = router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/tie/:id(.+)")), (request, variables) -> HttpResponse.ok());
            routes.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/tie/:id([0-9]+)")), (request, variables) -> HttpResponse.ok());
        });
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/tie/1")));
    }

    @Test
    void theColonEngineCountsItsPatternVariables() {
        assertEquals(0, RouteTemplateEngines.defaults().parse(COLON_ITEM).patternVariableCount());
        assertEquals(1, RouteTemplateEngines.defaults().parse(ColonRouteTemplateEngine.template("/a/:x/:y([0-9]+)")).patternVariableCount());
    }

    @Test
    void aCustomMethodIsDeclaredWithARouteTemplate() {
        RouteDeclaration declaration = RouteDeclaration.of("PROPFIND", COLON_ITEM);
        assertEquals(HttpMethod.CUSTOM, declaration.httpMethod());
        assertEquals("PROPFIND", declaration.httpMethodName());
        assertEquals(COLON_ITEM, declaration.template());
        // a standard method by its name is the standard method
        assertEquals("GET", RouteDeclaration.of("GET", COLON_ITEM).httpMethodName());
        assertEquals(HttpMethod.GET, RouteDeclaration.of("GET", COLON_ITEM).httpMethod());
        for (String contextPath : new String[] {null, "/ctx"}) {
            Router router = router(contextPath, routes ->
                routes.handle(declaration, (request, variables) -> HttpResponse.ok(variables.getString("id"))));
            String prefix = contextPath == null ? "" : contextPath;
            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, prefix + "/items/5", "PROPFIND"));
            assertNotNull(match, prefix);
            assertEquals("PROPFIND", match.getRouteInfo().getHttpMethodName(), prefix);
            assertEquals("5", match.getVariableValues().get("id"), prefix);
            assertNull(router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, prefix + "/items/5", "PROPPATCH")), prefix);
            assertNull(findClosestOrNull(router, prefix + "/items/5"), prefix);
        }
    }

    @SuppressWarnings("unchecked")
    private static MethodExecutionHandle<Object, Object> handle() {
        return (MethodExecutionHandle<Object, Object>) (MethodExecutionHandle<?, ?>) HandlerMethod.of((RequestHandler) (request, variables) -> HttpResponse.ok());
    }

    private static Router router(@Nullable String contextPath, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, contextPath, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private static UriRouteInfo<?, ?> route(Router router, HttpMethod method, RouteTemplate template) {
        return router.uriRoutes()
            .filter(r -> r.getHttpMethod() == method && r.getRouteTemplate().equals(template))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No " + method + " route " + template + " in " + router.uriRoutes().map(UriRouteInfo::getRouteTemplate).toList()));
    }

    private static UriRouteMatch<Object, Object> findClosest(Router router, String path) {
        UriRouteMatch<Object, Object> match = findClosestOrNull(router, path);
        assertNotNull(match, path);
        return match;
    }

    private static @Nullable UriRouteMatch<Object, Object> findClosestOrNull(Router router, String path) {
        return router.findClosest(HttpRequest.GET(path));
    }
}
