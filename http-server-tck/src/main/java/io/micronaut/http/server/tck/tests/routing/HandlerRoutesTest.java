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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.HttpRoutes;
import io.micronaut.web.router.PathVariables;
import io.micronaut.web.router.RouteSource;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Routes to handler functions, declared by {@link HttpRoutes} beans or published at runtime by a
 * {@link RouteSource}, run like controller routes: filters and error routes apply, bodies are
 * decoded and encoded, {@code GET} routes answer {@code HEAD}, and a wrong method is answered
 * with 405.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesTest {
    public static final String SPEC_NAME = "HandlerRoutesTest";

    @Test
    void handlerRouteIsHandledAndFiltered() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Hello Fred")
                .headers(Map.of("X-Fn-Filter", "true"))
                .build());
        }
    }

    @Test
    void headRequestIsHandledByTheGetRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
        }
    }

    @Test
    void bodyIsDecodedAndTheResultEncoded() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/items", Map.of("name", "apple")), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("{\"saved\":\"apple\"}")
                .build());
        }
    }

    @Test
    void asyncHandlerCompletesTheResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/async"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async")
                .build());
        }
    }

    @Test
    void errorRouteHandlesTheHandlerException() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/fail"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("handled checked failure")
                .build());
        }
    }

    @Test
    void wrongMethodIsNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void runtimeRoutesUseHandlers() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn-dynamic/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());

            server.getApplicationContext().getBean(DynamicHandlerRoutes.class).enable();

            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn-dynamic/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("dynamic /fn-dynamic/x")
                .headers(Map.of("X-Fn-Filter", "true"))
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    static final class CheckedFailure extends Exception {
        CheckedFailure(String message) {
            super(message);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        @Named("fn")
        HttpRoutes fnRoutes() {
            return routes -> {
                routes.GET("/fn/hello/{name}", request ->
                    HttpResponse.ok("Hello " + PathVariables.of(request).get("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.POST("/fn/items", Argument.mapOf(String.class, String.class), (request, item) ->
                    HttpResponse.created(Map.of("saved", item.get("name"))));
                routes.handleAsync(HttpMethod.GET, "/fn/async", request ->
                    CompletableFuture.supplyAsync(() -> HttpResponse.ok("async").contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.GET("/fn/fail", request -> {
                    throw new CheckedFailure("checked failure");
                });
            };
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DynamicHandlerRoutes implements RouteSource {
        private final RouteTableFactory tables;
        private volatile RouteTable current = RouteTable.empty();

        DynamicHandlerRoutes(RouteTableFactory tables) {
            this.tables = tables;
        }

        void enable() {
            current = tables.build(routes -> routes.GET("/fn-dynamic/{+path}", request ->
                HttpResponse.ok("dynamic " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE)));
        }

        @Override
        public RouteTable snapshot() {
            return current;
        }
    }

    @ServerFilter({"/fn/**", "/fn-dynamic/**"})
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FnFilter {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Fn-Filter", "true");
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Errors {
        @Error(global = true, exception = CheckedFailure.class)
        HttpResponse<String> checkedFailure(CheckedFailure failure) {
            return HttpResponse.<String>status(HttpStatus.CONFLICT).body("handled " + failure.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
