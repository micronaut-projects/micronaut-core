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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteGroup;
import io.micronaut.web.router.builder.ServerFilterSpec;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server filters declared with {@link HttpRouteBuilder#filter(String...)}: filter routes like
 * those of a {@code @ServerFilter} bean, with patterns, methods and an order.
 */
class HttpRoutesServerFiltersTest {

    @Test
    void serverFiltersRunByOrderThenInTheOrderDeclared() {
        List<String> trace = new ArrayList<>();
        Router router = router(null, routes -> {
            routes.filter("/**").before(request -> record(trace, "a0"));
            routes.filter("/**").order(10)
                .before(request -> record(trace, "b10-1"))
                .after((request, response) -> trace.add("b10-after"))
                .before(request -> record(trace, "b10-2"));
            routes.filter("/**").order(-10).before(request -> record(trace, "c-10")).after((request, response) -> trace.add("c-10-after"));
            routes.filter("/**").before(request -> record(trace, "d0"));
        });

        run(router, HttpRequest.GET("/anything"), trace);
        assertEquals(List.of("c-10", "a0", "d0", "b10-1", "b10-2", "handler", "b10-after", "c-10-after"), trace);
    }

    @Test
    void aServerFilterFiltersItsPatternsAndMethodsOnly() {
        List<String> trace = new ArrayList<>();
        Router router = router(null, routes -> {
            routes.filter("/api/**", "/admin").before(request -> record(trace, "api"));
            routes.filter("/**").methods(HttpMethod.POST, HttpMethod.PUT).before(request -> record(trace, "writes"));
            routes.filter("^/regex/[0-9]+$").patternStyle(FilterPatternStyle.REGEX).before(request -> record(trace, "regex"));
        });

        assertEquals(List.of("api", "handler"), run(router, HttpRequest.GET("/api/orders"), trace));
        assertEquals(List.of("api", "handler"), run(router, HttpRequest.GET("/admin"), trace));
        assertEquals(List.of("handler"), run(router, HttpRequest.GET("/other"), trace));
        assertEquals(List.of("writes", "handler"), run(router, HttpRequest.POST("/other", ""), trace));
        assertEquals(List.of("regex", "handler"), run(router, HttpRequest.GET("/regex/12"), trace));
        assertEquals(List.of("handler"), run(router, HttpRequest.GET("/regex/ab"), trace));
    }

    @Test
    void thePatternsAreUnderTheContextPathLikeThoseOfAServerFilterBean() {
        List<String> trace = new ArrayList<>();
        Router router = router("/ctx", routes -> {
            routes.filter("/api/**").before(request -> record(trace, "under"));
            routes.filter("/ctx/already/**").before(request -> record(trace, "already"));
            routes.filter("/raw/**").appendContextPath(false).before(request -> record(trace, "raw"));
        });

        assertEquals(List.of("under", "handler"), run(router, HttpRequest.GET("/ctx/api/x"), trace));
        assertEquals(List.of("handler"), run(router, HttpRequest.GET("/api/x"), trace));
        assertEquals(List.of("already", "handler"), run(router, HttpRequest.GET("/ctx/already/x"), trace));
        assertEquals(List.of("raw", "handler"), run(router, HttpRequest.GET("/raw/x"), trace));
    }

    @Test
    void aServerFilterOfAGroupIsGlobal() {
        List<String> trace = new ArrayList<>();
        Router router = router(null, routes -> routes.path("/api", api -> {
            api.before(request -> record(trace, "group"));
            ServerFilterSpec filter = api.filter("/other/**");
            filter.before(request -> record(trace, "server"));
        }));

        // not under the prefix of the group, and without the filters of the group
        assertEquals(List.of("server", "handler"), run(router, HttpRequest.GET("/other/x"), trace));
    }

    @Test
    void everyFilterVariantCompilesOnAServerFilter() {
        List<String> trace = new ArrayList<>();
        Router router = router(null, routes -> {
            ServerFilterSpec same = routes.filter("/**")
                .before(request -> record(trace, "before"))
                .before((request, propagatedContext) -> record(trace, "context-before"))
                .beforeAsync(request -> CompletableFuture.completedFuture(record(trace, "async-before")))
                .beforeAsync((request, propagatedContext) -> CompletableFuture.completedFuture(record(trace, "async-context-before")))
                .after((request, response) -> trace.add("after"))
                .after((request, response, propagatedContext) -> trace.add("context-after"))
                .afterAsync((request, response) -> CompletableFuture.completedFuture(trace.add("async-after")))
                .afterAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(trace.add("async-context-after")))
                .methods(HttpMethod.GET)
                .order(1)
                .patternStyle(FilterPatternStyle.ANT)
                .appendContextPath(true);
            assertNotNull(same);
        });

        assertEquals(List.of("before", "context-before", "async-before", "async-context-before", "handler",
            "after", "context-after", "async-after", "async-context-after"), run(router, HttpRequest.GET("/x"), trace));
    }

    @Test
    void aServerFilterNeedsAPatternAndAnOpenBuilder() {
        assertThrows(IllegalArgumentException.class, () -> router(null, routes -> routes.filter()));
        assertThrows(IllegalArgumentException.class, () -> router(null, routes -> routes.filter("")));
        AtomicReference<HttpRouteGroup> leaked = new AtomicReference<>();
        router(null, routes -> routes.group(leaked::set));
        assertThrows(IllegalStateException.class, () -> leaked.get().filter("/**"));
    }

    @Test
    void aRouteTableCannotDeclareServerFilters() {
        RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> tables.buildHttpRoutes(routes -> {
            routes.GET("/x", (request, pathVariables) -> HttpResponse.ok());
            routes.filter("/**").before(request -> null);
        }));
        assertTrue(error.getMessage().contains("filter"), error.getMessage());
    }

    /**
     * Run the server filters of a request no route matches, and answer it.
     */
    private static List<String> run(Router router, HttpRequest<?> request, List<String> trace) {
        trace.clear();
        HttpResponse<?> response = new FilterRunner(router.findFilters(request), (r, propagatedContext) -> {
            trace.add("handler");
            return ExecutionFlow.just(HttpResponse.status(HttpStatus.NOT_FOUND));
        }).run(request).tryCompleteValue();
        assertNotNull(response, "the filters completed synchronously");
        return List.copyOf(trace);
    }

    private static HttpResponse<?> record(List<String> trace, String step) {
        trace.add(step);
        return null;
    }

    private static Router router(@Nullable String contextPath, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> RouteAssembly.underContextPath(contextPath, uri), route -> { }, contextPath);
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
