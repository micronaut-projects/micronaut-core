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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
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
 * Server filters declared by {@link HttpRoutes} beans with {@link HttpRouteBuilder#filter}: like
 * {@code @ServerFilter} beans, they filter every request their patterns and methods match,
 * ordered together with the filter beans, wherever they are declared.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HttpRoutesServerFiltersTest {
    public static final String SPEC_NAME = "HttpRoutesServerFiltersTest";
    private static final String TRACE = "server-filters-trace";
    private static final String X_TRACE = "X-Trace";
    private static final String ORDERED_TRACE = "fn-5,fn0,bean10,fn20";
    private static final String ORDERED_RESPONSE_TRACE = "fn20,bean10,fn-5";

    @Test
    void aServerFilterOfTheRoutesFiltersAHandlerRouteAControllerRouteAndANotFound() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/sf/fn"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals(ORDERED_TRACE + ",ctx=fn", response.getBody(String.class).orElseThrow());
                    assertEquals(ORDERED_RESPONSE_TRACE, response.getHeaders().get(X_TRACE));
                })
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/sf/controller"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals(ORDERED_TRACE + ",ctx=fn", response.getBody(String.class).orElseThrow());
                    assertEquals(ORDERED_RESPONSE_TRACE, response.getHeaders().get(X_TRACE));
                })
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/sf/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .header(X_TRACE, ORDERED_RESPONSE_TRACE)
                .build());
        }
    }

    @Test
    void aServerFilterOfTheRoutesAppliesToItsMethodsOnly() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/sf/fn", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Post-Only", "true")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/sf/fn"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> assertNull(response.getHeaders().get("X-Post-Only")))
                .build());
        }
    }

    @Test
    void aServerFilterOfTheRoutesAppliesToItsPatternsOnly() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/sf/only/route"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Only", "true")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/sf/fn"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> assertNull(response.getHeaders().get("X-Only")))
                .build());
            // outside of every pattern
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/not-filtered"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("ctx=none", response.getBody(String.class).orElseThrow());
                    assertNull(response.getHeaders().get(X_TRACE));
                })
                .build());
        }
    }

    @Test
    void aServerFilterDeclaredInAGroupIsGlobal() throws IOException {
        try (ServerUnderTest server = server()) {
            // the prefix of the group does not apply to its pattern, and it filters a route outside
            // of the group and a request no route matches
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/sf/fn"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-In-Group", "true")
                .header("X-Async", "true")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/sf/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .header("X-In-Group", "true")
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

    private static String describe(HttpRequest<?> request) {
        String trace = request.getAttribute(TRACE, String.class).map(t -> t + ",").orElse("");
        return trace + "ctx=" + PropagatedContext.getOrEmpty().find(FilterTrace.class).map(FilterTrace::id).orElse("none");
    }

    record FilterTrace(String id) implements PropagatedContextElement {
    }

    /**
     * The server filters: declared by one bean, they filter the routes of the others.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FilterRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.filter("/sf/**").before(request -> trace(request, "fn0"));
            routes.filter("/sf/**").order(-5)
                .before(request -> trace(request, "fn-5"))
                .after((request, response) -> trace(response, "fn-5"));
            routes.filter("/sf/**").order(20)
                .before(request -> trace(request, "fn20"))
                .after((request, response) -> trace(response, "fn20"));
            routes.filter("/sf/**").methods(HttpMethod.POST).after((request, response) -> response.header("X-Post-Only", "true"));
            routes.filter("/sf/only/**").after((request, response) -> response.header("X-Only", "true"));
            routes.filter("/sf/**").before(TaskExecutors.IO, (request, propagatedContext) -> {
                propagatedContext.add(new FilterTrace("fn"));
                return null;
            });
            routes.path("/group", group -> {
                group.filter("/sf/**").after((request, response) -> response.header("X-In-Group", "true"));
                group.filter("/sf/**").afterAsync((request, response) -> CompletableFuture.completedFuture(response.header("X-Async", "true")));
            });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/sf/fn", (request, pathVariables) -> text(describe(request)));
            routes.POST("/sf/fn", (request, pathVariables) -> text(describe(request))).consumesAll();
            routes.GET("/sf/only/route", (request, pathVariables) -> text(describe(request)));
            routes.GET("/not-filtered", (request, pathVariables) -> text(describe(request)));
        }

        private static HttpResponse<?> text(String body) {
            return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Controller("/sf")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FilteredController {
        @Get("/controller")
        @Produces(MediaType.TEXT_PLAIN)
        String controller(HttpRequest<?> request) {
            return describe(request);
        }
    }

    @ServerFilter("/sf/**")
    @Order(10)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OrderedFilterBean {
        @RequestFilter
        void filterRequest(HttpRequest<?> request) {
            trace(request, "bean10");
        }

        @ResponseFilter
        void filterResponse(MutableHttpResponse<?> response) {
            trace(response, "bean10");
        }
    }
}
