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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A route selector that ranks the routes by the path first, as JAX-RS does: it is given the
 * routes of every media type, rejects a request with {@code 406} or {@code 415}, and a locator
 * route that a route of another method is more specific than does not locate.
 */
class RouteMatchSelectorPathFirstTest {

    private final TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes ->
        routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));

    private final Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
        routes.group(g -> {
            g.templates(PathFirstColonRouteTemplateEngine.INSTANCE).selectedBy(PathFirstColonRouteTemplateEngine.INSTANCE);
            // the most specific template is chosen before the accepted types
            g.GET("/r/fixed").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, variables) -> HttpResponse.ok());
            g.GET("/r/:x").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, variables) -> HttpResponse.ok());
            // and before the content type
            g.POST("/c/fixed").consumes(MediaType.APPLICATION_JSON_TYPE).handle((request, variables) -> HttpResponse.ok());
            g.POST("/c/:x").consumes(MediaType.TEXT_PLAIN_TYPE).handle((request, variables) -> HttpResponse.ok());
            // a resource method more specific than a locator route
            g.GET("/g/sub").handle((request, variables) -> HttpResponse.ok());
            g.locate("/g/:id", (request, variables) -> "located", target -> items);
        });
        // of the selector and of the Micronaut policy: the types decide first, as for other routes
        routes.route(HttpMethod.GET, PathFirstColonRouteTemplateEngine.template("/m/fixed")).selectedBy(PathFirstColonRouteTemplateEngine.INSTANCE)
            .produces(MediaType.APPLICATION_JSON_TYPE).handle((request, variables) -> HttpResponse.ok());
        routes.GET("/m/{x}").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, variables) -> HttpResponse.ok());
    });

    @Test
    void theMostSpecificTemplateIsChosenBeforeTheAcceptedTypes() {
        assertEquals("/r/fixed", template(router.findClosest(get("/r/fixed", "application/json"))));
        assertEquals("/r/:x", template(router.findClosest(get("/r/other", "text/plain"))));
        // JAX-RS: /r/fixed is chosen, and it cannot produce text/plain
        MutableHttpRequest<?> request = get("/r/fixed", "text/plain");
        assertNull(router.findClosest(request));
        assertEquals(List.of(), router.findAllClosest(request));
        UriRouteMatch<Object, Object> rejection = only(ofMethod(router.findAny(request), request.getMethodName()));
        assertEquals("/r/fixed", template(rejection));
        assertEquals(HttpStatus.NOT_ACCEPTABLE, ((DefaultUriRouteMatch<?, ?>) rejection).rejectionStatus());
    }

    @Test
    void theMostSpecificTemplateIsChosenBeforeTheContentType() {
        assertEquals("/c/fixed", template(router.findClosest(post("/c/fixed", "application/json"))));
        assertEquals("/c/:x", template(router.findClosest(post("/c/other", "text/plain"))));
        MutableHttpRequest<?> request = post("/c/fixed", "text/plain");
        assertNull(router.findClosest(request));
        UriRouteMatch<Object, Object> rejection = only(ofMethod(router.findAny(request), request.getMethodName()));
        assertEquals("/c/fixed", template(rejection));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ((DefaultUriRouteMatch<?, ?>) rejection).rejectionStatus());
    }

    @Test
    void theTypesDecideFirstWhenTheRoutesHaveDifferentSelectors() {
        UriRouteMatch<Object, Object> match = router.findClosest(get("/m/fixed", "text/plain"));
        assertNotNull(match);
        assertEquals("/m/{x}", template(match));
        assertEquals("/m/fixed", template(router.findClosest(get("/m/fixed", "application/json"))));
    }

    @Test
    void aLocatorRouteLessSpecificThanARouteOfAnotherMethodDoesNotLocate() {
        assertEquals("/g/sub", template(router.findClosest(HttpRequest.GET("/g/sub"))));
        // the locator route accepts OPTIONS, but GET /g/sub is more specific: no route, and GET is allowed
        MutableHttpRequest<?> options = HttpRequest.OPTIONS("/g/sub");
        assertNull(router.findClosest(options));
        assertEquals(Set.of("GET", "HEAD"), methods(router.findAny(options)));
        MutableHttpRequest<?> post = HttpRequest.POST("/g/sub", "");
        assertNull(router.findClosest(post));
        assertEquals(Set.of("GET", "HEAD"), methods(router.findAny(post)));
        // a path the route does not match still locates
        UriRouteMatch<Object, Object> located = router.findClosest(HttpRequest.GET("/g/5/items/3"));
        assertNotNull(located);
        assertEquals("5", located.getVariableValues().get("id"));
        assertEquals("3", located.getVariableValues().get("item"));
    }

    private static MutableHttpRequest<?> get(String path, String accept) {
        return HttpRequest.GET(path).header(HttpHeaders.ACCEPT, accept);
    }

    private static MutableHttpRequest<?> post(String path, String contentType) {
        return HttpRequest.POST(path, "x").header(HttpHeaders.CONTENT_TYPE, contentType);
    }

    private static <T, R> UriRouteMatch<T, R> only(List<UriRouteMatch<T, R>> matches) {
        assertEquals(1, matches.size(), matches.toString());
        return matches.get(0);
    }

    private static <T, R> List<UriRouteMatch<T, R>> ofMethod(List<UriRouteMatch<T, R>> matches, String method) {
        return matches.stream().filter(match -> match.getRouteInfo().getHttpMethodName().equals(method)).toList();
    }

    private static Set<String> methods(List<UriRouteMatch<Object, Object>> matches) {
        return matches.stream().map(match -> match.getRouteInfo().getHttpMethodName()).collect(Collectors.toSet());
    }

    private static @Nullable String template(@Nullable UriRouteMatch<?, ?> match) {
        return match == null ? null : match.getRouteInfo().getRouteTemplate().expression();
    }
}
