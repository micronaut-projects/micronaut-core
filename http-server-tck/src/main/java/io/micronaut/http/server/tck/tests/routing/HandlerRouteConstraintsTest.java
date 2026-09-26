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
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

/**
 * The constraints on the path variables of handler routes, {@code constrain} on a route or a
 * group: a request whose path variables a route rejects is answered by another route, or with
 * {@code 404}, never with a {@code 405} of the rejected route.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteConstraintsTest {
    public static final String SPEC_NAME = "HandlerRouteConstraintsTest";
    private static final Set<String> SHOPS = Set.of("north", "south");

    @Test
    void aRejectedRequestFallsThroughToAnotherRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/constraints/items/5"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item 5")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/constraints/items/lamp"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item named lamp")
                .build());
        }
    }

    @Test
    void anUnknownValueIsNotFoundAndNotAMethodNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/constraints/shops/north/stock"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("stock of north")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/constraints/shops/west/stock"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            // the other routes of the shop reject the value too: no 405
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/constraints/shops/west/stock"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/constraints/shops/north/stock"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
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
    static class ConstraintRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/constraints/items/{id}", (request, pathVariables) -> text("item " + pathVariables.getLong("id")))
                .constrain("id", Long.class, id -> id > 0)
                .order(-1);
            routes.GET("/constraints/items/{name}", (request, pathVariables) -> text("item named " + pathVariables.getString("name")));
            routes.path("/constraints/shops/{shop}", shop -> {
                shop.constrain("shop", SHOPS);
                shop.GET("/stock", (request, pathVariables) -> text("stock of " + pathVariables.getString("shop")));
                shop.POST("/stock", (request, pathVariables) -> text("restocked " + pathVariables.getString("shop")));
            });
        }
    }
}
