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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The routes of {@link HttpRouteBuilder#any(String, io.micronaut.web.router.builder.RequestHandler)}:
 * every method, standard or custom, behind a route of a specific method on the same path, an
 * implicit {@code HEAD} route and a CORS preflight, and never a {@code 405}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteAnyMethodTest {
    public static final String SPEC_NAME = "HandlerRouteAnyMethodTest";
    private static final String ROUTE = "X-Route";

    @Test
    void anyAnswersEveryStandardMethod() throws IOException {
        try (ServerUnderTest server = server()) {
            for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
                HttpRequest<?> request = method.permitsRequestBody()
                    ? HttpRequest.create(method, "/any/things/1").body("x").contentType(MediaType.TEXT_PLAIN_TYPE)
                    : HttpRequest.create(method, "/any/things/1");
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("any " + method.name() + " 1")
                    .build());
            }
        }
    }

    @Test
    void anyAnswersACustomMethod() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.create(HttpMethod.CUSTOM, "/any/things/1", "REPORT"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("any REPORT 1")
                .build());
        }
    }

    @Test
    void aRouteOfASpecificMethodOnTheSamePathWins() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/any/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("get items")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.DELETE("/any/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("any DELETE items")
                .build());
        }
    }

    @Test
    void theImplicitHeadRouteOfAGetRouteWinsAndAnyAnswersOptions() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/any/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header(ROUTE, "get")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/any/things/2"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header(ROUTE, "any")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.OPTIONS("/any/items"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header(ROUTE, "any")
                .build());
        }
    }

    @Test
    void theCorsFilterAnswersAPreflightRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.OPTIONS("/any/items")
                    .header(HttpHeaders.ORIGIN, "https://example.com")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://example.com")
                    .assertResponse(response -> assertFalse(response.getHeaders().contains(ROUTE), "the preflight is not routed"))
                    .build());
        }
    }

    @Test
    void aPathWithAnAnyRouteIsNeverAnsweredWith405() throws IOException {
        try (ServerUnderTest server = server()) {
            // only POST is declared besides any: the other methods are answered by any
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.PUT("/any/items", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("any PUT items")
                .build());
            // a path without an any route still is
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/any/only-get"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of("micronaut.server.cors.enabled", true));
    }

    private static HttpResponse<?> text(String route, String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE).header(ROUTE, route);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnyRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.any("/any/things/{id}", (request, pathVariables) -> text("any", "any " + request.getMethodName() + " " + pathVariables.getLong("id")))
                .consumesAll();
            routes.any("/any/items", (request, pathVariables) -> text("any", "any " + request.getMethodName() + " items"))
                .consumesAll();
            routes.GET("/any/items", (request, pathVariables) -> text("get", "get items"));
            routes.POST("/any/items", (request, pathVariables) -> text("post", "post items"));
            routes.GET("/any/only-get", (request, pathVariables) -> text("get", "only get"));
        }
    }
}
