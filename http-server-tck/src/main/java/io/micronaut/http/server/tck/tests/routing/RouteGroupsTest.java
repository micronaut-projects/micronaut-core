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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Groups of handler routes, {@link HttpRouteBuilder#group} and {@link HttpRouteBuilder#path}: the
 * filters of a group apply to every route of the group, after the server filters and before the
 * filters of the route, and only to the requests a route of the group matched.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RouteGroupsTest {
    public static final String SPEC_NAME = "RouteGroupsTest";
    private static final String TRACE = "route-groups-trace";
    private static final String X_TRACE = "X-Trace";

    @Test
    void aGroupFilterAppliesToEveryRouteOfTheGroup() throws IOException {
        try (ServerUnderTest server = server()) {
            // declared before the filters of the group, after them, and in a nested group
            for (String path : new String[]{"/groups/first", "/groups/last", "/groups/inner/route"}) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header("X-Outer", "true")
                    .build());
            }
        }
    }

    @Test
    void aPrefixGroupHasItsOwnFiltersAndTheRouteItsOwn() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/groups/first"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    // the filters of the group, not those of the routes of the group
                    assertEquals("server,outer1,outer2", response.getBody(String.class).orElseThrow());
                    assertEquals("outer1,outer2,server", response.getHeaders().get(X_TRACE));
                })
                .build());
        }
    }

    @Test
    void filtersRunServerOuterInnerRouteAndResponseFiltersTheOtherWay() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/groups/inner/route"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("server,outer1,outer2,inner,route1,route2", response.getBody(String.class).orElseThrow());
                    assertEquals("route1,route2,inner,outer1,outer2,server", response.getHeaders().get(X_TRACE));
                })
                .build());
        }
    }

    @Test
    void theResponseFiltersOfTheGroupFilterARejectionOfTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/groups/rejected"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .header(X_TRACE, "route-after,outer1,outer2,server")
                .build());
        }
    }

    @Test
    void aRequestUnderThePrefixThatNoRouteMatchesDoesNotRunTheGroupFilters() throws IOException {
        try (ServerUnderTest server = server()) {
            // 404: the server filter of the prefix runs, the group filters do not
            AssertionUtils.assertThrows(server, HttpRequest.GET("/groups/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .assertResponse(response -> {
                    assertEquals("server", response.getHeaders().get(X_TRACE));
                    assertNull(response.getHeaders().get("X-Outer"));
                })
                .build());
            // 405: a route of the group matches the path with another method
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/groups/first"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .assertResponse(response -> {
                    assertEquals("server", response.getHeaders().get(X_TRACE));
                    assertNull(response.getHeaders().get("X-Outer"));
                })
                .build());
        }
    }

    @Test
    void theResponseFiltersOfTheGroupSeeTheErrorRouteResponseLikeThoseOfTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/groups/fails"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .assertResponse(response -> {
                    assertEquals("group failure", response.getBody(String.class).orElseThrow());
                    // the parity: the response filters of the group filter what those of the route filter
                    assertEquals(response.getHeaders().get("X-Route-After"), response.getHeaders().get("X-Group-After"));
                    assertEquals("true", response.getHeaders().get("X-Route-After"));
                })
                .build());
        }
    }

    @Test
    void asyncAndContextFiltersOfAGroupChangeTheContextOfTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/groups/context/sync", "/groups/context/async"}) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        assertEquals("outer=o,inner=i,blocking=b", response.getBody(String.class).orElseThrow(), path);
                        // the response filters see the context the request filters produced
                        assertEquals("outer=o,inner=i,blocking=b", response.getHeaders().get("X-Context-After"), path);
                        assertEquals("true", response.getHeaders().get("X-Async-After"), path);
                    })
                    .build());
            }
            // the error route sees the context of the group filters
            AssertionUtils.assertThrows(server, HttpRequest.GET("/groups/context/fails"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("outer=o,inner=i,blocking=b")
                .build());
        }
    }

    @Test
    void theFiltersOfAGroupOfOneBeanDoNotApplyToTheRoutesOfAnotherBean() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/bean-a"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Bean-A", "true")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/bean-b"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> assertNull(response.getHeaders().get("X-Bean-A")))
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> trace(HttpRequest<?> request, String step) {
        String trace = request.getAttribute(TRACE, String.class).orElse(null);
        request.setAttribute(TRACE, trace == null ? step : trace + "," + step);
        return null;
    }

    private static void trace(MutableHttpResponse<?> response, String step) {
        String trace = response.getHeaders().get(X_TRACE);
        response.getHeaders().set(X_TRACE, trace == null ? step : trace + "," + step);
    }

    private static HttpResponse<?> text(HttpStatus status, String body) {
        return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static HttpResponse<?> text(String body) {
        return text(HttpStatus.OK, body);
    }

    private static String describeContext() {
        PropagatedContext context = PropagatedContext.getOrEmpty();
        return "outer=" + context.find(OuterTrace.class).map(OuterTrace::id).orElse("none")
            + ",inner=" + context.find(InnerTrace.class).map(InnerTrace::id).orElse("none")
            + ",blocking=" + context.find(BlockingTrace.class).map(BlockingTrace::id).orElse("none");
    }

    record OuterTrace(String id) implements PropagatedContextElement {
    }

    record InnerTrace(String id) implements PropagatedContextElement {
    }

    record BlockingTrace(String id) implements PropagatedContextElement {
    }

    static final class GroupFailure extends RuntimeException {
        GroupFailure() {
            super("group failure");
        }
    }

    static final class ContextFailure extends RuntimeException {
        ContextFailure() {
            super("context failure");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GroupRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/groups", groups -> {
                groups.GET("/first", (request, pathVariables) -> text(request.getAttribute(TRACE, String.class).orElse("")));
                groups.before(request -> trace(request, "outer1"));
                groups.after((request, response) -> {
                    trace(response, "outer1");
                    response.header("X-Outer", "true");
                });
                groups.path("/inner", inner -> {
                    inner.GET("/route", (request, pathVariables) -> text(request.getAttribute(TRACE, String.class).orElse("")))
                        .before(request -> trace(request, "route1"))
                        .after((request, response) -> trace(response, "route1"))
                        .before(request -> trace(request, "route2"))
                        .after((request, response) -> trace(response, "route2"));
                    inner.before(request -> trace(request, "inner"));
                    inner.after((request, response) -> trace(response, "inner"));
                });
                groups.GET("/rejected", (request, pathVariables) -> text("not rejected"))
                    .before(request -> HttpResponse.status(HttpStatus.FORBIDDEN))
                    .after((request, response) -> trace(response, "route-after"));
                groups.GET("/fails", (request, pathVariables) -> {
                    throw new GroupFailure();
                }).after((request, response) -> response.header("X-Route-After", "true"));
                groups.after((request, response) -> response.header("X-Group-After", "true"));
                groups.before(request -> trace(request, "outer2"));
                groups.after((request, response) -> trace(response, "outer2"));
                groups.GET("/last", (request, pathVariables) -> text(request.getAttribute(TRACE, String.class).orElse("")));
            });
            routes.error(GroupFailure.class, (request, error) -> text(HttpStatus.CONFLICT, error.getMessage()));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ContextGroupRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/groups/context", outer -> {
                outer.before((request, propagatedContext) -> {
                    propagatedContext.add(new OuterTrace("o"));
                    return null;
                });
                outer.group(inner -> {
                    inner.beforeAsync((request, propagatedContext) -> {
                        propagatedContext.add(new InnerTrace("i"));
                        return CompletableFuture.completedFuture(null);
                    });
                    inner.before(TaskExecutors.IO, (request, propagatedContext) -> {
                        propagatedContext.add(new BlockingTrace("b"));
                        return null;
                    });
                    inner.after((request, response, propagatedContext) -> response.header("X-Context-After", describeContext()));
                    inner.afterAsync((request, response) -> CompletableFuture.completedFuture(response.header("X-Async-After", "true")));
                    inner.GET("/sync", (request, pathVariables) -> text(describeContext()));
                    inner.asyncGET("/async", (request, pathVariables) -> CompletableFuture.completedFuture(text(describeContext())));
                    inner.GET("/fails", (request, pathVariables) -> {
                        throw new ContextFailure();
                    });
                });
            });
            routes.error(ContextFailure.class, (request, error) -> text(HttpStatus.CONFLICT, describeContext()));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BeanARoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.group(all -> {
                all.after((request, response) -> response.header("X-Bean-A", "true"));
                all.GET("/bean-a", (request, pathVariables) -> text("a"));
            });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BeanBRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/bean-b", (request, pathVariables) -> text("b"));
        }
    }

    @ServerFilter("/groups/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PrefixServerFilter {
        @RequestFilter
        void filterRequest(HttpRequest<?> request) {
            if (!request.getPath().startsWith("/groups/context")) {
                trace(request, "server");
            }
        }

        @ResponseFilter
        void filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
            if (!request.getPath().startsWith("/groups/context")) {
                trace(response, "server");
            }
        }
    }
}
