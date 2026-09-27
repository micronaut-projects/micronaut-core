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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.ValueMatcher;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.micronaut.web.router.builder.ValueMatcher.oneOf;
import static io.micronaut.web.router.builder.ValueMatcher.present;
import static io.micronaut.web.router.builder.ValueMatcher.regex;
import static io.micronaut.web.router.builder.ValueMatcher.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The constraints on path variables with a {@link ValueMatcher}.
 */
class RouteConstrainMatcherTest {

    @Test
    void aVariableConstrainedWithAMatcher() {
        Router router = router(routes -> routes.GET("/files/{name}", handler("file"))
            .constrain("name", startsWith(".").negate()));

        assertEquals("file", target(router, HttpRequest.GET("/files/readme")));
        assertNull(router.findClosest(HttpRequest.GET("/files/.hidden")));
    }

    @Test
    void aMatcherIgnoringCase() {
        Router router = router(routes -> routes.path("/shops/{shop}", shop -> {
            shop.constrain("shop", oneOf("north", "south").ignoringCase());
            shop.GET("/stock", handler("stock"));
        }));

        assertEquals("stock", target(router, HttpRequest.GET("/shops/NORTH/stock")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/west/stock")));
    }

    @Test
    void aMissingVariableIsAnAbsentValue() {
        Router router = router(routes -> {
            routes.GET("/files{/name}", handler("named")).constrain("name", present());
            routes.GET("/archive{/name}", handler("unnamed")).constrain("name", present().negate());
        });

        assertEquals("named", target(router, HttpRequest.GET("/files/readme")));
        assertNull(router.findClosest(HttpRequest.GET("/files")), "only a negated matcher matches an absent value");
        assertEquals("unnamed", target(router, HttpRequest.GET("/archive")));
        assertNull(router.findClosest(HttpRequest.GET("/archive/x")));
    }

    @Test
    void severalVariablesConstrainedWithAMap() {
        Map<String, ValueMatcher> matchers = new LinkedHashMap<>();
        matchers.put("shop", oneOf("north", "south"));
        matchers.put("item", regex("\\d{1,3}"));
        Router router = router(routes -> routes.GET("/shops/{shop}/items/{item}", handler("item")).constrain(matchers));
        matchers.clear(); // copied when declared

        assertEquals("item", target(router, HttpRequest.GET("/shops/north/items/42")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/west/items/42")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/north/items/4200")));
    }

    @Test
    void theCollectionFormIsAOneOfMatcher() {
        Router router = router(routes -> routes.GET("/shops/{shop}", handler("shop")).constrain("shop", List.of("north", "south")));

        assertEquals("shop", target(router, HttpRequest.GET("/shops/south")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/North")), "case-sensitive");
    }

    private static String target(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return ((Named) ((io.micronaut.web.router.builder.HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name();
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

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
