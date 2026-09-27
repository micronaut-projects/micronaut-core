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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes of {@link HttpRouteBuilder#respond}: ordinary routes that answer with a response
 * computed once, or created by a supplier or from the path variables, without a handler reading
 * the request. Filters, conditions, error routes and implicit {@code HEAD} routes apply, and a
 * response computed once is never shared by the requests.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteRespondTest {
    public static final String SPEC_NAME = "HandlerRouteRespondTest";

    @Test
    void aConstantResponseAnswersEachRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            for (int i = 0; i < 2; i++) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/ping"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("pong")
                    .header("X-Constant", "yes")
                    .build());
            }
        }
    }

    @Test
    void theFiltersRunAndTheResponseIsNotSharedBetweenRequests() throws IOException {
        try (ServerUnderTest server = server()) {
            for (int i = 1; i <= 3; i++) {
                int request = i;
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/filtered/ping").header("X-Allowed", "true"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        assertEquals("pong", response.getBody(String.class).orElseThrow());
                        // added by the server filter, the group filter and the route filter to
                        // this response only: a shared response would have the values of the
                        // earlier requests too
                        assertEquals(List.of("server"), response.getHeaders().getAll("X-Server-Filter"));
                        assertEquals(List.of("group"), response.getHeaders().getAll("X-Group-Filter"));
                        assertEquals(1, response.getHeaders().getAll("X-Route-Filter").size());
                        assertTrue(Integer.parseInt(response.getHeaders().get("X-Route-Filter")) >= request);
                        assertEquals(List.of("yes"), response.getHeaders().getAll("X-Constant"));
                    })
                    .build());
            }
            // the filter of the group answers before the route
            AssertionUtils.assertThrows(server, HttpRequest.GET("/respond/filtered/ping"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build());
        }
    }

    @Test
    void theImplicitHeadRouteAnswersWithoutABody() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/respond/ping"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header("X-Constant", "yes")
                .assertResponse(response -> assertFalse(response.getBody(String.class).filter(body -> !body.isEmpty()).isPresent()))
                .build());
        }
    }

    @Test
    void aSupplierCreatesAResponseForEachRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/count"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("1")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/count"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("2")
                .build());
        }
    }

    @Test
    void aFunctionCreatesTheResponseFromThePathVariables() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/hello/World"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Hello World")
                .build());
        }
    }

    @Test
    void theConditionsOfTheRouteApply() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/channel").header("X-Beta", "true"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("beta")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/channel"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("stable")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/respond/beta-only"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void theConstraintsOfTheRouteApply() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/status/north"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("north is open")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/respond/status/west"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void aRouteOfAnotherMethodConsumesAnyContentType() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/respond/accepted", "<ignored/>").contentType(MediaType.APPLICATION_XML_TYPE),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.ACCEPTED)
                    .body("queued")
                    .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/respond/accepted"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void theRouteProducesTheContentTypeOfTheResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/respond/ping").accept(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("pong")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .build());
        }
    }

    @Test
    void theErrorRoutesAnswerASupplierThatThrows() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/respond/failing"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("failed: unavailable")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static MutableHttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RespondRoutes implements HttpRoutes {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicInteger filtered = new AtomicInteger();

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.respond("/respond/ping", text("pong").header("X-Constant", "yes"));
            routes.respond("/respond/count", () -> text(String.valueOf(count.incrementAndGet())));
            routes.respond("/respond/hello/{name}", pathVariables -> text("Hello " + pathVariables.getString("name")));

            routes.filter("/respond/filtered/**").after((request, response) -> response.header("X-Server-Filter", "server"));
            routes.path("/respond/filtered", group -> {
                group.beforeReplacing(request -> request.getHeaders().contains("X-Allowed") ? null : HttpResponse.status(HttpStatus.FORBIDDEN));
                group.after((request, response) -> response.header("X-Group-Filter", "group"));
                group.respond("/ping", text("pong").header("X-Constant", "yes"))
                    .after((request, response) -> response.header("X-Route-Filter", String.valueOf(filtered.incrementAndGet())));
            });

            routes.respond("/respond/channel", text("beta"))
                .where(RouteCondition.header("X-Beta"))
                .order(-1);
            routes.respond("/respond/channel", text("stable"));
            routes.respond("/respond/beta-only", text("beta")).where(RouteCondition.header("X-Beta"));

            routes.respond("/respond/status/{shop}", pathVariables -> text(pathVariables.getString("shop") + " is open"))
                .constrain("shop", List.of("north", "south"));

            routes.respond(HttpMethod.POST, "/respond/accepted", HttpResponse.accepted().body("queued").contentType(MediaType.TEXT_PLAIN_TYPE));

            routes.path("/respond/failing", group -> {
                group.error(IllegalStateException.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT)
                    .body("failed: " + error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE));
                group.respond("/", () -> {
                    throw new IllegalStateException("unavailable");
                });
            });
        }
    }
}
