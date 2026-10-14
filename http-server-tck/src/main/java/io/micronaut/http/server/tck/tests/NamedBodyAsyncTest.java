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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.micronaut.http.tck.TestScenario.asserts;

/**
 * A {@code @Body("name")} future or publisher argument binds the named component of the body.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class NamedBodyAsyncTest {
    public static final String SPEC_NAME = "NamedBodyAsyncTest";
    private static final String JSON = "{\"name\":\"Fred\",\"items\":[\"a\",\"b\"]}";

    @Test
    void futureOfJsonProperty() throws IOException {
        assertOk(HttpRequest.POST("/named-body-async/future", JSON).contentType(MediaType.APPLICATION_JSON_TYPE), "Fred");
    }

    @Test
    void futureOfFormField() throws IOException {
        assertOk(HttpRequest.POST("/named-body-async/future-form", Map.of("name", "Fred", "other", "x"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), "Fred");
    }

    @Test
    void monoOfJsonProperty() throws IOException {
        assertOk(HttpRequest.POST("/named-body-async/mono", JSON).contentType(MediaType.APPLICATION_JSON_TYPE), "Fred");
    }

    @Test
    void publisherOfJsonArrayProperty() throws IOException {
        assertOk(HttpRequest.POST("/named-body-async/publisher", JSON).contentType(MediaType.APPLICATION_JSON_TYPE), "a,b");
    }

    private static void assertOk(HttpRequest<?> request, String body) throws IOException {
        asserts(SPEC_NAME, request,
            (server, req) -> AssertionUtils.assertDoesNotThrow(server, req, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(body)
                .build()));
    }

    @Controller("/named-body-async")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class NamedBodyController {

        @Post("/future")
        CompletableFuture<String> future(@Body("name") CompletableFuture<String> name) {
            return name;
        }

        @Post("/future-form")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        CompletableFuture<String> futureForm(@Body("name") CompletableFuture<String> name) {
            return name;
        }

        @Post("/mono")
        Mono<String> mono(@Body("name") Mono<String> name) {
            return name;
        }

        @Post("/publisher")
        Mono<String> publisher(@Body("items") Publisher<String> items) {
            return Flux.from(items).collectList().map(list -> String.join(",", list));
        }
    }
}
