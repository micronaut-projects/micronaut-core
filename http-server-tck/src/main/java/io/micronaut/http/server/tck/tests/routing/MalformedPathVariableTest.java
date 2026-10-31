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
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * A path variable that is percent-encoded malformed UTF-8 is a bad request, for a controller
 * route and a handler route, and a local {@link Error} handler answers it.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class MalformedPathVariableTest {
    public static final String SPEC_NAME = "MalformedPathVariableTest";
    private static final List<String> MALFORMED = List.of("%C0%AE", "%FF", "%E2%82");

    @Test
    void aMalformedVariableOfAControllerRouteIsABadRequest() throws IOException {
        assertBadRequest("/malformed/controller/items/");
    }

    @Test
    void aMalformedRemainderOfAControllerRouteIsABadRequest() throws IOException {
        assertBadRequest("/malformed/controller/files/a/");
    }

    @Test
    void aMalformedVariableOfAHandlerRouteIsABadRequest() throws IOException {
        assertBadRequest("/malformed/handler/items/");
    }

    @Test
    void aMalformedRemainderOfAHandlerRouteIsABadRequest() throws IOException {
        assertBadRequest("/malformed/handler/files/a/");
    }

    @Test
    void aWellFormedVariableIsDecoded() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/malformed/controller/items/%E2%82%AC"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item €")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/malformed/handler/files/a/b%E2%82%AC"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("file a/b€")
                .build());
        }
    }

    @Test
    void aLocalErrorHandlerAnswersAMalformedVariable() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/malformed/handled/%C0%AE"), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .body("handled")
                .build());
        }
    }

    @Test
    void aMalformedQueryValueIsNotAServerError() throws IOException {
        // query values are decoded by the runtime, which replaces a malformed sequence
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/malformed/controller/query?q=%C0%AE"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
        }
    }

    private static void assertBadRequest(String prefix) throws IOException {
        try (ServerUnderTest server = server()) {
            for (String malformed : MALFORMED) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(prefix + malformed), HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .build());
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Controller("/malformed")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MalformedController {
        @Get("/controller/items/{id}")
        @Produces(MediaType.TEXT_PLAIN)
        String item(String id) {
            return "item " + id;
        }

        @Get("/controller/files/{+path}")
        @Produces(MediaType.TEXT_PLAIN)
        String file(String path) {
            return "file " + path;
        }

        @Get("/controller/query")
        @Produces(MediaType.TEXT_PLAIN)
        String query(@QueryValue String q) {
            return "query " + q.length();
        }

        @Get("/handled/{id}")
        @Produces(MediaType.TEXT_PLAIN)
        String handled(String id) {
            return "handled " + id;
        }

        @Error(HttpStatusException.class)
        HttpResponse<String> badRequest(HttpStatusException e) {
            return HttpResponse.<String>status(e.getStatus()).body("handled").contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MalformedRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/malformed/handler/items/{id}", (request, pathVariables) ->
                HttpResponse.ok("item " + pathVariables.getString("id")).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/malformed/handler/files/{+path}", (request, pathVariables) ->
                HttpResponse.ok("file " + pathVariables.getString("path")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
