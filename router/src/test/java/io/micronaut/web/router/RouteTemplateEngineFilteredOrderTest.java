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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.filter.FilteredRouter;
import io.micronaut.web.router.version.RouteVersionFilter;
import io.micronaut.web.router.version.resolution.HeaderVersionResolver;
import io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Versioned routes of route template engines with an order and a route selector: the version
 * filter applies to the candidates before the order of the engine, its route selector and the
 * order of the routes decide among them. The ambiguity resolved for the candidates of any router,
 * see {@link Router#findAllClosest(HttpRequest, java.util.function.Predicate)}, follows the order of
 * the engine too.
 */
class RouteTemplateEngineFilteredOrderTest {

    private static final String VERSION = HeaderVersionResolverConfiguration.DEFAULT_HEADER_NAME;

    @Test
    void theOrderOfTheEngineSelectsAmongTheRoutesOfTheRequestedVersion() {
        Router router = versioned(routes -> {
            // the engine prefers the pattern variable, the Micronaut policy the plain one
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/sel/:id(.+)"), "v1 pattern", "1");
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/sel/:id"), "v1 plain", "1");
            // more literal text, but another version: it does not hide the version 1 routes
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/sel/special"), "v2 special", "2");
        });

        assertEquals("v1 pattern", target(router, HttpRequest.GET("/sel/special").header(VERSION, "1")));
        assertEquals("v1 pattern", target(router, HttpRequest.GET("/sel/1").header(VERSION, "1")));
        assertEquals("v2 special", target(router, HttpRequest.GET("/sel/special").header(VERSION, "2")));
        assertNull(router.findClosest(HttpRequest.GET("/sel/1").header(VERSION, "2")));
    }

    @Test
    void theOrderOfTheRoutesBreaksATieOfTheEngineAmongTheRoutesOfTheRequestedVersion() {
        Router router = versioned(routes -> {
            // equally specific for the engine
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/tie/:id(.+)"), "v1 late", "1").order(5);
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/tie/:id([0-9]+)"), "v1 early", "1").order(-5);
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/tie/1"), "v2 literal", "2").order(-10);
        });

        assertEquals("v1 early", target(router, HttpRequest.GET("/tie/1").header(VERSION, "1")));
        assertEquals(2, router.findAllClosest(HttpRequest.GET("/tie/1").header(VERSION, "1")).size());
        assertEquals("v2 literal", target(router, HttpRequest.GET("/tie/1").header(VERSION, "2")));
    }

    @Test
    void theRouteSelectorOfTheEngineSelectsAmongTheRoutesOfTheRequestedVersion() {
        Router router = versioned(routes -> {
            // the selector takes the first of the most specific routes
            engineRoute(routes, SelectingColonRouteTemplateEngine.template("/sv/:id"), "v2", "2");
            engineRoute(routes, SelectingColonRouteTemplateEngine.template("/sv/:id"), "v1", "1");
        });

        assertEquals("v1", target(router, HttpRequest.GET("/sv/1").header(VERSION, "1")));
        assertEquals(1, SelectingColonRouteTemplateEngine.LAST_CANDIDATES.get().size());
        assertEquals("v2", target(router, HttpRequest.GET("/sv/1").header(VERSION, "2")));
    }

    @Test
    void theAmbiguityResolvedForTheCandidatesOfAnyRouterFollowsTheOrderOfTheEngine() {
        Router table = RouteTemplateEngineOrderTest.router(null, routes -> {
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/sel/:id(.+)"), "pattern", "1");
            engineRoute(routes, OrderedColonRouteTemplateEngine.template("/sel/:id"), "plain", "1");
        });
        HttpRequest<?> request = HttpRequest.GET("/sel/1");

        List<UriRouteMatch<Object, Object>> closest = DefaultRouter.resolveAmbiguity(request, table.<Object, Object>find(request).toList());
        assertEquals(1, closest.size());
        assertEquals("pattern", name(closest.get(0)));
    }

    private static HttpRouteSpec engineRoute(HttpRouteBuilder routes, RouteTemplate template, String name, String version) {
        return routes.handle(RouteDeclaration.of(HttpMethod.GET, template), handler(name))
            .annotate(AnnotationValue.builder(Version.class).value(version).build());
    }

    private static String target(Router router, MutableHttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return name(match);
    }

    private static String name(UriRouteMatch<?, ?> match) {
        return ((Named) ((HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name();
    }

    private static RequestHandler handler(String name) {
        return new Named(name);
    }

    private record Named(String name) implements RequestHandler {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok(name);
        }
    }

    private static Router versioned(Consumer<HttpRouteBuilder> routes) {
        RouteVersionFilter versions = new RouteVersionFilter(List.of(new HeaderVersionResolver(new HeaderVersionResolverConfiguration())), null, null, null);
        return new FilteredRouter(RouteTemplateEngineOrderTest.router(null, routes), versions);
    }
}
