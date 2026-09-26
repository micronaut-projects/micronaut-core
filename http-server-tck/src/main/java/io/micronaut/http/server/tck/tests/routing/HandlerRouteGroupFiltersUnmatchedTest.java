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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The filters of a group are route filters: they run only when a route of the group matched the
 * request. A {@code 404} or a {@code 405} under the prefix of the group runs the server filters
 * only, the pre-matching ones included.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteGroupFiltersUnmatchedTest {
    public static final String SPEC_NAME = "HandlerRouteGroupFiltersUnmatchedTest";
    private static final String TRACE = "group-filters-unmatched-trace";

    @Test
    void aMatchedRouteRunsThePreMatchingTheServerAndTheGroupFilters() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/unmatched/route"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("pre,server,group", response.getBody(String.class).orElseThrow());
                    assertEquals("true", response.getHeaders().get("X-Group"));
                    assertEquals("true", response.getHeaders().get("X-Server"));
                    assertEquals("true", response.getHeaders().get("X-Pre-Matching"));
                })
                .build());
        }
    }

    @Test
    void aNotFoundUnderThePrefixRunsTheServerFiltersOnly() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/unmatched/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .assertResponse(serverFiltersOnly())
                .build());
        }
    }

    @Test
    void aMethodNotAllowedUnderThePrefixRunsTheServerFiltersOnly() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/unmatched/route"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .assertResponse(serverFiltersOnly())
                .build());
        }
    }

    private static Consumer<HttpResponse<?>> serverFiltersOnly() {
        return response -> {
            assertEquals("true", response.getHeaders().get("X-Pre-Matching"));
            assertEquals("true", response.getHeaders().get("X-Server"));
            assertEquals("pre,server", response.getHeaders().get("X-Request-Trace"));
            assertNull(response.getHeaders().get("X-Group"));
        };
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static void trace(HttpRequest<?> request, String step) {
        String trace = request.getAttribute(TRACE, String.class).orElse(null);
        request.setAttribute(TRACE, trace == null ? step : trace + "," + step);
    }

    private static void traceHeader(HttpRequest<?> request, MutableHttpResponse<?> response) {
        request.getAttribute(TRACE, String.class).ifPresent(trace -> response.header("X-Request-Trace", trace));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GroupRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.filter("/unmatched/**").preMatching()
                .before(request -> trace(request, "pre"))
                .and()
                .after((request, response) -> response.header("X-Pre-Matching", "true"));
            routes.filter("/unmatched/**")
                .before(request -> trace(request, "server"))
                .and()
                .after((request, response) -> {
                    response.header("X-Server", "true");
                    traceHeader(request, response);
                });
            routes.path("/unmatched", group -> {
                group.GET("/route", (request, pathVariables) -> HttpResponse.ok(request.getAttribute(TRACE, String.class).orElse(""))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
                group.before(request -> trace(request, "group"));
                group.after((request, response) -> response.header("X-Group", "true"));
            });
        }
    }
}
