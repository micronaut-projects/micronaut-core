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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Handler routes run with the propagated context of the request like controller routes: an
 * element an annotation filter with a {@link MutablePropagatedContext} parameter adds, e.g. an MDC
 * context, is in scope in synchronous and asynchronous handlers, in their continuations on a
 * propagating executor, in the route filters, and in error and status routes. Route filters
 * change the context like annotation filters.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesPropagatedContextTest {
    public static final String SPEC_NAME = "HandlerRoutesPropagatedContextTest";
    private static final String TRACE = "X-Trace";

    @Test
    void controllerSeesTheContextOfTheFilter() throws IOException {
        // the controller use case of the documentation, the reference for the handler routes
        assertDescribed("/propagation/controller", "trace=t1,route=none,mdc=t1,request=/propagation/controller");
    }

    @Test
    void synchronousHandlerSeesTheContextOfTheFilter() throws IOException {
        assertDescribed("/propagation/sync", "trace=t1,route=none,mdc=t1,request=/propagation/sync");
        // on the blocking executor
        assertDescribed("/propagation/blocking", "trace=t1,route=none,mdc=t1,request=/propagation/blocking");
    }

    @Test
    void asynchronousHandlerSeesTheContextOfTheFilter() throws IOException {
        assertDescribed("/propagation/async", "trace=t1,route=none,mdc=t1,request=/propagation/async");
    }

    @Test
    void continuationOnAPropagatingExecutorSeesTheContext() throws IOException {
        // the task submitted to the IO executor, and the continuation of its stage
        assertDescribed("/propagation/async-io",
            "trace=t1,route=none,mdc=t1,request=/propagation/async-io;trace=t1,route=none,mdc=t1,request=/propagation/async-io");
    }

    @Test
    void handlerThatReadsTheBodySeesTheContext() throws IOException {
        // what the continuation of the read sees depends on when the body arrives, like for the
        // CompletableFuture body of a controller, see PropagatedContextParityTest
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/propagation/async-body", "hello").contentType(MediaType.TEXT_PLAIN_TYPE).header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("hello:trace=t1,route=none,mdc=t1,request=/propagation/async-body")
                    .build());
        }
    }

    @Test
    void routeFilterSeesTheContextOfTheFilterAndChangesIt() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/propagation/route-filter").header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("trace=t1,route=r-t1,mdc=route-t1,request=/propagation/route-filter")
                    // the route filters see the context of the annotation filter, and the response
                    // filters the context the request filters produced
                    .headers(Map.of(
                        "X-Before", "trace=t1,route=none,mdc=t1",
                        "X-After", "trace=t1,route=r-t1,mdc=route-t1"
                    ))
                    .build());
        }
    }

    @Test
    void asyncRouteFilterChangesTheContextWhenItsStageCompletes() throws IOException {
        // the filter adds the element in the continuation of a stage completed on the IO executor,
        // the handler completes on the IO executor too
        assertDescribed("/propagation/route-filter-async",
            "trace=t1,route=async-t1,mdc=t1,request=/propagation/route-filter-async;trace=t1,route=async-t1,mdc=t1,request=/propagation/route-filter-async");
    }

    @Test
    void routeFilterOnAnExecutorChangesTheContext() throws IOException {
        assertDescribed("/propagation/route-filter-blocking", "trace=t1,route=blocking-t1,mdc=t1,request=/propagation/route-filter-blocking");
    }

    @Test
    void responseFilterChangesTheContextOfTheNextResponseFilter() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/propagation/response-filter").header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("trace=t1,route=r-t1,mdc=t1,request=/propagation/response-filter")
                    .headers(Map.of(
                        "X-First", "trace=t1,route=r-t1,mdc=t1",
                        // the first response filter removed the element of the route filter
                        "X-Second", "trace=t1,route=none,mdc=t1",
                        "X-Third", "trace=t1,route=async-removed,mdc=t1"
                    ))
                    .build());
        }
    }

    @Test
    void errorRoutesSeeTheContext() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/propagation/fail").header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.I_AM_A_TEAPOT)
                    .body("error:trace=t1,route=r-t1,mdc=t1,request=/propagation/fail")
                    .build());
            // an asynchronous error route, for the failed stage of an asynchronous handler
            AssertionUtils.assertThrows(server, HttpRequest.GET("/propagation/fail-async").header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.I_AM_A_TEAPOT)
                    .body("async error:trace=t1,route=r-t1,mdc=t1,request=/propagation/fail-async")
                    .build());
            // a status route, for a request that no route matches
            AssertionUtils.assertThrows(server, HttpRequest.GET("/propagation/missing").header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .body("status:trace=t1,route=none,mdc=t1,request=/propagation/missing")
                    .build());
        }
    }

    private static void assertDescribed(String path, String expected) throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path).header(TRACE, "t1"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(expected)
                    .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    /**
     * @return The propagated context, the MDC and the request in scope
     */
    static String describe() {
        return describeContext() + ",request=" + ServerRequestContext.currentRequest().map(HttpRequest::getPath).orElse("none");
    }

    /**
     * @return The propagated context and the MDC in scope
     */
    static String describeContext() {
        PropagatedContext context = PropagatedContext.getOrEmpty();
        return "trace=" + context.find(Trace.class).map(Trace::id).orElse("none")
            + ",route=" + context.find(RouteTrace.class).map(RouteTrace::id).orElse("none")
            + ",mdc=" + Objects.requireNonNullElse(MDC.get("trace"), "none");
    }

    static HttpResponse<?> text(HttpStatus status, String body) {
        return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    static HttpResponse<?> text(String body) {
        return text(HttpStatus.OK, body);
    }

    /**
     * The element the annotation filter adds.
     *
     * @param id The trace
     */
    record Trace(String id) implements PropagatedContextElement {
    }

    /**
     * The element the route filters add.
     *
     * @param id The trace
     */
    record RouteTrace(String id) implements PropagatedContextElement {
    }

    static final class PropagationFailure extends RuntimeException {
        PropagationFailure() {
            super("propagation failure");
        }
    }

    static final class AsyncPropagationFailure extends RuntimeException {
        AsyncPropagationFailure() {
            super("async propagation failure");
        }
    }

    /**
     * Like the MDC filter of the documentation: adds the trace of the request to the propagated
     * context and to the MDC.
     */
    @ServerFilter("/propagation/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TraceFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            String trace = request.getHeaders().get(TRACE);
            if (trace != null) {
                propagatedContext.add(new Trace(trace));
                propagatedContext.add(new MdcPropagationContext(Map.of("trace", trace)));
            }
        }
    }

    @Controller("/propagation/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TraceController {
        @Get(produces = MediaType.TEXT_PLAIN)
        String describe() {
            return HandlerRoutesPropagatedContextTest.describe();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final ExecutorService io;

        Routes(@Named(TaskExecutors.IO) ExecutorService io) {
            this.io = io;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/propagation/sync", (request, pathVariables) -> text(describe()));
            routes.GET("/propagation/blocking", (request, pathVariables) -> text(describe()))
                .executeOn(TaskExecutors.BLOCKING);
            routes.asyncGET("/propagation/async", (request, pathVariables) -> CompletableFuture.completedFuture(text(describe())));
            routes.asyncGET("/propagation/async-io", (request, pathVariables) ->
                // the IO executor propagates the context of the thread that submits the task
                CompletableFuture.supplyAsync(HandlerRoutesPropagatedContextTest::describe, io)
                    .thenApply(onExecutor -> text(onExecutor + ";" + describe())));
            routes.asyncPOST("/propagation/async-body", (request, pathVariables) -> {
                String handler = describe();
                return request.text().thenApply(body -> text(body + ":" + handler));
            })
                .consumes(MediaType.TEXT_PLAIN_TYPE);

            routes.GET("/propagation/route-filter", (request, pathVariables) -> text(describe()))
                .before(request -> {
                    // a route filter runs with the context of the filters before it in scope
                    request.setAttribute("before", describeContext());
                })
                .before((request, propagatedContext) -> {
                    String trace = request.getHeaders().get(TRACE);
                    propagatedContext.add(new RouteTrace("r-" + trace));
                    propagatedContext.add(new MdcPropagationContext(Map.of("trace", "route-" + trace)));
                })
                .after((request, response) -> response
                    .header("X-Before", request.getAttribute("before", String.class).orElse("missing"))
                    .header("X-After", describeContext()));

            routes.asyncGET("/propagation/route-filter-async", (request, pathVariables) ->
                    CompletableFuture.supplyAsync(HandlerRoutesPropagatedContextTest::describe, io)
                        .thenApply(onExecutor -> text(onExecutor + ";" + describe())))
                .beforeAsync((request, propagatedContext) -> CompletableFuture.supplyAsync(() -> request.getHeaders().get(TRACE), io)
                    .thenApply(trace -> {
                        // added once the filter looked the trace up, on the IO executor
                        propagatedContext.add(new RouteTrace("async-" + trace));
                        return null;
                    }));

            routes.GET("/propagation/route-filter-blocking", (request, pathVariables) -> text(describe()))
                .before(TaskExecutors.BLOCKING, (request, propagatedContext) -> {
                    propagatedContext.add(new RouteTrace("blocking-" + request.getHeaders().get(TRACE)));
                });

            routes.GET("/propagation/response-filter", (request, pathVariables) -> text(describe()))
                .before(RouteFilters::addRouteTrace)
                .after((request, response, propagatedContext) -> {
                    response.header("X-First", describeContext());
                    Objects.requireNonNull(propagatedContext.getContext()).find(RouteTrace.class).ifPresent(propagatedContext::remove);
                })
                .afterAsync((request, response, propagatedContext) -> CompletableFuture.runAsync(() -> {
                    response.header("X-Second", describeContext());
                    propagatedContext.add(new RouteTrace("async-removed"));
                }, io))
                .after((request, response) -> response.header("X-Third", describeContext()));

            routes.GET("/propagation/fail", (request, pathVariables) -> {
                throw new PropagationFailure();
            }).before(RouteFilters::addRouteTrace);
            routes.asyncGET("/propagation/fail-async", (request, pathVariables) -> CompletableFuture.supplyAsync(() -> {
                throw new AsyncPropagationFailure();
            }, io)).before(RouteFilters::addRouteTrace);

            routes.error(PropagationFailure.class, (request, error) -> text(HttpStatus.I_AM_A_TEAPOT, "error:" + describe()));
            routes.errorAsync(AsyncPropagationFailure.class, (request, error) ->
                CompletableFuture.completedFuture(text(HttpStatus.I_AM_A_TEAPOT, "async error:" + describe())));
            routes.status(HttpStatus.NOT_FOUND, request -> text(HttpStatus.NOT_FOUND, "status:" + describe()));
        }
    }

    static final class RouteFilters {
        private RouteFilters() {
        }

        static void addRouteTrace(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            propagatedContext.add(new RouteTrace("r-" + request.getHeaders().get(TRACE)));
        }
    }
}
