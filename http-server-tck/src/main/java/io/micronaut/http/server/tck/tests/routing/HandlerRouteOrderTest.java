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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestPredicates;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * The order of handler routes, {@link io.micronaut.web.router.builder.HttpRouteSpec#order} and
 * {@link io.micronaut.web.router.builder.HttpRouteGroup#order}: the route with the lowest order
 * answers a request that equally good routes match, a controller route having the order
 * {@code 0}; routes with the same order stay ambiguous.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteOrderTest {
    public static final String SPEC_NAME = "HandlerRouteOrderTest";

    @Test
    void theLowestOrderAnswersARequestTheConditionsOfTwoRoutesBothAccept() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/order/reports/1?format=csv"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("csv 1")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/order/reports/1"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("report 1")
                .build());
        }
    }

    @Test
    void routesWithTheSameOrderAreAmbiguous() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/order/tie"), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
        }
    }

    @Test
    void aHandlerRouteWithANegativeOrderWinsOverAControllerRouteAndAPositiveOneLoses() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/order/controller/first").accept(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("handler first")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/order/controller/last").accept(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("controller last")
                .build());
        }
    }

    @Test
    void theRoutesOfAGroupHaveTheOrderOfTheGroupAndTheOrderNeverBeatsSpecificity() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/order/pages/about"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("page about")
                .build());
            // the literal route of the group is more specific than the variable routes
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/order/pages/home"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("fallback home")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OrderedRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/order/reports/{id}", (request, pathVariables) -> text("csv " + pathVariables.getLong("id")))
                .where(RequestPredicates.queryParam("format", "csv"))
                .order(-1);
            routes.GET("/order/reports/{id}", (request, pathVariables) -> text("report " + pathVariables.getLong("id")));
            routes.GET("/order/tie", (request, pathVariables) -> text("a")).order(3);
            routes.GET("/order/tie", (request, pathVariables) -> text("b")).order(3);
            // the same media types as the controller routes: only the order tells them apart
            routes.GET("/order/controller/first", (request, pathVariables) -> text("handler first"))
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .order(-1);
            routes.GET("/order/controller/last", (request, pathVariables) -> text("handler last"))
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .order(1);
            routes.path("/order/pages", fallbacks -> {
                fallbacks.order(100);
                fallbacks.GET("/{name}", (request, pathVariables) -> text("fallback " + pathVariables.getString("name")));
                fallbacks.GET("/home", (request, pathVariables) -> text("fallback home"));
            });
            routes.GET("/order/pages/{page}", (request, pathVariables) -> text("page " + pathVariables.getString("page")));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/order/controller")
    static class OrderController {
        @Get("/first")
        @Produces(MediaType.TEXT_PLAIN)
        String first() {
            return "controller first";
        }

        @Get("/last")
        @Produces(MediaType.TEXT_PLAIN)
        String last() {
            return "controller last";
        }
    }
}
