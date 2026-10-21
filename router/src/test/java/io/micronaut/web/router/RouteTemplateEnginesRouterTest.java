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
import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriRouteTemplate;
import io.micronaut.http.uri.UriTypeMatchTemplate;
import io.micronaut.http.uri.RouteTemplateVariable;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.HttpRouteSpec;
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
            routes.GET("/items/{id:3}").handle((request, variables) -> HttpResponse.ok("micronaut"));
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/{id:3}")).handle((request, variables) -> HttpResponse.ok("colon"));
        });
        UriRouteInfo<?, ?> micronaut = route(router, HttpMethod.GET, UriRouteTemplate.of("/items/{id:3}"));
        UriRouteInfo<?, ?> colon = route(router, HttpMethod.GET, ColonRouteTemplateEngine.template("/items/{id:3}"));

        // {id:3} is a variable of at most three characters for Micronaut
        assertNotNull(micronaut.tryMatch("/items/abc"));
        assertEquals("abc", micronaut.tryMatch("/items/abc").getVariableValues().get("id"));
        assertNull(micronaut.tryMatch("/items/abcd"));
        // and literal text for the colon engine
        assertNull(colon.tryMatch("/items/abc"));
        assertNotNull(colon.tryMatch("/items/{id:3}"));

        assertEquals(UriRouteTemplate.of("/items/{id:3}"), findClosest(router, "/items/abc").getRouteInfo().getRouteTemplate());
        // both GET routes have their own implicit HEAD route
        assertEquals(2, router.uriRoutes().filter(r -> r.getHttpMethod() == HttpMethod.HEAD).count());
    }

    @Test
    void aForeignRouteMatchesAndCaptures() {
        Router router = router(null, routes ->
            routes.route(HttpMethod.GET, COLON_ITEM).handle((request, variables) -> HttpResponse.ok(variables.getString("id"))));
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
            routes.route(HttpMethod.GET, COLON_ITEM).handle((request, variables) -> HttpResponse.ok()));
        UriRouteInfo<?, ?> route = route(router, HttpMethod.GET, COLON_ITEM);
        assertInstanceOf(DefaultUrlRouteInfo.class, route);
        assertEquals(COLON_ITEM, route.getRouteTemplate());
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, route::getUriMatchTemplate);
        assertTrue(e.getMessage().contains(ColonRouteTemplateEngine.ID), e.getMessage());
    }

    @Test
    void theMicronautPolicySelectsAcrossEngines() {
        Router router = router(null, routes -> {
            routes.route(HttpMethod.GET, COLON_ITEM).handle((request, variables) -> HttpResponse.ok());
            routes.GET("/items/special", (request, variables) -> HttpResponse.ok());
        });
        // more literal text wins
        assertEquals(UriRouteTemplate.of("/items/special"), findClosest(router, "/items/special").getRouteInfo().getRouteTemplate());
        assertEquals(COLON_ITEM, findClosest(router, "/items/5").getRouteInfo().getRouteTemplate());
    }

    @Test
    void aForeignRouteIsMountedLiterallyUnderTheContextPath() {
        Router router = router("/:ctx", routes ->
            routes.route(HttpMethod.GET, COLON_ITEM).handle((request, variables) -> HttpResponse.ok()));
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
            routes.GET("/items/{id}").handle((request, variables) -> HttpResponse.ok()));
        UriRouteInfo<?, ?> route = route(router, HttpMethod.GET, UriRouteTemplate.of("/ctx/items/{id}"));
        assertEquals("/ctx/items/{id}", route.getUriMatchTemplate().toString());
        assertNotNull(route.tryMatch("/ctx/items/5"));
    }

    @Test
    void implicitHeadRoutesAreKeyedByEngineAndTemplate() {
        Router router = router(null, routes -> {
            // an explicit HEAD route with the same text, but of the Micronaut engine
            routes.route(HttpMethod.HEAD, "/items/:id").handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.GET, COLON_ITEM).handle((request, variables) -> HttpResponse.ok());
            // an explicit HEAD route of the same engine and template
            routes.route(HttpMethod.HEAD, ColonRouteTemplateEngine.template("/books/:id")).handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/books/:id")).handle((request, variables) -> HttpResponse.ok());
        });
        List<UriRouteInfo<?, ?>> heads = router.uriRoutes().filter(r -> r.getHttpMethod() == HttpMethod.HEAD).toList();
        assertEquals(4, heads.size());
        assertTrue(heads.stream().anyMatch(r -> r.isImplicitHead() && r.getRouteTemplate().equals(COLON_ITEM)));
        // the implicit HEAD route of the same engine and template is kept, the explicit one wins at match time
        assertTrue(heads.stream().anyMatch(r -> r.isImplicitHead() && r.getRouteTemplate().expression().equals("/books/:id")));
        UriRouteMatch<Object, Object> head = router.findClosest(HttpRequest.HEAD("/books/5"));
        assertNotNull(head);
        assertFalse(head.getRouteInfo().isImplicitHead());
    }

    @Test
    void aTemplateDescribedIncorrectlyFailsWhenTheRouteIsDeclared() {
        RouteTemplate template = new NegativeLengthTemplate(COLON_ITEM);
        // declaring the pending route does not check the template, its terminal does
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> router(null, routes -> {
            HttpRouteSpec route = routes.route(HttpMethod.GET, template);
            route.handle((request, variables) -> HttpResponse.ok());
        }));
        assertTrue(e.getMessage().contains("a negative raw length"), e.getMessage());
        assertTrue(e.getMessage().contains("NegativeLengthTemplate"), e.getMessage());
    }

    /**
     * A template whose raw length is negative: the router rejects it.
     *
     * @param delegate The template it describes otherwise
     */
    private record NegativeLengthTemplate(RouteTemplate delegate) implements RouteTemplate {
        @Override
        public String expression() {
            return delegate.expression();
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            return delegate.variables();
        }

        @Override
        public @Nullable RouteCaptures match(String path) {
            return delegate.match(path);
        }

        @Override
        public RouteTemplate mount(String prefix) {
            return new NegativeLengthTemplate(delegate.mount(prefix));
        }

        @Override
        public String requiredPrefix() {
            return delegate.requiredPrefix();
        }

        @Override
        public int rawLength() {
            return -1;
        }

        @Override
        public int pathVariableCount() {
            return delegate.pathVariableCount();
        }

        @Override
        public int patternVariableCount() {
            return delegate.patternVariableCount();
        }

    }

    @Test
    void onlyUriTemplatesAreNested() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        UriRoute parent = assembly.addRoute("GET", HttpMethod.GET, ColonRouteTemplateEngine.template("/shelves/:shelf"), List.of(MediaType.APPLICATION_JSON_TYPE), handle());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parent.nest(() ->
            assembly.addRoute("GET", HttpMethod.GET, ColonRouteTemplateEngine.template("/books/:book"), List.of(MediaType.APPLICATION_JSON_TYPE), handle())));
        assertTrue(e.getMessage().contains("only URI templates are nested"), e.getMessage());

        UriRoute uriParent = assembly.addRoute("GET", HttpMethod.GET, UriRouteTemplate.of("/racks/{rack}"), List.of(MediaType.APPLICATION_JSON_TYPE), handle());
        uriParent.nest(() -> assembly.addRoute("GET", HttpMethod.GET, UriRouteTemplate.of("/books/{book}"), List.of(MediaType.APPLICATION_JSON_TYPE), handle()));
        assertEquals(UriRouteTemplate.of("/racks/{rack}/books/{book}"), assembly.uriRoutes().get(assembly.uriRoutes().size() - 1).getRouteTemplate());
    }

    @Test
    void aTemplateOfASubclassOfUriMatchTemplateIsNotNested() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        UriRoute parent = assembly.addRoute("GET", HttpMethod.GET, UriRouteTemplate.of("/parent"), List.of(MediaType.APPLICATION_JSON_TYPE), handle());
        // nested, only its expression would be kept: /parent/items/abc would match
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parent.nest(() ->
            assembly.addRoute("GET", HttpMethod.GET, UriRouteTemplate.of(new UriTypeMatchTemplate("/items/{id}", Integer.class)),
                List.of(MediaType.APPLICATION_JSON_TYPE), handle())));
        assertTrue(e.getMessage().contains("of a subclass of UriMatchTemplate cannot be nested"), e.getMessage());
    }

    @Test
    void nestingAcrossEnginesIsRejected() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        UriRoute micronautParent = assembly.addRoute("GET", HttpMethod.GET, "/shelves/{shelf}", List.of(MediaType.APPLICATION_JSON_TYPE), handle());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> micronautParent.nest(() ->
            assembly.addRoute("GET", HttpMethod.GET, ColonRouteTemplateEngine.template("/books/:book"), List.of(MediaType.APPLICATION_JSON_TYPE), handle())));
        assertTrue(e.getMessage().contains("only URI templates are nested"), e.getMessage());

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
    void foreignRoutesWithAPlainAndAPatternVariableAreAmbiguous() {
        RouteTemplate plain = ColonRouteTemplateEngine.template("/sel/:id");
        RouteTemplate pattern = ColonRouteTemplateEngine.template("/sel/:id(.+)");
        for (String contextPath : new String[] {null, "/ctx"}) {
            // declared routes without a context path, ordinary routes under it
            Router router = router(contextPath, routes -> {
                routes.route(HttpMethod.GET, pattern).handle((request, variables) -> HttpResponse.ok());
                routes.route(HttpMethod.GET, plain).handle((request, variables) -> HttpResponse.ok());
            });
            String prefix = contextPath == null ? "" : contextPath;
            // as for Micronaut routes, the policy has no key for variables with a regular expression
            assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET(prefix + "/sel/1")), prefix);
            List<UriRouteMatch<Object, Object>> all = router.findAllClosest(HttpRequest.GET(prefix + "/sel/1"));
            assertEquals(2, all.size(), prefix);
        }
    }

    @Test
    void foreignRoutesThatTieOnThePatternVariableCountStayAmbiguous() {
        Router router = router(null, routes -> {
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/tie/:id(.+)")).handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/tie/:id([0-9]+)")).handle((request, variables) -> HttpResponse.ok());
        });
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/tie/1")));
    }

    @Test
    void theColonEngineCountsItsPatternVariables() {
        assertEquals(0, COLON_ITEM.patternVariableCount());
        assertEquals(1, ColonRouteTemplateEngine.template("/a/:x/:y([0-9]+)").patternVariableCount());
    }

    @Test
    void aCustomMethodIsDeclaredWithARouteTemplate() {
        // a standard method by its name is the standard method
        UriRouteMatch<Object, Object> get = router(null, routes -> routes.route("get", COLON_ITEM).handle((request, variables) -> HttpResponse.ok()))
            .findClosest(HttpRequest.GET("/items/5"));
        assertNotNull(get);
        assertEquals(HttpMethod.GET, get.getRouteInfo().getHttpMethod());
        assertEquals("GET", get.getRouteInfo().getHttpMethodName());
        assertEquals(COLON_ITEM, get.getRouteInfo().getRouteTemplate());
        for (String contextPath : new String[] {null, "/ctx"}) {
            Router router = router(contextPath, routes ->
                routes.route("PROPFIND", COLON_ITEM).handle((request, variables) -> HttpResponse.ok(variables.getString("id"))));
            String prefix = contextPath == null ? "" : contextPath;
            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, prefix + "/items/5", "PROPFIND"));
            assertNotNull(match, prefix);
            assertEquals(HttpMethod.CUSTOM, match.getRouteInfo().getHttpMethod(), prefix);
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
