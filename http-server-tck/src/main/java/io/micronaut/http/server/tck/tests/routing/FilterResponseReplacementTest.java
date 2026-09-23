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
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestHandler;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Response filters declared as functions replace the response, like {@code @ResponseFilter}
 * methods returning a response: on a route, a group and a server filter, synchronously, on an
 * executor and asynchronously, and the response filters after them and the client see the
 * replacement.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterResponseReplacementTest {
    public static final String SPEC_NAME = "FilterResponseReplacementTest";

    private static final RequestHandler ROUTE = (request, pathVariables) -> text(HttpResponse.ok("route"));

    @Test
    void aRouteResponseFilterReplacesTheStatusTheHeadersAndTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/route"), HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .header("X-Replaced", "route")
                .body("replaced route")
                .build());
        }
    }

    @Test
    void theResponseFiltersAfterTheReplacementAndTheFilterBeansSeeIt() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/outer/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .header("X-Route-After", "201:replaced")
                .header("X-Group-After", "201:replaced")
                .header("X-Server-After", "201:replaced")
                .header("X-Bean-After", "201:replaced")
                .body("replaced")
                .build());
        }
    }

    @Test
    void theFiltersOfAGroupAndOfAServerFilterReplaceTheResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/group/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .header("X-Replaced", "group")
                .body("group replaced route")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/server/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .header("X-Replaced", "server")
                .body("server replaced route")
                .build());
        }
    }

    @Test
    void anAsynchronousFilterAndAFilterOnAnExecutorReplaceTheResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            // the second filter changes the replacement of the first in place, and keeps it
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/async"), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .header("X-Async-Kept", "201")
                .body("async replaced")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/executor"), HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .body("executor replaced")
                .build());
        }
    }

    @Test
    void nullKeepsTheResponseTheFilterChanged() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/null"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Kept", "true")
                .header("X-Async-Kept", "true")
                .body("route")
                .build());
        }
    }

    @Test
    void aContextFilterReplacesTheResponseAndChangesTheContextOfTheFiltersAfterIt() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/context"), HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .header("X-Context", "replacer")
                .body("context replaced")
                .build());
        }
    }

    @Test
    void aPreMatchingServerFilterReplacesTheResponseItsRequestFilterAnsweredWith() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/rr/pre/blocked"), HttpResponseAssertion.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .header("X-Pre-Replaced", "403")
                .build());
            // the response of the route is not replaced
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/rr/pre/ok"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("route")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static MutableHttpResponse<?> text(MutableHttpResponse<?> response) {
        return response.contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static MutableHttpResponse<?> replacement(HttpStatus status, String body) {
        return text(HttpResponse.status(status).body(body));
    }

    private static String seen(HttpResponse<?> response) {
        return response.code() + ":" + response.body();
    }

    record Marker(String name) implements PropagatedContextElement {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/rr/route", ROUTE)
                .afterReplacing((request, response) -> replacement(HttpStatus.ACCEPTED, "replaced " + response.body())
                    .header("X-Replaced", "route"));
            routes.path("/rr/outer", group -> {
                group.after((request, response) -> response.header("X-Group-After", seen(response)));
                group.GET("/x", ROUTE)
                    // not mutable: the filters after it, e.g. the filter method, get it mutable
                    .afterReplacing((request, response) -> new HttpResponseWrapper<>(replacement(HttpStatus.CREATED, "replaced")))
                    .after((request, response) -> response.header("X-Route-After", seen(response)));
            });
            routes.filter("/rr/outer/**").after((request, response) -> response.header("X-Server-After", seen(response)));
            routes.path("/rr/group", group -> {
                group.afterReplacing((request, response) -> replacement(HttpStatus.ACCEPTED, "group replaced " + response.body())
                    .header("X-Replaced", "group"));
                group.GET("/x", ROUTE);
            });
            routes.GET("/rr/server/x", ROUTE);
            routes.filter("/rr/server/**").afterReplacing((request, response) -> replacement(HttpStatus.ACCEPTED, "server replaced " + response.body())
                .header("X-Replaced", "server"));
            routes.GET("/rr/async", ROUTE)
                .afterReplacingAsync((request, response) -> CompletableFuture.completedFuture(replacement(HttpStatus.CREATED, "async replaced")))
                .afterReplacingAsync((request, response, propagatedContext) -> {
                    response.header("X-Async-Kept", String.valueOf(response.code()));
                    return CompletableFuture.completedFuture(null);
                });
            routes.GET("/rr/executor", ROUTE)
                .afterReplacing(TaskExecutors.BLOCKING, (request, response) -> replacement(HttpStatus.ACCEPTED, "executor replaced"));
            routes.GET("/rr/null", ROUTE)
                .afterReplacing((request, response) -> {
                    response.header("X-Kept", "true");
                    return null;
                })
                .afterReplacingAsync((request, response) -> {
                    response.header("X-Async-Kept", "true");
                    return CompletableFuture.completedFuture(null);
                });
            routes.GET("/rr/context", ROUTE)
                .afterReplacing((request, response, propagatedContext) -> {
                    propagatedContext.add(new Marker("replacer"));
                    return replacement(HttpStatus.ACCEPTED, "context replaced");
                })
                .after((request, response) -> response.header("X-Context",
                    PropagatedContext.getOrEmpty().find(Marker.class).map(Marker::name).orElse("none")));
            routes.GET("/rr/pre/ok", ROUTE);
            routes.filter("/rr/pre/**").preMatching()
                .before(request -> request.getPath().equals("/rr/pre/blocked") ? HttpResponse.status(HttpStatus.FORBIDDEN) : null)
                .afterReplacing((request, response) -> response.code() == HttpStatus.FORBIDDEN.getCode()
                    ? HttpResponse.status(HttpStatus.UNAUTHORIZED).header("X-Pre-Replaced", String.valueOf(response.code()))
                    : null);
        }
    }

    @ServerFilter("/rr/outer/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FilterBean {
        @ResponseFilter
        void after(MutableHttpResponse<?> response) {
            response.header("X-Bean-After", seen(response));
        }
    }
}
