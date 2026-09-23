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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Request filters declared as functions change the request in place or continue with another
 * request, like filter methods, and a pre-matching server filter changes the route that is matched.
 */
class HttpRoutesRequestChangesTest {

    private static final RequestHandler OK = (request, pathVariables) -> HttpResponse.ok();

    @Test
    void aPreMatchingServerFilterThatChangesTheUriChangesTheMatchedRoute() {
        Router router = router(routes -> {
            routes.GET("/new", OK);
            routes.filter("/**").preMatching().before(request -> {
                if (request.getPath().equals("/old")) {
                    request.uri(URI.create("/new?q=1"));
                }
            });
        });

        Run run = run(router, HttpRequest.GET("/old"));
        assertEquals(List.of("match GET /new", "handler GET /new"), run.trace);
        assertEquals("q=1", run.handled().getUri().getQuery());
    }

    @Test
    void aPreMatchingServerFilterThatReturnsARequestWithAnotherMethodChangesTheMatchedRoute() {
        Router router = router(routes -> {
            routes.POST("/item", OK);
            routes.PUT("/item", OK);
            routes.filter("/**").preMatching().beforeReplacing(request -> {
                String override = request.getHeaders().get("X-Method");
                return override == null ? null : withMethod(request, HttpMethod.parse(override));
            });
        });

        assertEquals(List.of("match PUT /item", "handler PUT /item"), run(router, HttpRequest.POST("/item", "").header("X-Method", "PUT")).trace);
        assertEquals(List.of("match POST /item", "handler POST /item"), run(router, HttpRequest.POST("/item", "")).trace);
    }

    @Test
    void aFilterThatIsNotPreMatchingDoesNotChangeTheMatchedRoute() {
        Router router = router(routes -> {
            routes.GET("/old", OK);
            routes.GET("/new", OK);
            routes.filter("/**").before(request -> {
                request.uri(URI.create("/new"));
            });
        });

        // the route of /old was matched before the filter: it runs with the changed request
        assertEquals(List.of("match GET /old", "handler GET /new"), run(router, HttpRequest.GET("/old")).trace);
    }

    @Test
    void aPreMatchingServerFilterIsSelectedByTheRequestAsItWasReceived() {
        Router router = router(routes -> {
            routes.GET("/b", OK);
            routes.filter("/a").preMatching().before(request -> {
                request.uri(URI.create("/b"));
            });
            routes.filter("/b").preMatching().before(request -> trace(request, "b"));
        });

        Run run = run(router, HttpRequest.GET("/a"));
        assertEquals(List.of("match GET /b", "handler GET /b"), run.trace);
        assertFalse(run.handled().getAttribute("trace").isPresent());
    }

    @Test
    void theRouteTheGroupAndTheServerFiltersChangeTheHeadersOfTheRequest() {
        Router router = router(routes -> {
            routes.filter("/**").beforeReplacing(request -> request.header("X-Server", "server"));
            routes.group(group -> {
                group.before(request -> {
                    request.getHeaders().set("X-Client", "group");
                });
                group.GET("/headers", OK).before(request -> {
                    request.getHeaders().add("X-Route", request.getHeaders().get("X-Client") + "-route");
                });
            });
        });

        // a request that cannot be mutated: the filters are given a mutable wrapper of it
        HttpRequest<?> request = new HttpRequestWrapper<>(HttpRequest.GET("/headers").header("X-Client", "client"));
        Run run = run(router, request);
        assertEquals(List.of("match GET /headers", "handler GET /headers"), run.trace);
        assertEquals("server", run.handled().getHeaders().get("X-Server"));
        assertEquals("group", run.handled().getHeaders().get("X-Client"));
        assertEquals("group-route", run.handled().getHeaders().get("X-Route"));
    }

    @Test
    void aMutableRequestIsChangedInPlace() {
        Router router = router(routes -> routes.GET("/x", OK).before(request -> {
            request.header("X-Changed", "true");
        }));
        MutableHttpRequest<?> request = HttpRequest.GET("/x");

        Run run = run(router, request);
        assertSame(request, run.handled());
        assertEquals("true", request.getHeaders().get("X-Changed"));
    }

    @Test
    void aRequestFilterContinuesWithTheRequestItReturns() {
        List<HttpRequest<?>> replacements = new ArrayList<>();
        Router router = router(routes -> routes.GET("/x", OK)
            .beforeReplacing(request -> {
                HttpRequest<?> replacement = new HttpRequestWrapper<>(request);
                replacements.add(replacement);
                return replacement;
            })
            .beforeAsync(request -> {
                // the next filter is given a mutable wrapper of the request the previous one returned
                assertInstanceOf(MutableHttpRequest.class, request);
                request.header("X-Async", "true");
                return CompletableFuture.completedFuture(null);
            })
            .beforeReplacing((request, propagatedContext) -> {
                assertEquals("true", request.getHeaders().get("X-Async"));
                HttpRequest<?> replacement = withMethod(request, HttpMethod.PATCH);
                replacements.add(replacement);
                return replacement;
            }));

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(List.of("match GET /x", "handler PATCH /x"), run.trace);
        assertEquals(2, replacements.size());
        assertSame(replacements.get(1), run.handled());
    }

    @Test
    void aRequestFilterThatAnswersStopsTheChainLikeBefore() {
        Router router = router(routes -> routes.GET("/x", OK)
            .beforeReplacing(request -> HttpResponse.status(HttpStatus.UNAUTHORIZED))
            .beforeReplacing(request -> {
                throw new AssertionError("not called");
            }));

        Run run = run(router, HttpRequest.GET("/x"));
        assertEquals(List.of("match GET /x"), run.trace);
        assertEquals(HttpStatus.UNAUTHORIZED, run.response.getStatus());
        assertNull(run.handledRequest);
    }

    private static HttpResponse<?> trace(HttpRequest<?> request, String step) {
        String trace = request.getAttribute("trace", String.class).orElse(null);
        request.setAttribute("trace", trace == null ? step : trace + "," + step);
        return null;
    }

    private static <B> HttpRequest<B> withMethod(HttpRequest<B> request, HttpMethod method) {
        return new HttpRequestWrapper<>(request) {
            @Override
            public HttpMethod getMethod() {
                return method;
            }

            @Override
            public String getMethodName() {
                return method.name();
            }
        };
    }

    /**
     * Run the pre-matching filters, match the route with the request they continue with, then run
     * the filters of the match, like the request lifecycle of the server.
     */
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
