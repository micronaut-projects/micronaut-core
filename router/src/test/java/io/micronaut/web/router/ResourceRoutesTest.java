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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.PathVariables;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.ResourceHandler;
import io.micronaut.web.router.builder.RequestCondition;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape of the routes of {@link io.micronaut.web.router.builder.HttpRouteSpec#resources(ResourceHandler)}: a
 * {@code GET} route for the prefix and one for every path under it, ordinary routes of the router
 * configured together.
 */
class ResourceRoutesTest {

    @Test
    void theRouteMatchesThePrefixAndEveryPathUnderIt() {
        Router router = router(routes -> routes.GET("/assets").resources(resources("assets", "path")));

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
        Router router = router(routes -> routes.GET("/assets").resources(resources("assets", "path")));

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
            routes.GET("public/").resources(resources("public", "path"));
            routes.GET("/static//").resources(resources("static", "path"));
        });

        assertEquals("public", target(router, "/public/site.css"));
        assertEquals("static", target(router, "/static/site.css"));
        assertEquals("", path(router, "/static"));
    }

    @Test
    void theRootPrefixMatchesEveryPath() {
        for (String prefix : new String[]{"", "/"}) {
            Router router = router(routes -> routes.GET(prefix).resources(resources("root", "path")));

            assertEquals("", path(router, "/"));
            assertEquals("index.html", path(router, "/index.html"));
            assertEquals("a/b/c.js", path(router, "/a/b/c.js"));
        }
    }

    @Test
    void thePathVariableIsTheOneOfTheHandler() {
        Router router = router(routes -> routes.GET("/files").resources(resources("files", "file")));

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/files/a/b.txt"));
        assertNotNull(match);
        assertEquals("/files/{+file}", match.getRouteInfo().getUriMatchTemplate().toString());
        assertEquals("a/b.txt", match.getVariableValues().get("file"));
    }

    @Test
    void aPathVariableThatIsNotAPlainNameIsRejected() {
        for (String name : new String[]{"", "a/b", "path:.*", "{path}", "a b"}) {
            assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.GET("/assets").resources(resources("assets", name))), name);
        }
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.GET("/assets?x=1").resources(resources("assets", "path"))));
    }

    @Test
    void onlyARouteOfGetOnlyIsARouteOfResources() {
        Router router = router(routes -> {
            assertThrows(IllegalStateException.class, () -> routes.POST("/assets").resources(resources("assets", "path")));
            assertThrows(IllegalStateException.class, () -> routes.any("/assets").resources(resources("assets", "path")));
            assertThrows(IllegalStateException.class, () -> routes.route(java.util.Set.of(HttpMethod.GET, HttpMethod.POST), "/assets")
                .resources(resources("assets", "path")));
        });

        // the failed routes are dropped, not left without a terminal
        assertNull(router.findClosest(HttpRequest.GET("/assets/site.css")));
        assertNull(router.findClosest(HttpRequest.POST("/assets/site.css", "")));
    }

    @Test
    void aRouteOfResourcesHasAUriTemplate() {
        Router router = router(routes -> routes.group(g -> {
            g.templates(ColonRouteTemplateEngine.INSTANCE);
            assertThrows(IllegalStateException.class, () -> g.GET("/assets").resources(resources("assets", "path")));
        }));

        // the failed route is dropped, not left without a terminal
        assertNull(router.findClosest(HttpRequest.GET("/assets/site.css")));
    }

    @Test
    void aRouteOfGetOnlyByAnyMeansIsARouteOfResources() {
        Router router = router(routes -> {
            routes.route("GET", "/byName").resources(resources("byName", "path"));
            routes.route(java.util.Set.of(HttpMethod.GET), "/bySet").resources(resources("bySet", "path"));
        });

        assertEquals("byName", target(router, "/byName/site.css"));
        assertEquals("bySet", target(router, "/bySet/site.css"));
    }

    @Test
    void aRouteOfResourcesIsEndedOnce() {
        router(routes -> {
            var spec = routes.GET("/assets");
            spec.resources(resources("assets", "path"));
            assertThrows(IllegalStateException.class, () -> spec.resources(resources("assets", "path")));
            assertThrows(IllegalStateException.class, () -> spec.handle(handler("other")));
        });
    }

    @Test
    void inAGroupThePrefixFollowsThePrefixOfTheGroup() {
        Router router = router(routes -> routes.path("/app", app -> {
            app.GET("").resources(resources("app", "path"));
            app.GET("/static").resources(resources("static", "path"));
        }));

        assertEquals("/app", route(router, HttpRequest.GET("/app")).getUriMatchTemplate().toString());
        assertEquals("/app/{+path}", route(router, HttpRequest.GET("/app/main.js")).getUriMatchTemplate().toString());
        assertEquals("", path(router, "/app"));
        assertEquals("main.js", path(router, "/app/main.js"));
        assertEquals("static", target(router, "/app/static/site.css"));
        assertEquals("site.css", path(router, "/app/static/site.css"));
    }

    @Test
    void theResourcesWithoutAPrefixAreAtThePrefixOfTheGroup() {
        Router router = router(routes -> routes.path("/app", app -> app.GET("").resources(resources("app", "path"))));

        assertEquals("/app", route(router, HttpRequest.GET("/app")).getUriMatchTemplate().toString());
        assertEquals("/app/{+path}", route(router, HttpRequest.GET("/app/main.js")).getUriMatchTemplate().toString());
        assertEquals("main.js", path(router, "/app/main.js"));
    }

    @Test
    void theMoreSpecificRoutesUnderThePrefixTakePrecedence() {
        Router router = router(routes -> {
            routes.GET("/assets").resources(resources("assets", "path"));
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
        Router router = router(routes -> routes.GET("/assets")
            .attribute("static", true)
            .where(RequestCondition.custom(request -> !request.getHeaders().contains("X-Skip")))
            .resources(resources("assets", "path")));

        // both routes
        assertEquals(Optional.of(true), route(router, HttpRequest.GET("/assets/site.css")).getAttribute("static", Boolean.class));
        assertEquals(Optional.of(true), route(router, HttpRequest.GET("/assets")).getAttribute("static", Boolean.class));
        assertNull(router.findClosest(HttpRequest.GET("/assets/site.css").header("X-Skip", "1")));
        assertNull(router.findClosest(HttpRequest.GET("/assets").header("X-Skip", "1")));
    }

    @Test
    void inAGroupTheRouteHasTheExecutorOfTheGroupButNotItsMediaTypes() {
        Router plain = router(routes -> routes.GET("/assets").resources(resources("assets", "path")));
        Router grouped = router(routes -> routes.path("/assets", assets -> {
            assets.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE).executeOn("group-executor");
            assets.GET("").resources(resources("assets", "path"));
        }));

        for (String uri : new String[]{"/assets", "/assets/site.css"}) {
            UriRouteInfo<Object, Object> route = route(grouped, HttpRequest.GET(uri).accept(MediaType.of("text/css")));
            UriRouteInfo<Object, Object> expected = route(plain, HttpRequest.GET(uri));
            assertEquals(expected.getConsumes(), route.getConsumes(), uri);
            assertEquals(expected.getProduces(), route.getProduces(), uri);
            // no executor of that name in this router
            Exception error = assertThrows(Exception.class, () -> route.getExecutor(ThreadSelection.MANUAL), uri);
            assertTrue(error.getMessage().contains("group-executor"), error.getMessage());
        }
    }

    @Test
    void aRouteOfResourcesThatBlocksRunsOnTheBlockingExecutor() {
        Router router = router(routes -> routes.GET("/assets").resources(resources("assets", "path")));

        for (String uri : new String[]{"/assets", "/assets/site.css"}) {
            UriRouteInfo<Object, Object> route = route(router, HttpRequest.GET(uri));
            // no executor of that name in this router
            Exception error = assertThrows(Exception.class, () -> route.getExecutor(ThreadSelection.MANUAL), uri);
            assertTrue(error.getMessage().contains("blocking"), error.getMessage());
        }
    }

    @Test
    void theExecutorOfTheRouteReplacesTheBlockingExecutor() {
        Router own = router(routes -> routes.GET("/own").executeOn("own-executor").resources(resources("own", "path")));
        Router nonBlocking = router(routes -> routes.GET("/event-loop").nonBlocking().resources(resources("event-loop", "path")));

        Exception error = assertThrows(Exception.class, () -> route(own, HttpRequest.GET("/own/site.css")).getExecutor(ThreadSelection.MANUAL));
        assertTrue(error.getMessage().contains("own-executor"), error.getMessage());
        assertNull(route(nonBlocking, HttpRequest.GET("/event-loop/site.css")).getExecutor(ThreadSelection.MANUAL));
    }

    @Test
    void aResourceHandlerOfAnOrdinaryRouteIsARouteOfResources() {
        Router router = router(routes -> routes.path("/api", api -> {
            api.produces(MediaType.APPLICATION_JSON_TYPE);
            api.GET("/files/{+path}", resources("files", "path"));
            api.GET("/memory/{+path}", new InMemory());
        }));
        Router plain = router(routes -> routes.GET("/files/{+path}", handler("files")));

        UriRouteInfo<Object, Object> files = route(router, HttpRequest.GET("/api/files/site.css").accept(MediaType.of("text/css")));
        assertEquals(route(plain, HttpRequest.GET("/files/site.css")).getProduces(), files.getProduces());
        Exception error = assertThrows(Exception.class, () -> files.getExecutor(ThreadSelection.MANUAL));
        assertTrue(error.getMessage().contains("blocking"), error.getMessage());
        assertNull(route(router, HttpRequest.GET("/api/memory/site.css").accept(MediaType.of("text/css"))).getExecutor(ThreadSelection.MANUAL));
    }

    @Test
    void theExecutorOfTheClosestGroupReplacesTheBlockingExecutor() {
        Router router = router(routes -> routes.path("/outer", outer -> {
            outer.executeOn("outer-executor");
            outer.path("/inner", inner -> {
                inner.executeOn("inner-executor");
                inner.GET("/assets").resources(resources("inner", "path"));
            });
            outer.path("/plain", plain -> plain.GET("/assets").resources(resources("plain", "path")));
            outer.path("/loop", loop -> {
                loop.nonBlocking();
                loop.GET("/assets").resources(resources("loop", "path"));
            });
        }));

        Exception inner = assertThrows(Exception.class, () -> route(router, HttpRequest.GET("/outer/inner/assets/a.css")).getExecutor(ThreadSelection.MANUAL));
        assertTrue(inner.getMessage().contains("inner-executor"), inner.getMessage());
        Exception outer = assertThrows(Exception.class, () -> route(router, HttpRequest.GET("/outer/plain/assets/a.css")).getExecutor(ThreadSelection.MANUAL));
        assertTrue(outer.getMessage().contains("outer-executor"), outer.getMessage());
        assertNull(route(router, HttpRequest.GET("/outer/loop/assets/a.css")).getExecutor(ThreadSelection.MANUAL));
    }

    @Test
    void aVariableOfThePrefixMatchesOneSegment() {
        Router router = router(routes -> {
            routes.GET("/tenants/{tenant:[a-z]+?}").resources(resources("tenants", "path"));
            routes.GET("/digits/{id:\\d+}").resources(resources("digits", "path"));
            routes.GET("/segment/{name:[^/]+}").resources(resources("segment", "path"));
            routes.GET("/dotted/{name:[.a-z]+}").resources(resources("dotted", "path"));
            routes.GET("/choice/{name:ab|cd}").resources(resources("choice", "path"));
            for (String prefix : new String[]{"/docs{/version}", "/dirs/{+dir}", "/any/{dir:.*}", "/slash/{dir:[^x]+}", "/many/{dirs*}",
                "/range/{dir:[!-~]+}", "/empty/{dir:[a-z]*}", "/optional/{dir:a?}", "/modifier/{dir:?}", "/groups/{tenant:(ac)(hello[.]txt)}",
                "/literal/{version:foo/bar}", "/escaped/{version:foo\\/bar}"}) {
                assertThrows(IllegalArgumentException.class, () -> routes.GET(prefix).resources(resources("bad", "path")), prefix);
            }
        });

        assertEquals("tenants", target(router, "/tenants/acme/site.css"));
        assertEquals("acme", router.findClosest(HttpRequest.GET("/tenants/acme/site.css")).getVariableValues().get("tenant"));
        assertNull(router.findClosest(HttpRequest.GET("/docs/css/site.css")));
        assertEquals("digits", target(router, "/digits/42/site.css"));
        assertEquals("segment", target(router, "/segment/a.b/site.css"));
        assertEquals("dotted", target(router, "/dotted/a.b/site.css"));
        assertEquals("choice", target(router, "/choice/cd/site.css"));
    }

    @Test
    void aLocatedResourceHandlerIsNotARouteOfResources() {
        AtomicReference<@Nullable Exception> error = new AtomicReference<>();
        TestLocatedRoutes<Object> located = TestLocatedRoutes.of(routes -> {
            try {
                routes.GET("/files/{+path}", resources("files", "path"));
            } catch (IllegalStateException e) {
                error.set(e);
            }
            routes.GET("/", (request, pathVariables) -> HttpResponse.ok());
        });
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        builder.locate("/orders/{id}", (request, pathVariables) -> "order", target -> located);
        builder.close();
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        HttpRequest<?> request = HttpRequest.GET("/orders/1/");
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        new FilterRunner(router.findFilters(request, match), (r, propagatedContext) -> ExecutionFlow.just(HttpResponse.ok()))
            .run(request);

        assertNotNull(error.get());
        assertTrue(error.get().getMessage().contains("located route"), error.get().getMessage());
    }

    @Test
    void aRouteOfResourcesThatDoesNotBlockRunsLikeAnyHandler() {
        Router router = router(routes -> routes.GET("/memory").resources(new InMemory()));

        assertNull(route(router, HttpRequest.GET("/memory/site.css")).getExecutor(ThreadSelection.MANUAL));
    }

    @Test
    void thePrefixCannotHaveThePathVariableOfTheResources() {
        Router router = router(routes -> {
            routes.path("/tenants/{path}", tenants ->
                assertThrows(IllegalStateException.class, () -> tenants.GET("").resources(resources("tenants", "path"))));
            routes.path("/sites/{path}", sites -> sites.GET("").resources(resources("sites", "file")));
        });

        // the failed route is dropped, the one with another variable is declared
        assertNull(router.findClosest(HttpRequest.GET("/tenants/acme/site.css")));
        assertEquals("sites", target(router, "/sites/acme/site.css"));
    }

    @Test
    void aLocatedRouteIsNotARouteOfResources() {
        AtomicReference<@Nullable Exception> error = new AtomicReference<>();
        TestLocatedRoutes<Object> located = TestLocatedRoutes.of(routes -> {
            try {
                routes.GET("/assets").resources(resources("assets", "path"));
            } catch (IllegalStateException e) {
                error.set(e);
            }
            routes.GET("/", (request, pathVariables) -> HttpResponse.ok());
        });
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        builder.locate("/orders/{id}", (request, pathVariables) -> "order", target -> located);
        builder.close();
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        // the locator declares the located routes of a request when its filter runs
        HttpRequest<?> request = HttpRequest.GET("/orders/1/");
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        new FilterRunner(router.findFilters(request, match), (r, propagatedContext) -> ExecutionFlow.just(HttpResponse.ok()))
            .run(request);

        assertNotNull(error.get());
        assertTrue(error.get().getMessage().contains("located route"), error.get().getMessage());
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

    private record InMemory() implements ResourceHandler {
        @Override
        public String pathVariable() {
            return "path";
        }

        @Override
        public boolean blocking() {
            return false;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok("memory");
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
