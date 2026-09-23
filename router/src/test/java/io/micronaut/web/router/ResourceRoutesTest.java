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
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.ResourceHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The shape of the routes of {@link HttpRouteBuilder#resources(String, ResourceHandler)}: a
 * {@code GET} route for the prefix and one for every path under it, ordinary routes of the router
 * configured together.
 */
class ResourceRoutesTest {

    @Test
    void theRouteMatchesThePrefixAndEveryPathUnderIt() {
        Router router = router(routes -> routes.resources("/assets", resources("assets", "path")));

        assertEquals("/assets", route(router, HttpRequest.GET("/assets")).getUriMatchTemplate().toString());
        assertEquals("/assets/{+path}", route(router, HttpRequest.GET("/assets/site.css")).getUriMatchTemplate().toString());
        assertEquals("", path(router, "/assets"));
        assertEquals("", path(router, "/assets/"));
        assertEquals("site.css", path(router, "/assets/site.css"));
        assertEquals("css/deep/site.css", path(router, "/assets/css/deep/site.css"));
        assertEquals("docs", path(router, "/assets/docs/"));
        // not under the prefix
        assertNull(router.findClosest(HttpRequest.GET("/assetsX")));
        assertNull(router.findClosest(HttpRequest.GET("/other/site.css")));
    }

    @Test
    void theRouteIsAGetRouteWithAnImplicitHeadRoute() {
        Router router = router(routes -> routes.resources("/assets", resources("assets", "path")));

        assertEquals(HttpMethod.GET, route(router, HttpRequest.GET("/assets/site.css")).getHttpMethod());
        UriRouteMatch<Object, Object> head = router.findClosest(HttpRequest.HEAD("/assets/site.css"));
        assertNotNull(head);
        assertEquals(HttpMethod.HEAD, head.getHttpMethod());
        assertEquals("site.css", head.getVariableValues().get("path"));
        assertNull(router.findClosest(HttpRequest.POST("/assets/site.css", "")));
    }

    @Test
    void theTrailingSlashesOfThePrefixAreIgnoredAndALeadingSlashIsAdded() {
        Router router = router(routes -> {
            routes.resources("public/", resources("public", "path"));
            routes.resources("/static//", resources("static", "path"));
        });

        assertEquals("public", target(router, "/public/site.css"));
        assertEquals("static", target(router, "/static/site.css"));
        assertEquals("", path(router, "/static"));
    }

    @Test
    void theRootPrefixMatchesEveryPath() {
        for (String prefix : new String[]{"", "/"}) {
            Router router = router(routes -> routes.resources(prefix, resources("root", "path")));

            assertEquals("", path(router, "/"));
            assertEquals("index.html", path(router, "/index.html"));
            assertEquals("a/b/c.js", path(router, "/a/b/c.js"));
        }
    }

    @Test
    void thePathVariableIsTheOneOfTheHandler() {
        Router router = router(routes -> routes.resources("/files", resources("files", "file")));

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/files/a/b.txt"));
        assertNotNull(match);
        assertEquals("/files/{+file}", match.getRouteInfo().getUriMatchTemplate().toString());
        assertEquals("a/b.txt", match.getVariableValues().get("file"));
    }

    @Test
    void aPathVariableThatIsNotAPlainNameIsRejected() {
        for (String name : new String[]{"", "a/b", "path:.*", "{path}", "a b"}) {
            assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.resources("/assets", resources("assets", name))), name);
        }
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.resources("/assets?x=1", resources("assets", "path"))));
    }

    @Test
    void inAGroupThePrefixFollowsThePrefixOfTheGroup() {
        Router router = router(routes -> routes.path("/app", app -> {
            app.resources("", resources("app", "path"));
            app.resources("/static", resources("static", "path"));
        }));

        assertEquals("/app", route(router, HttpRequest.GET("/app")).getUriMatchTemplate().toString());
        assertEquals("/app/{+path}", route(router, HttpRequest.GET("/app/main.js")).getUriMatchTemplate().toString());
        assertEquals("", path(router, "/app"));
        assertEquals("main.js", path(router, "/app/main.js"));
        assertEquals("static", target(router, "/app/static/site.css"));
        assertEquals("site.css", path(router, "/app/static/site.css"));
    }

    @Test
    void theMoreSpecificRoutesUnderThePrefixTakePrecedence() {
        Router router = router(routes -> {
            routes.resources("/assets", resources("assets", "path"));
            routes.GET("/assets/special.txt", handler("special"));
            routes.GET("/assets/items/{id}", handler("item"));
            routes.GET("/{+any}", handler("catch-all"));
        });

        assertEquals("special", target(router, "/assets/special.txt"));
        assertEquals("item", target(router, "/assets/items/1"));
        assertEquals("assets", target(router, "/assets/other.txt"));
        assertEquals("assets", target(router, "/assets/items/1/more.txt"));
        assertEquals("assets", target(router, "/assets"));
        assertEquals("catch-all", target(router, "/elsewhere.txt"));
    }

    @Test
    void theRouteIsAnOrdinaryRouteWithItsOwnSettings() {
        Router router = router(routes -> routes.resources("/assets", resources("assets", "path"))
            .attribute("static", true)
            .where(request -> !request.getHeaders().contains("X-Skip")));

        // both routes
        assertEquals(Optional.of(true), route(router, HttpRequest.GET("/assets/site.css")).getAttribute("static", Boolean.class));
        assertEquals(Optional.of(true), route(router, HttpRequest.GET("/assets")).getAttribute("static", Boolean.class));
        assertNull(router.findClosest(HttpRequest.GET("/assets/site.css").header("X-Skip", "1")));
        assertNull(router.findClosest(HttpRequest.GET("/assets").header("X-Skip", "1")));
    }

    private static String path(Router router, String uri) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(uri));
        assertNotNull(match, uri);
        // the route of the prefix itself has no variable
        return (String) match.getVariableValues().getOrDefault("path", "");
    }

    private static UriRouteInfo<Object, Object> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return match.getRouteInfo();
    }

    private static String target(Router router, String uri) {
        Object target = ((io.micronaut.web.router.builder.HandlerMethod<?>) route(router, HttpRequest.GET(uri)).getTargetMethod()).getTarget();
        return target instanceof Resources resources ? resources.name() : ((Named) target).name();
    }

    private static ResourceHandler resources(String name, String pathVariable) {
        return new Resources(name, pathVariable);
    }

    private static RequestHandler handler(String name) {
        return new Named(name);
    }

    private record Resources(String name, String pathVariable) implements ResourceHandler {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok(name);
        }
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
