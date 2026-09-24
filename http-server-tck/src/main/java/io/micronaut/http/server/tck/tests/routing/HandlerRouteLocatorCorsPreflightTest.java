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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CORS preflight request for a path that only a locator route serves knows the routes of the
 * located target, also when the locator is asynchronous and locates the target later.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteLocatorCorsPreflightTest {
    public static final String SPEC_NAME = "HandlerRouteLocatorCorsPreflightTest";
    private static final String ORIGIN = "https://foo.com";
    private static final Map<String, Object> CONFIG = Map.of(
        "micronaut.server.cors.enabled", StringUtils.TRUE,
        "micronaut.server.cors.configurations.web.allowed-origins", List.of(ORIGIN));

    @ParameterizedTest
    @ValueSource(strings = {"/cors-sync/5/items", "/cors-async/5/items"})
    void aPreflightForTheMethodOfTheLocatedRouteIsAllowed(String path) throws IOException {
        asserts(SPEC_NAME, CONFIG, preflight(path, HttpMethod.GET),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals(List.of(ORIGIN), response.getHeaders().getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
                    assertTrue(response.getHeaders().getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).stream()
                        .anyMatch(methods -> methods.contains(HttpMethod.GET.name())), path);
                })
                .build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cors-sync/5/items", "/cors-async/5/items"})
    void aPreflightForAMethodTheLocatedTableDoesNotHaveIsForbidden(String path) throws IOException {
        asserts(SPEC_NAME, CONFIG, preflight(path, HttpMethod.DELETE),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build()));
    }

    @Test
    void theActualRequestIsAnsweredWithTheCorsHeaders() throws IOException {
        asserts(SPEC_NAME, CONFIG, HttpRequest.GET("/cors-async/5/items").header(HttpHeaders.ORIGIN, ORIGIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
                .build()));
    }

    private static MutableHttpRequest<?> preflight(String path, HttpMethod method) {
        return HttpRequest.OPTIONS(path)
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method.name());
    }

    private static <T> T later(T value) {
        try {
            // after the router asked for the target
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CorsLocatorRoutes implements HttpRoutes {
        private final RouteTableFactory tables;
        private final ExecutorService executor;

        CorsLocatorRoutes(RouteTableFactory tables, @Named(TaskExecutors.IO) ExecutorService executor) {
            this.tables = tables;
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) ->
                HttpResponse.ok("item").contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.locate("/cors-sync/{id}", (request, pathVariables) -> "order", order -> items);
            routes.locateAsync("/cors-async/{id}", (request, pathVariables) ->
                CompletableFuture.supplyAsync(() -> later("order"), executor), order -> items);
        }
    }
}
