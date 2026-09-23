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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteDeclaration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Typed locators and the route tables of located targets of a type, whose handlers receive the
 * target: {@link io.micronaut.web.router.RouteTableFactory#buildLocatedHttpRoutes(Class, java.util.function.Consumer)}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteTypedLocatorTest {
    public static final String SPEC_NAME = "HandlerRouteTypedLocatorTest";

    @Test
    void theHandlersOfATypedTableReceiveTheTarget() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/shops/5/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("shop 5")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/shops/5/declared"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("declared shop 5")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/shops/5/items", "{\"name\":\"pen\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("shop 5 adds pen")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/shops/5/form", "item=cup")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("shop 5 form cup")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/shops/5/async"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async shop 5")
                .build());
        }
    }

    @Test
    void aNullTargetIsNotFound() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/shops/0/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/async-shops/0/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void anAsynchronousTypedLocatorLocatesTheTarget() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/async-shops/7/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("shop 7")
                .build());
        }
    }

    @Test
    void theTablesOfTypedTargetsLocateAgainFromTheirTarget() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/folders/root/docs/2026/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("root/docs/2026")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/folders/root/docs/async/2026/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("root/docs/2026")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/folders/root/missing/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void aTargetThatIsNotOfTheTypeOfTheTableFails() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/mismatched/1/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build());
        }
    }

    @Test
    void theRouteTableFunctionReceivesTheTypedTarget() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/chosen/100/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("big shop 100")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/chosen/1/name"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("shop 1")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(HttpStatus status, String body) {
        return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    record Shop(long id) {
    }

    public record Item(String name) {
    }

    record Folder(String path, Map<String, Folder> children) {
        @Nullable Folder child(String name) {
            return children.get(name);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TypedLocatorRoutes implements HttpRoutes {
        private final RouteTableFactory tables;
        private final Executor executor;

        TypedLocatorRoutes(RouteTableFactory tables, @Named(TaskExecutors.IO) ExecutorService executor) {
            this.tables = tables;
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            RouteTable shops = tables.buildLocatedHttpRoutes(Shop.class, shop -> {
                shop.handle(HttpMethod.GET, "/name", (request, pathVariables, target) -> text(HttpStatus.OK, "shop " + target.id()));
                shop.handle(RouteDeclaration.of(HttpMethod.GET, "/declared"), (request, pathVariables, target) ->
                    text(HttpStatus.OK, "declared shop " + target.id()));
                shop.handle(HttpMethod.POST, "/items", Argument.of(Item.class), (request, pathVariables, target, item) ->
                    text(HttpStatus.CREATED, "shop " + target.id() + " adds " + item.name()));
                shop.handleForm(HttpMethod.POST, "/form", (request, pathVariables, target, form) ->
                    text(HttpStatus.OK, "shop " + target.id() + " form " + form.getString("item")));
                shop.handleAsync(HttpMethod.GET, "/async", (request, pathVariables, target) ->
                    CompletableFuture.supplyAsync(() -> text(HttpStatus.OK, "async shop " + target.id()), executor));
            });
            // the type of the target is the type the locator returns
            routes.locate("/shops/{id}", (request, pathVariables) -> {
                long id = pathVariables.getLong("id");
                return id == 0 ? null : new Shop(id);
            }, shop -> shops);
            routes.locateAsync("/async-shops/{id}", (request, pathVariables) -> {
                long id = pathVariables.getLong("id");
                return CompletableFuture.supplyAsync(() -> id == 0 ? null : new Shop(id), executor);
            }, shop -> shops);

            RouteTable bigShops = tables.buildLocatedHttpRoutes(Argument.of(Shop.class), shop ->
                shop.handle(HttpMethod.GET, "/name", (request, pathVariables, target) -> text(HttpStatus.OK, "big shop " + target.id())));
            routes.locate("/chosen/{id}", (request, pathVariables) -> new Shop(pathVariables.getLong("id")),
                shop -> shop.id() >= 100 ? bigShops : shops);

            Folder root = new Folder("root", Map.of("docs", new Folder("root/docs", Map.of("2026", new Folder("root/docs/2026", Map.of())))));
            RouteTable[] folders = new RouteTable[1];
            folders[0] = tables.buildLocatedHttpRoutes(Folder.class, folder -> {
                folder.handle(HttpMethod.GET, "/name", (request, pathVariables, current) -> text(HttpStatus.OK, current.path()));
                folder.locateAsync("/async/{child}", (request, pathVariables, parent) ->
                    CompletableFuture.supplyAsync(() -> parent.child(pathVariables.getString("child")), executor), child -> folders[0]);
                folder.locate("/{child}", (request, pathVariables, parent) -> parent.child(pathVariables.getString("child")), child -> folders[0]);
            });
            routes.locate("/folders/root", (request, pathVariables) -> root, folder -> folders[0]);

            // a table for shops given a string
            routes.locate("/mismatched/{id}", (request, pathVariables) -> "not a shop", target -> shops);
        }
    }
}
