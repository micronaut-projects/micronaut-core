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
import io.micronaut.core.annotation.ReflectiveAccess;
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
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * The body stages of a functional route: an asynchronous handler of the decoded body or of the
 * form, which is read before the handler runs, a synchronous handler of the body it reads itself,
 * and the stages of a route of a custom method declared by its name.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteBodyStagesTest {
    public static final String SPEC_NAME = "HandlerRouteBodyStagesTest";

    @Test
    void anAsynchronousHandlerReceivesTheDecodedBody() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/stages/async-typed", "{\"name\":\"cup\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("async cup")
                .build());
        }
    }

    @Test
    void theBodyOfAnAsynchronousHandlerIsDecodedBeforeItRuns() throws IOException {
        try (ServerUnderTest server = server()) {
            // an invalid body is answered like the invalid @Body argument of a controller
            AssertionUtils.assertThrows(server, HttpRequest.POST("/stages/async-typed", "{not json")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/stages/async-typed", "cup")
                .contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .build());
        }
    }

    @Test
    void anAsynchronousHandlerReceivesTheForm() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/stages/async-form", "name=cup&size=2")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("form cup 2")
                .build());
        }
    }

    @Test
    void aSynchronousHandlerReadsTheBodyItself() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/stages/raw", "plain text")
                .contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("read plain text")
                .build());
        }
    }

    @Test
    void aRouteOfACustomMethodHasTheBodyStages() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.create(HttpMethod.CUSTOM, "/stages/custom", "PROPPATCH")
                .body("{\"name\":\"cup\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("PROPPATCH cup")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.create(HttpMethod.CUSTOM, "/stages/custom-async", "REPORT")
                .body("{\"name\":\"mug\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("REPORT mug")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(HttpStatus status, String body) {
        return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Introspected
    @ReflectiveAccess
    record Item(String name) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyStageRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/stages/async-typed")
                .body(Item.class)
                .handleAsync((request, pathVariables, item) ->
                    CompletableFuture.completedFuture(text(HttpStatus.CREATED, "async " + item.name())));
            routes.POST("/stages/async-form")
                .form()
                .handleAsync((request, pathVariables, form) ->
                    CompletableFuture.completedFuture(text(HttpStatus.OK, "form " + form.getString("name") + " " + form.getInt("size"))));
            routes.POST("/stages/raw")
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .executeOn(TaskExecutors.BLOCKING)
                .body()
                .handle((request, pathVariables, body) ->
                    text(HttpStatus.OK, "read " + body.text().toCompletableFuture().join()));
            routes.route("PROPPATCH", "/stages/custom")
                .body(Item.class)
                .handle((request, pathVariables, item) -> text(HttpStatus.OK, request.getMethodName() + " " + item.name()));
            routes.route("REPORT", "/stages/custom-async")
                .body(Item.class)
                .handleAsync((request, pathVariables, item) ->
                    CompletableFuture.completedFuture(text(HttpStatus.OK, request.getMethodName() + " " + item.name())));
        }
    }
}
