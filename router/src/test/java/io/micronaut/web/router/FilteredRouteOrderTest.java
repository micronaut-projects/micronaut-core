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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.filter.FilteredRouter;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import io.micronaut.web.router.version.RouteVersionFilter;
import io.micronaut.web.router.version.resolution.HeaderVersionResolver;
import io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A route match filter, here versioning, applies to the candidates before the ambiguity between
 * them is resolved, and the order of the routes still breaks the ties among the routes the filter
 * accepts, also in the table of a located target.
 */
class FilteredRouteOrderTest {

    private static final String VERSION = HeaderVersionResolverConfiguration.DEFAULT_HEADER_NAME;

    private final RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void theOrderBreaksTheTieAmongTheRoutesOfTheRequestedVersion() {
        Router router = versioned(routes -> {
            routes.GET("/items/{name}", handler("v1 late")).annotate(version("1")).order(5);
            routes.GET("/items/{name}", handler("v1 early")).annotate(version("1")).order(-5);
            // more specific, but another version: it does not hide the version 1 routes
            routes.GET("/items/special", handler("v2 special")).annotate(version("2")).order(-10);
        });

        assertEquals("v1 early", target(router, HttpRequest.GET("/items/special").header(VERSION, "1")));
        assertEquals("v1 early", target(router, HttpRequest.GET("/items/other").header(VERSION, "1")));
        assertEquals("v2 special", target(router, HttpRequest.GET("/items/special").header(VERSION, "2")));
        assertNull(router.findClosest(HttpRequest.GET("/items/other").header(VERSION, "2")));
    }

    @Test
    void routesOfTheRequestedVersionWithTheSameOrderStayAmbiguous() {
        Router router = versioned(routes -> {
            routes.GET("/items/{name}", handler("a")).annotate(version("1")).order(5);
            routes.GET("/items/{name}", handler("b")).annotate(version("1")).order(5);
            // a lower order, but another version
            routes.GET("/items/{name}", handler("c")).annotate(version("2")).order(-5);
        });

        DuplicateRouteException error = assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/items/x").header(VERSION, "1")));
        assertEquals(2, error.getUriRoutes().size());
        assertEquals("c", target(router, HttpRequest.GET("/items/x").header(VERSION, "2")));
    }

    @Test
    void theRoutesOfALocatedTableAreFilteredBeforeTheirAmbiguityIsResolved() {
        RouteTable items = tables.buildLocatedHttpRoutes(located -> {
            located.GET("/items/{name}", handler("v1 late")).annotate(version("1")).order(5);
            located.GET("/items/{name}", handler("v1 early")).annotate(version("1")).order(-5);
            located.GET("/items/special", handler("v2 special")).annotate(version("2"));
        });
        // the locator route has no version: it is not rejected for a request of a version
        Router router = versioned(routes -> routes.locate("/shops/{id}", (request, pathVariables) -> "shop", shop -> items));

        assertEquals("v1 early", target(router, HttpRequest.GET("/shops/1/items/special").header(VERSION, "1")));
        assertEquals("v2 special", target(router, HttpRequest.GET("/shops/1/items/special").header(VERSION, "2")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/1/items/other").header(VERSION, "2")));
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
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        RouteVersionFilter versions = new RouteVersionFilter(List.of(new HeaderVersionResolver(new HeaderVersionResolverConfiguration())), null, null, null);
        return new FilteredRouter(new DefaultRouter(List.of(), List.of(() -> assembly)), versions);
    }
}
