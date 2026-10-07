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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.filter.FilteredRouter;
import io.micronaut.web.router.version.RouteVersionFilter;
import io.micronaut.web.router.version.resolution.HeaderVersionResolver;
import io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route reached through a locator has the annotations of the groups of the locator routes,
 * outer first, which its own annotations override, like a route declared in those groups: the
 * route match filters, e.g. versioning, and the readers of the annotations of the match, e.g. a
 * security rule, see them.
 */
class LocatedRouteGroupAnnotationsTest {

    private static final String VERSION = HeaderVersionResolverConfiguration.DEFAULT_HEADER_NAME;

    @Test
    void aLocatedRouteHasTheAnnotationsOfTheGroupOfTheLocator() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = versioned(routes -> routes.path("/admin", admin -> {
            admin.annotate(version("1"));
            admin.annotate(Secured.class.getName(), secured -> secured.value("ROLE_ADMIN"));
            admin.GET("/plain", (request, pathVariables) -> HttpResponse.ok());
            admin.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items);
            admin.locateAsync("/async/{id}", (request, pathVariables) -> CompletableFuture.completedFuture("order"), target -> items);
        }));

        for (String path : List.of("/admin/plain", "/admin/orders/1/items", "/admin/async/1/items")) {
            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path).header(VERSION, "1"));
            assertNotNull(match, path);
            assertArrayEquals(new String[]{"ROLE_ADMIN"}, match.getAnnotationMetadata().stringValues(Secured.class), path);
            assertTrue(match.hasAnnotation(Secured.class), path);
            assertEquals("1", match.getExecutableMethod().stringValue(Version.class).orElseThrow(), path);
            assertNull(router.findClosest(HttpRequest.GET(path).header(VERSION, "2")), path);
        }
    }

    @Test
    void theAnnotationsOfNestedLocatorsAreLayeredAndTheLocatedRouteOverridesThem() {
        LocatedRoutes<Object> lines = TestLocatedRoutes.of(located -> {
            located.GET("/lines", (request, pathVariables) -> HttpResponse.ok());
            located.GET("/own")
                .annotate(version("3"))
                .annotate(Secured.class.getName(), secured -> secured.value("ROLE_OWNER"))
                .handle((request, pathVariables) -> HttpResponse.ok());
        });
        LocatedRoutes<?> orders = TestLocatedRoutes.of(located -> located.locate("/items/{item}", (target, request, pathVariables) -> "item", lines));
        Router router = versioned(routes -> routes.path("/shop", shop -> {
            shop.annotate(Secured.class.getName(), secured -> secured.value("ROLE_ADMIN"));
            shop.path("/v2", nested -> {
                nested.annotate(version("2"));
                nested.locate("/orders/{id}", (request, pathVariables) -> "order", target -> orders);
            });
        }));

        UriRouteMatch<Object, Object> inherited = router.findClosest(HttpRequest.GET("/shop/v2/orders/1/items/2/lines").header(VERSION, "2"));
        assertNotNull(inherited);
        AnnotationMetadata metadata = inherited.getAnnotationMetadata();
        assertArrayEquals(new String[]{"ROLE_ADMIN"}, metadata.stringValues(Secured.class));
        assertEquals("2", metadata.stringValue(Version.class).orElseThrow());
        assertEquals("1", inherited.getVariableValues().get("id"));
        assertNull(router.findClosest(HttpRequest.GET("/shop/v2/orders/1/items/2/lines").header(VERSION, "1")));

        UriRouteMatch<Object, Object> own = router.findClosest(HttpRequest.GET("/shop/v2/orders/1/items/2/own").header(VERSION, "3"));
        assertNotNull(own);
        assertEquals("ROLE_OWNER", own.getAnnotationMetadata().stringValue(Secured.class).orElseThrow());
        assertEquals("3", own.getAnnotationMetadata().stringValue(Version.class).orElseThrow());
        assertNull(router.findClosest(HttpRequest.GET("/shop/v2/orders/1/items/2/own").header(VERSION, "2")));
    }

    @Test
    void aLocatorWithoutGroupAnnotationsKeepsTheAnnotationsOfTheLocatedRoute() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            located.GET("/plain", (request, pathVariables) -> HttpResponse.ok());
            located.GET("/marked").annotate(Secured.class.getName(), secured -> secured.value("ROLE_USER"))
                .handle((request, pathVariables) -> HttpResponse.ok());
        });
        Router router = versioned(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));

        UriRouteMatch<Object, Object> plain = router.findClosest(HttpRequest.GET("/orders/1/plain"));
        assertNotNull(plain);
        assertTrue(plain.getAnnotationMetadata().isEmpty());
        UriRouteMatch<Object, Object> marked = router.findClosest(HttpRequest.GET("/orders/1/marked"));
        assertNotNull(marked);
        assertArrayEquals(new String[]{"ROLE_USER"}, marked.getAnnotationMetadata().stringValues(Secured.class));
    }

    private static AnnotationValue<Version> version(String version) {
        return AnnotationValue.builder(Version.class).value(version).build();
    }

    private static Router versioned(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        RouteVersionFilter versions = new RouteVersionFilter(List.of(new HeaderVersionResolver(new HeaderVersionResolverConfiguration())), null, null, null);
        return new FilteredRouter(new DefaultRouter(List.of(), List.of(() -> assembly)), versions);
    }

    /**
     * Read like the annotation of a security rule.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Secured {
        String[] value();
    }
}
