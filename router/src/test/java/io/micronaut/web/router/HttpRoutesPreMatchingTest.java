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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Server filters declared by {@code HttpRoutes} that run before the route is matched, like
 * {@code @PreMatching} filter methods.
 */
class HttpRoutesPreMatchingTest {

    private static final RequestHandler OK = (request, pathVariables) -> HttpResponse.ok();

    @Test
    void preMatchingServerFiltersRunByOrderBeforeTheMatch() {
        Router router = router(routes -> {
            routes.GET("/x", OK).before(request -> trace(request, "route"));
            routes.filter("/**").order(10).preMatching().before(request -> trace(request, "pre10"));
            routes.filter("/**").before(request -> trace(request, "server0"));
            routes.filter("/**").preMatching().order(-5)
                .before(request -> trace(request, "pre-5"))
                .before((request, propagatedContext) -> trace(request, "pre-5-context"))
                .beforeAsync(request -> CompletableFuture.completedFuture(trace(request, "pre-5-async")))
                .beforeAsync((request, propagatedContext) -> CompletableFuture.completedFuture(trace(request, "pre-5-async-context")));
        });

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(List.of("match GET /x", "handler GET /x"), run.trace);
        assertEquals("pre-5,pre-5-context,pre-5-async,pre-5-async-context,pre10,server0,route", run.handled().getAttribute("trace", String.class).orElseThrow());
    }

    @Test
    void theResponseFiltersOfAPreMatchingServerFilterFilterEveryResponseOnce() {
        Router router = router(routes -> {
            routes.GET("/ok", OK);
            routes.filter("/**").preMatching()
                .before(request -> request.getPath().equals("/blocked") ? HttpResponse.status(HttpStatus.FORBIDDEN) : null)
                .after((request, response) -> response.header("X-After", response.getHeaders().get("X-After") == null ? "1" : "2"));
        });

        Run blocked = run(router, HttpRequest.GET("/blocked"));
        assertEquals(List.of(), blocked.trace);
        assertEquals(HttpStatus.FORBIDDEN, blocked.response.getStatus());
        assertEquals("1", blocked.response.getHeaders().get("X-After"));

        Run ok = run(router, HttpRequest.GET("/ok"));
        assertEquals(List.of("match GET /ok", "handler GET /ok"), ok.trace);
        assertEquals("1", ok.response.getHeaders().get("X-After"));

        Run missing = run(router, HttpRequest.GET("/missing"));
        assertEquals(List.of("match GET /missing none"), missing.trace);
        assertEquals(HttpStatus.NOT_FOUND, missing.response.getStatus());
        assertEquals("1", missing.response.getHeaders().get("X-After"));
    }

    private static HttpResponse<?> trace(HttpRequest<?> request, String step) {
        String trace = request.getAttribute("trace", String.class).orElse(null);
        request.setAttribute("trace", trace == null ? step : trace + "," + step);
        return null;
    }

    private static Run run(Router router, HttpRequest<?> request) {
        Run run = new Run();
        FilterRunner runner = new FilterRunner(router.findPreMatchingFilters(request), null, (r, propagatedContext) -> {
            throw new IllegalStateException("not called");
        }) {
            private @Nullable UriRouteMatch<Object, Object> match;

            @Override
            protected void doRouteMatch(HttpRequest<?> matched) {
                match = router.findClosest(matched);
                run.trace.add("match " + matched.getMethodName() + " " + matched.getPath() + (match == null ? " none" : ""));
            }

            @Override
            protected List<GenericHttpFilter> findFiltersAfterRouteMatch(HttpRequest<?> matched) {
                return router.findFilters(matched, match);
            }

            @Override
            protected ExecutionFlow<HttpResponse<?>> provideResponse(HttpRequest<?> handled, PropagatedContext propagatedContext) {
                if (match == null) {
                    return ExecutionFlow.just(HttpResponse.notFound());
                }
                run.trace.add("handler " + handled.getMethodName() + " " + handled.getPath());
                run.handledRequest = handled;
                return ExecutionFlow.just(HttpResponse.ok());
            }
        };
        HttpResponse<?> response = runner.run(request).tryCompleteValue();
        assertNotNull(response, "the filters completed synchronously");
        run.response = response;
        return run;
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { }, null);
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private static final class Run {
        final List<String> trace = new ArrayList<>();
        @Nullable HttpRequest<?> handledRequest;
        HttpResponse<?> response = HttpResponse.ok();

        HttpRequest<?> handled() {
            return assertInstanceOf(HttpRequest.class, handledRequest);
        }
    }
}
