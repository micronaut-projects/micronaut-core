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
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriRouteTemplate;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order of specificity a kind of templates declares, see
 * {@link io.micronaut.http.uri.RouteTemplateKind#comparator()}.
 */
class RouteTemplateEngineOrderTest {

    private static final RouteTemplate PLAIN = OrderedColonRouteTemplateEngine.template("/sel/:id");
    private static final RouteTemplate PATTERN = OrderedColonRouteTemplateEngine.template("/sel/:id(.+)");

    @Test
    void aSelectorDeclaresItsOrder() {
        assertTrue(OrderedColonRouteTemplateEngine.INSTANCE.order().isPresent());
        assertTrue(AmbiguousSelectingEngine.INSTANCE.order().isEmpty());
    }

    @Test
    void theOrderOfTheEngineSelectsBetweenItsRoutes() {
        for (String contextPath : new String[] {null, "/ctx"}) {
            // declared routes without a context path, ordinary routes under it
            Router router = router(contextPath, routes -> {
                routes.route(HttpMethod.GET, PLAIN).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
                routes.route(HttpMethod.GET, PATTERN).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
            });
            String prefix = contextPath == null ? "" : contextPath;
            // the engine prefers more variables with a regular expression, the Micronaut policy fewer
            RouteTemplate expected = OrderedColonRouteTemplateEngine.template(prefix + "/sel/:id(.+)");
            assertEquals(expected, findClosest(router, prefix + "/sel/1").getRouteInfo().getRouteTemplate(), prefix);
            List<UriRouteMatch<Object, Object>> all = router.findAllClosest(HttpRequest.GET(prefix + "/sel/1"));
            assertEquals(1, all.size(), prefix);
            assertEquals(expected, all.get(0).getRouteInfo().getRouteTemplate(), prefix);
        }
    }

    @Test
    void theSameRoutesOfAnEngineWithoutAnOrderKeepTheMicronautOrder() {
        Router router = router(null, routes -> {
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/sel/:id(.+)")).handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/sel/:id")).handle((request, variables) -> HttpResponse.ok());
        });
        // same literal length and variable count: as for Micronaut routes, a plain and a constrained variable are ambiguous
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/sel/1")));
        assertEquals(2, router.findAllClosest(HttpRequest.GET("/sel/1")).size());
    }

    @Test
    void routesTheEngineOrdersEquallyAreAmbiguous() {
        Router router = router(null, routes -> {
            routes.route(HttpMethod.GET, OrderedColonRouteTemplateEngine.template("/tie/:id(.+)")).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.GET, OrderedColonRouteTemplateEngine.template("/tie/:id([0-9]+)")).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
        });
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/tie/1")));
        assertEquals(2, router.findAllClosest(HttpRequest.GET("/tie/1")).size());
    }

    @Test
    void moreLiteralTextStillWinsFirst() {
        Router router = router(null, routes -> {
            routes.route(HttpMethod.GET, PATTERN).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.GET, OrderedColonRouteTemplateEngine.template("/sel/special")).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
        });
        assertEquals(OrderedColonRouteTemplateEngine.template("/sel/special"), findClosest(router, "/sel/special").getRouteInfo().getRouteTemplate());
        assertEquals(PATTERN, findClosest(router, "/sel/other").getRouteInfo().getRouteTemplate());
    }

    @Test
    void aRouteOfTheEngineAndAMicronautRouteAreOrderedByTheMicronautPolicy() {
        Router router = router(null, routes -> {
            routes.route(HttpMethod.GET, PATTERN).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
            routes.GET("/sel/{id}", (request, variables) -> HttpResponse.ok());
        });
        // same literal length and variable count: ambiguous, the Micronaut policy has no third key
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/sel/1")));
        assertEquals(2, router.findAllClosest(HttpRequest.GET("/sel/1")).size());
    }

    @Test
    void mixedEnginesAmongOrderedRoutesUseTheMicronautPolicy() {
        Router router = router(null, routes -> {
            routes.route(HttpMethod.GET, PLAIN).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
            routes.route(HttpMethod.GET, PATTERN).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).handle((request, variables) -> HttpResponse.ok());
            routes.GET("/sel/{id:.+}", (request, variables) -> HttpResponse.ok());
        });
        // three candidates of two engines: the Micronaut policy selects, and they are equally specific
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/sel/1")));
        assertEquals(3, router.findAllClosest(HttpRequest.GET("/sel/1")).size());
    }

    @Test
    void micronautRoutesAreUnaffected() {
        Router router = router(null, routes -> {
            routes.GET("/sel/{id:.+}", (request, variables) -> HttpResponse.ok());
            routes.GET("/sel/{id}", (request, variables) -> HttpResponse.ok());
        });
        // a plain and a constrained variable at the same position are ambiguous
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/sel/1")));
        assertFalse(router.uriRoutes().anyMatch(r -> !(r.getRouteTemplate() instanceof UriRouteTemplate)));
    }

    static Router router(@Nullable String contextPath, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, contextPath, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private static UriRouteMatch<Object, Object> findClosest(Router router, String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return match;
    }
}
