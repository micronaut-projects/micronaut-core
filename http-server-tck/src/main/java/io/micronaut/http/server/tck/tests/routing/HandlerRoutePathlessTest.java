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
import io.micronaut.core.annotation.Introspected;
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
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Handler routes declared without a path, like a controller method mapped without a URI: at the
 * prefix of their group, at the root, at the prefix of the locator in a located table, under the
 * context path, with their implicit {@code HEAD} route and a {@code 405} for another method.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutePathlessTest {
    public static final String SPEC_NAME = "HandlerRoutePathlessTest";

    @Test
    void aRouteWithoutAPathIsAtThePrefixOfItsGroupLikeAControllerMethod() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            assertBody(server, HttpRequest.GET("/pathless/users"), "users");
            assertBody(server, HttpRequest.POST("/pathless/users", "{\"name\":\"ann\"}").contentType(MediaType.APPLICATION_JSON_TYPE), "created ann");
            assertBody(server, HttpRequest.GET("/pathless/ctl"), "controller");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/pathless/users"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.PUT("/pathless/users", "{}"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void aRouteWithoutAPathIsAtTheRootAndAtThePrefixOfALocator() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            assertBody(server, HttpRequest.GET("/"), "root");
            assertBody(server, HttpRequest.GET("/pathless/items/a"), "item a");
            assertBody(server, HttpRequest.GET("/pathless/items/a/details"), "details of a");
        }
    }

    @Test
    void aRouteWithoutAPathIsUnderTheContextPath() throws IOException {
        try (ServerUnderTest server = server(Map.of("micronaut.server.context-path", "/cp"))) {
            assertBody(server, HttpRequest.GET("/cp"), "root");
            assertBody(server, HttpRequest.GET("/cp/pathless/users"), "users");
            assertBody(server, HttpRequest.GET("/cp/pathless/ctl"), "controller");
        }
    }

    private static void assertBody(ServerUnderTest server, HttpRequest<?> request, String body) {
        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .body(body)
            .build());
    }

    private static ServerUnderTest server(Map<String, Object> configuration) {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration);
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Introspected
    record User(String name) {
    }

    record Item(String name) {
    }

    @Controller("/pathless/ctl")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PathlessController {
        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String get() {
            return "controller";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PathlessRoutes implements HttpRoutes {
        private final RouteTable items;

        PathlessRoutes(RouteTableFactory tables) {
            this.items = tables.buildLocatedHttpRoutes(Item.class, item -> {
                item.handle(io.micronaut.http.HttpMethod.GET, (request, pathVariables, target) -> text("item " + target.name()));
                item.GET("/details", (request, pathVariables) -> text("details of " + pathVariables.locatedTarget(Item.class).name()));
            });
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET((request, pathVariables) -> text("root"));
            routes.path("/pathless/users", users -> {
                users.GET((request, pathVariables) -> text("users"));
                users.POST(User.class, (request, pathVariables, user) -> text("created " + user.name()));
            });
            routes.locate("/pathless/items/{name}", (request, pathVariables) -> new Item(pathVariables.getString("name")), target -> items);
        }
    }
}
