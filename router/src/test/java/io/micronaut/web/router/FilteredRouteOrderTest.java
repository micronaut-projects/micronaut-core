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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.filter.FilteredRouter;
import io.micronaut.web.router.version.RouteVersionFilter;
import io.micronaut.web.router.version.resolution.HeaderVersionResolver;
import io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A route match filter, here versioning, applies to the candidates before the ambiguity between
 * them is resolved, and the order of the routes still breaks the ties among the routes the filter
 * accepts, also in the table of a located target.
 */
class FilteredRouteOrderTest {

    private static final String VERSION = HeaderVersionResolverConfiguration.DEFAULT_HEADER_NAME;


    @Test
    void theOrderBreaksTheTieAmongTheRoutesOfTheRequestedVersion() {
        Router router = versioned(routes -> {
            routes.GET("/items/{name}").annotate(version("1")).order(5).handle(handler("v1 late"));
            routes.GET("/items/{name}").annotate(version("1")).order(-5).handle(handler("v1 early"));
            // more specific, but another version: it does not hide the version 1 routes
            routes.GET("/items/special").annotate(version("2")).order(-10).handle(handler("v2 special"));
        });

        assertEquals("v1 early", target(router, HttpRequest.GET("/items/special").header(VERSION, "1")));
        assertEquals("v1 early", target(router, HttpRequest.GET("/items/other").header(VERSION, "1")));
        assertEquals("v2 special", target(router, HttpRequest.GET("/items/special").header(VERSION, "2")));
        assertNull(router.findClosest(HttpRequest.GET("/items/other").header(VERSION, "2")));
    }

    @Test
    void routesOfTheRequestedVersionWithTheSameOrderStayAmbiguous() {
        Router router = versioned(routes -> {
            routes.GET("/items/{name}").annotate(version("1")).order(5).handle(handler("a"));
            routes.GET("/items/{name}").annotate(version("1")).order(5).handle(handler("b"));
            // a lower order, but another version
            routes.GET("/items/{name}").annotate(version("2")).order(-5).handle(handler("c"));
        });

        var versionOne = HttpRequest.GET("/items/x").header(VERSION, "1");
        DuplicateRouteException error = assertThrows(DuplicateRouteException.class, () -> router.findClosest(versionOne));
        assertEquals(2, error.getUriRoutes().size());
        assertEquals("c", target(router, HttpRequest.GET("/items/x").header(VERSION, "2")));
    }

    @Test
    void theRoutesOfALocatedTableAreFilteredBeforeTheirAmbiguityIsResolved() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            located.GET("/items/{name}").annotate(version("1")).order(5).handle(handler("v1 late"));
            located.GET("/items/{name}").annotate(version("1")).order(-5).handle(handler("v1 early"));
            located.GET("/items/special").annotate(version("2")).handle(handler("v2 special"));
        });
        // the locator route has no version: it is not rejected for a request of a version
        Router router = versioned(routes -> routes.locate("/shops/{id}", (request, pathVariables) -> "shop", shop -> items));

        assertEquals("v1 early", target(router, HttpRequest.GET("/shops/1/items/special").header(VERSION, "1")));
        assertEquals("v2 special", target(router, HttpRequest.GET("/shops/1/items/special").header(VERSION, "2")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/1/items/other").header(VERSION, "2")));
    }

    @Test
    void filteredResolutionCollectsTopLevelCandidatesOnce() {
        AtomicInteger predicates = new AtomicInteger();
        AtomicInteger locators = new AtomicInteger();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located ->
            located.GET("/items/{name}").annotate(version("1")).produces(MediaType.TEXT_PLAIN_TYPE).handle(handler("located")));
        Router router = versioned(routes -> {
            routes.GET("/shops/{id}/items/special").where(request -> {
                predicates.incrementAndGet();
                return true;
            }).annotate(version("2")).handle(handler("rejected"));
            routes.locate("/shops/{id}", (request, variables) -> {
                locators.incrementAndGet();
                return "shop";
            }, target -> items);
        });

        for (int i = 0; i < 2; i++) {
            var request = HttpRequest.GET("/shops/1/items/special").header(VERSION, "1").accept(MediaType.TEXT_PLAIN_TYPE);
            var match = router.resolve(request).matchOrNull();
            assertNotNull(match);
            assertEquals("located", ((Named) ((HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name());
            assertEquals(Map.of("id", "1", "name", "special"), match.getVariableValues());
            assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), match.getRouteInfo().getProduces());
            assertEquals(i + 1, locators.get());
            assertEquals(i + 1, predicates.get(), "only one top-level matching pass per resolution");
        }
    }

    @Test
    void locatedBodyConditionsKeepLessSpecificTopLevelCandidates() {
        AtomicInteger predicates = new AtomicInteger();
        AtomicInteger specific = new AtomicInteger();
        AtomicInteger fallback = new AtomicInteger();
        LocatedRoutes<?> special = TestLocatedRoutes.of(located ->
            located.POST("/special").annotate(version("1")).where(RequestCondition.bodyText("express"::equals)).consumesAll().handle(handler("special")));
        LocatedRoutes<?> general = TestLocatedRoutes.of(located ->
            located.POST("/items/{name}").annotate(version("1")).consumesAll().handle(handler("fallback")));
        Router router = versioned(routes -> {
            routes.POST("/shops/{id}/items/special").consumesAll().annotate(version("2")).where(request -> {
                predicates.incrementAndGet();
                return true;
            }).handle(handler("rejected"));
            routes.locate("/shops/{id}/items", (request, variables) -> {
                specific.incrementAndGet();
                return "specific";
            }, target -> special);
            routes.locate("/shops/{id}", (request, variables) -> {
                fallback.incrementAndGet();
                return "fallback";
            }, target -> general);
        });

        var request = HttpRequest.POST("/shops/1/items/special", "ordinary").contentType(MediaType.TEXT_PLAIN_TYPE).header(VERSION, "1");
        RouteResolution.Pending<Object, Object> resolution = assertInstanceOf(RouteResolution.Pending.class, router.resolve(request));
        var pending = resolution.pending();
        assertEquals(2, pending.getRanks().size());
        var ranks = pending.getRanks();
        var match = pending.choose(request, ranks.get(1)).matchOrNull();
        assertNotNull(match);
        assertEquals("fallback", ((Named) ((HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name());
        assertEquals(Map.of("id", "1", "name", "special"), match.getVariableValues());
        assertEquals(1, specific.get());
        assertEquals(1, fallback.get());
        assertEquals(1, predicates.get(), "body fall-through must not recollect top-level candidates");
    }

    @Test
    void filteredResolutionLeavesADominatedLocatorUnvisitedAndKeepsAmbiguity() {
        AtomicInteger locators = new AtomicInteger();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/special").handle(handler("located")));
        Router router = versioned(routes -> {
            routes.GET("/items/special").annotate(version("1")).handle(handler("static"));
            routes.locate("/items", (request, variables) -> {
                locators.incrementAndGet();
                return "items";
            }, target -> items);
        });
        assertNotNull(router.resolve(HttpRequest.GET("/items/special").header(VERSION, "1")).matchOrNull());
        assertEquals(0, locators.get());

        Router ambiguous = versioned(routes -> {
            routes.locate("/items", (request, variables) -> "items", target -> items);
            routes.GET("/items/special").annotate(version("1")).handle(handler("first"));
            routes.GET("/items/special").annotate(version("1")).handle(handler("second"));
        });
        assertThrows(DuplicateRouteException.class, () -> ambiguous.resolve(HttpRequest.GET("/items/special").header(VERSION, "1")));
    }

    @Test
    void filteredLocatedRoutesKeepMethodAndHeadPreferences() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            located.GET("/implicit").handle(handler("get"));
            located.GET("/explicit").handle(handler("implicit head"));
            located.route(HttpMethod.HEAD, "/explicit").handle(handler("explicit head"));
            located.any("/custom").handle(handler("any"));
            located.route("REPORT", "/custom").handle(handler("report"));
        });
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        var routes = new DefaultHttpRouteBuilder(assembly);
        routes.locate("/items/{id}", (request, variables) -> "items", target -> items);
        routes.close();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));

        List<HttpRequest<?>> requests = List.of(
            HttpRequest.HEAD("/items/1/implicit"), HttpRequest.HEAD("/items/1/explicit"),
            HttpRequest.create(HttpMethod.CUSTOM, "/items/1/custom", "REPORT"),
            HttpRequest.create(HttpMethod.CUSTOM, "/items/1/custom", "PURGE"));
        List<String> expected = List.of("get", "explicit head", "report", "any");
        for (int i = 0; i < requests.size(); i++) {
            var match = router.resolve(requests.get(i), candidate -> true).matchOrNull();
            assertNotNull(match);
            assertEquals(expected.get(i), ((Named) ((HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name());
        }
    }

    private static AnnotationValue<Version> version(String version) {
        return AnnotationValue.builder(Version.class).value(version).build();
    }

    private static String target(Router router, MutableHttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
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
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        RouteVersionFilter versions = new RouteVersionFilter(List.of(new HeaderVersionResolver(new HeaderVersionResolverConfiguration())), null, null, null);
        return new FilteredRouter(new DefaultRouter(List.of(), List.of(() -> assembly)), versions);
    }
}
