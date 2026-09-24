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
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Response filters declared as functions replace the response they are given, like
 * {@code @ResponseFilter} methods returning a response: the response filters after them and the
 * client see the replacement.
 */
class HttpRoutesResponseReplacementTest {

    private static final RequestHandler OK = (request, pathVariables) -> HttpResponse.ok("route");

    @Test
    void aRouteResponseFilterReplacesTheResponse() {
        Router router = router(routes -> routes.GET("/x", OK)
            .afterReplacing((request, response) -> HttpResponse.status(HttpStatus.ACCEPTED)
                .header("X-Replaced", "route")
                .body("replaced")));

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(HttpStatus.ACCEPTED, run.response.getStatus());
        assertEquals("route", run.response.getHeaders().get("X-Replaced"));
        assertEquals("replaced", run.response.body());
        assertNotSame(run.handlerResponse, run.response);
    }

    @Test
    void theResponseFiltersAfterAReplacementSeeTheReplacement() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> {
            routes.filter("/**").order(-10).after((request, response) -> trace.add("server " + response.code() + " " + response.body()));
            routes.group(group -> {
                group.after((request, response) -> trace.add("group " + response.code() + " " + response.body()));
                group.GET("/x", OK)
                    .after((request, response) -> trace.add("route-before " + response.code()))
                    .afterReplacing((request, response) -> HttpResponse.status(HttpStatus.CREATED).body("replaced"))
                    .after((request, response) -> trace.add("route-after " + response.code() + " " + response.body()));
            });
        });

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(List.of("route-before 200", "route-after 201 replaced", "group 201 replaced", "server 201 replaced"), trace);
        assertEquals(HttpStatus.CREATED, run.response.getStatus());
    }

    @Test
    void aReplacementThatIsNotMutableIsMutableForTheFiltersAfterIt() {
        Router router = router(routes -> routes.GET("/x", OK)
            .afterReplacing((request, response) -> new HttpResponseWrapper<>(HttpResponse.status(HttpStatus.ACCEPTED).header("X-Wrapped", "true")))
            .after((request, response) -> {
                assertInstanceOf(MutableHttpResponse.class, response);
                response.header("X-After", "true");
            }));

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(HttpStatus.ACCEPTED, run.response.getStatus());
        assertEquals("true", run.response.getHeaders().get("X-Wrapped"));
        assertEquals("true", run.response.getHeaders().get("X-After"));
    }

    @Test
    void nullKeepsTheResponseTheFilterChanged() {
        Router router = router(routes -> routes.GET("/x", OK)
            .afterReplacing((request, response) -> {
                response.header("X-Sync", "true");
                return null;
            })
            .afterReplacing((request, response, propagatedContext) -> {
                response.header("X-Context", "true");
                return null;
            })
            .afterReplacingAsync((request, response) -> {
                response.header("X-Async", "true");
                return CompletableFuture.completedFuture(null);
            })
            .afterReplacingAsync((request, response, propagatedContext) -> {
                response.header("X-Async-Context", "true");
                return CompletableFuture.completedFuture(null);
            }));

        Run run = run(router, HttpRequest.GET("/x"));
        assertSame(run.handlerResponse, run.response);
        assertEquals(HttpStatus.OK, run.response.getStatus());
        assertEquals("route", run.response.body());
        for (String header : List.of("X-Sync", "X-Context", "X-Async", "X-Async-Context")) {
            assertEquals("true", run.response.getHeaders().get(header), header);
        }
    }

    @Test
    void anAsynchronousFilterReplacesTheResponseWithTheResponseItsStageCompletesWith() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> routes.group(group -> {
            group.afterAsync((request, response) -> CompletableFuture.completedFuture(trace.add("group " + response.code())));
            group.GET("/x", OK)
                .afterReplacingAsync((request, response) -> CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.ACCEPTED).header("X-Async", "1")))
                .afterReplacingAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(
                    HttpResponse.status(HttpStatus.CREATED).header("X-Async", response.getHeaders().get("X-Async") + ",2")));
        }));

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(HttpStatus.CREATED, run.response.getStatus());
        assertEquals("1,2", run.response.getHeaders().get("X-Async"));
        assertEquals(List.of("group 201"), trace);
    }

    @Test
    void aContextFilterReplacesTheResponseAndChangesTheContextOfTheFiltersAfterIt() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> routes.GET("/x", OK)
            .afterReplacing((request, response, propagatedContext) -> {
                propagatedContext.add(new Marker("replacer"));
                return HttpResponse.status(HttpStatus.ACCEPTED);
            })
            .after((request, response) -> trace.add(response.code() + " " + PropagatedContext.getOrEmpty().find(Marker.class).map(Marker::name).orElse("none"))));

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(List.of("202 replacer"), trace);
        assertEquals(HttpStatus.ACCEPTED, run.response.getStatus());
    }

    @Test
    void theFiltersOfAGroupAndOfAServerFilterReplaceTheResponse() {
        Router router = router(routes -> {
            routes.filter("/**").afterReplacing((request, response) ->
                HttpResponse.status(HttpStatus.CREATED).header("X-Server", response.getHeaders().get("X-Group")));
            routes.group(group -> {
                group.afterReplacing((request, response) -> HttpResponse.status(HttpStatus.ACCEPTED).header("X-Group", "group-" + response.body()));
                group.GET("/x", OK);
            });
        });

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(HttpStatus.CREATED, run.response.getStatus());
        assertEquals("group-route", run.response.getHeaders().get("X-Server"));
    }

    @Test
    void aPreMatchingServerFilterReplacesTheResponseItsRequestFilterAnsweredWith() {
        Router router = router(routes -> {
            routes.GET("/ok", OK);
            routes.filter("/**").preMatching()
                .beforeReplacing(request -> request.getPath().equals("/blocked") ? HttpResponse.status(HttpStatus.FORBIDDEN) : null)
                .afterReplacing((request, response) -> response.code() == HttpStatus.OK.getCode()
                    ? null
                    : HttpResponse.status(HttpStatus.UNAUTHORIZED).header("X-Replaced", String.valueOf(response.code())));
        });

        Run blocked = run(router, HttpRequest.GET("/blocked"));
        assertEquals(List.of(), blocked.trace);
        assertEquals(HttpStatus.UNAUTHORIZED, blocked.response.getStatus());
        assertEquals("403", blocked.response.getHeaders().get("X-Replaced"));

        Run missing = run(router, HttpRequest.GET("/missing"));
        assertEquals(HttpStatus.UNAUTHORIZED, missing.response.getStatus());
        assertEquals("404", missing.response.getHeaders().get("X-Replaced"));

        Run ok = run(router, HttpRequest.GET("/ok"));
        assertEquals(List.of("match GET /ok", "handler GET /ok"), ok.trace);
        assertSame(ok.handlerResponse, ok.response);
    }

    private record Marker(String name) implements PropagatedContextElement {
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
                MutableHttpResponse<String> response = HttpResponse.ok("route");
                run.handlerResponse = response;
                return ExecutionFlow.just(response);
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
        @Nullable HttpResponse<?> handlerResponse;
        HttpResponse<?> response = HttpResponse.ok();
    }
}
