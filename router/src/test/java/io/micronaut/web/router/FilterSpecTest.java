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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.FilterSpec;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteGroup;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.RouteFilterSpec;
import io.micronaut.web.router.builder.ServerFilterSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@link FilterSpec} every filter method returns: {@code and()} goes back to the route, the
 * group or the server filter, and {@code executeOn} and {@code nonBlocking} choose the thread of
 * the filter, the last call winning. Without an executor selector, a filter given an executor
 * fails when it runs: that is how these tests see which filters were given one.
 */
class FilterSpecTest {

    private static final String EXECUTOR = "blocking";
    private static final String NO_EXECUTOR = "No executor selector to find executor: " + EXECUTOR;

    @Test
    void andGoesBackToTheRouteTheGroupOrTheServerFilter() {
        router(routes -> {
            HttpRouteSpec route = routes.GET("/route", FilterSpecTest::ok);
            assertSame(route, route.before(request -> { }).and());
            assertSame(route, route.after((request, response) -> { }).executeOn(EXECUTOR).and());
            assertSame(route, route.beforeReplacingAsync(request -> CompletableFuture.completedFuture(null)).nonBlocking().and());
            ServerFilterSpec server = routes.filter("/**");
            assertSame(server, server.beforeReplacing(request -> null).executeOn(EXECUTOR).nonBlocking().and());
            routes.group(group -> {
                assertSame(group, group.afterReplacing((request, response) -> null).and());
                assertSame(group, group.beforeAsync(request -> CompletableFuture.completedFuture(null)).executeOn(EXECUTOR).and());
            });
        });
    }

    @Test
    void aRouteFilterRunsOnTheExecutorItIsGivenTheLastChoiceWinning() {
        Router router = router(routes -> {
            routes.GET("/executor", FilterSpecTest::ok).before(request -> { }).executeOn(EXECUTOR);
            routes.GET("/non-blocking", FilterSpecTest::ok).before(request -> { }).nonBlocking();
            routes.GET("/executor-last", FilterSpecTest::ok).before(request -> { }).nonBlocking().executeOn(EXECUTOR);
            routes.GET("/non-blocking-last", FilterSpecTest::ok).before(request -> { }).executeOn(EXECUTOR).nonBlocking();
            routes.GET("/after", FilterSpecTest::ok).after((request, response) -> { }).executeOn(EXECUTOR);
            routes.GET("/async", FilterSpecTest::ok).afterAsync((request, response) -> CompletableFuture.completedFuture(null)).executeOn(EXECUTOR);
            routes.GET("/second", FilterSpecTest::ok)
                .before(request -> { })
                .and()
                .before(request -> { }).executeOn(EXECUTOR);
        });

        assertEquals(NO_EXECUTOR, failure(router, "/executor"));
        assertNull(failure(router, "/non-blocking"));
        assertEquals(NO_EXECUTOR, failure(router, "/executor-last"));
        assertNull(failure(router, "/non-blocking-last"));
        assertEquals(NO_EXECUTOR, failure(router, "/after"));
        assertEquals(NO_EXECUTOR, failure(router, "/async"));
        assertEquals(NO_EXECUTOR, failure(router, "/second"));
    }

    @Test
    void aFilterOfAGroupRunsOnTheExecutorItIsGiven() {
        Router router = router(routes -> {
            routes.path("/executor", group -> {
                group.GET("/route", FilterSpecTest::ok);
                group.after((request, response) -> { }).nonBlocking().executeOn(EXECUTOR);
            });
            routes.path("/non-blocking", group -> {
                group.before(request -> { }).executeOn(EXECUTOR).nonBlocking();
                group.GET("/route", FilterSpecTest::ok);
            });
        });

        assertEquals(NO_EXECUTOR, failure(router, "/executor/route"));
        assertNull(failure(router, "/non-blocking/route"));
    }

    @Test
    void aServerFilterRunsOnTheExecutorItIsGiven() {
        Router router = router(routes -> {
            routes.filter("/executor/**").before(request -> { }).executeOn(EXECUTOR).and().order(5);
            routes.filter("/non-blocking/**").after((request, response) -> { }).executeOn(EXECUTOR).nonBlocking();
        });

        assertEquals(NO_EXECUTOR, failure(router, "/executor/x"));
        assertNull(failure(router, "/non-blocking/x"));
    }

    @Test
    void theExecutorOfAFilterOfAClosedGroupCannotChange() {
        List<FilterSpec<HttpRouteGroup>> kept = new ArrayList<>();
        router(routes -> routes.group(group -> {
            group.GET("/route", FilterSpecTest::ok);
            kept.add(group.before(request -> { }));
        }));
        FilterSpec<HttpRouteGroup> filter = kept.getFirst();
        IllegalStateException executor = assertThrows(IllegalStateException.class, () -> filter.executeOn(EXECUTOR));
        assertEquals("The route group is closed: declare the filters of a group, and their executors, in its lambda", executor.getMessage());
        assertThrows(IllegalStateException.class, filter::nonBlocking);
    }

    @Test
    void theExecutorOfAFilterIsFixedWhenTheRouterIsBuilt() {
        List<FilterSpec<HttpRouteSpec>> kept = new ArrayList<>();
        Router router = router(routes -> kept.add(routes.GET("/route", FilterSpecTest::ok).before(request -> { })));
        // the router took the route: the change is ignored, like a change of the settings of the route
        assertNull(failure(router, "/route"));
        kept.getFirst().executeOn(EXECUTOR);
        assertNull(failure(router, "/route"));
    }

    @Test
    void everyFilterMethodReturnsTheSpecOfTheFilter() {
        // the one-argument lambdas and the variants with the propagated context: sixteen methods, none with an executor
        List<Function<RouteFilterSpec<HttpRouteSpec>, FilterSpec<HttpRouteSpec>>> methods = List.of(
            spec -> spec.before(request -> { }),
            spec -> spec.before((request, propagatedContext) -> { }),
            spec -> spec.beforeAsync(request -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeAsync((request, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeReplacing(request -> null),
            spec -> spec.beforeReplacing((request, propagatedContext) -> null),
            spec -> spec.beforeReplacingAsync(request -> CompletableFuture.completedFuture(null)),
            spec -> spec.beforeReplacingAsync((request, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.after((request, response) -> { }),
            spec -> spec.after((request, response, propagatedContext) -> { }),
            spec -> spec.afterAsync((request, response) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterReplacing((request, response) -> null),
            spec -> spec.afterReplacing((request, response, propagatedContext) -> null),
            spec -> spec.afterReplacingAsync((request, response) -> CompletableFuture.completedFuture(null)),
            spec -> spec.afterReplacingAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(null)));
        List<String> paths = new ArrayList<>();
        Router router = router(routes -> {
            for (int i = 0; i < methods.size(); i++) {
                String path = "/method/" + i;
                paths.add(path);
                HttpRouteSpec route = routes.GET(path, FilterSpecTest::ok);
                assertSame(route, methods.get(i).apply(route).executeOn(EXECUTOR).and());
            }
        });
        for (String path : paths) {
            assertEquals(NO_EXECUTOR, failure(router, path), path);
        }
    }

    /**
     * Run the filters of the route of a request.
     *
     * @return The message of the failure of the filter chain, or {@code null} if it completed
     */
    private static String failure(Router router, String path) {
        HttpRequest<?> request = HttpRequest.GET(path);
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        ExecutionFlow<HttpResponse<?>> flow = new FilterRunner(router.findFilters(request, match),
            (r, propagatedContext) -> ExecutionFlow.just(HttpResponse.ok()))
            .run(request);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<HttpResponse<?>> response = new AtomicReference<>();
        flow.onComplete((value, throwable) -> {
            response.set(value);
            error.set(throwable);
        });
        Throwable failure = error.get();
        if (failure == null) {
            assertNotNull(response.get(), path);
            return null;
        }
        return failure.getMessage();
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, io.micronaut.web.router.builder.PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
