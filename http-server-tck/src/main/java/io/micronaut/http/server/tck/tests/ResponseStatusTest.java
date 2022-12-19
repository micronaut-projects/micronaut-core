/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface ResponseStatusTest {

    @Test
    default void testConstraintViolationCauses400() throws IOException {
        TestScenario.builder()
            .specName("ResponseStatusSpec")
            .request(HttpRequest.POST("/response-status/constraint-violation", Collections.emptyMap()).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build()))
            .run();
    }

    @Test
    default void testVoidMethodsDoesNotCause404() throws IOException {
        TestScenario.builder()
            .specName("ResponseStatusSpec")
            .request(HttpRequest.DELETE("/response-status/delete-something")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NO_CONTENT)
                .build()))
            .run();
    }

    @Test
    default void testNullCauses404() throws IOException {
        TestScenario.builder()
            .request(HttpRequest.GET("/response-status/null").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN))
            .specName("ResponseStatusSpec")
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build()))
            .run();
    }

    @Test
    default void testOptionalCauses404() throws IOException {
        TestScenario.builder()
            .specName("ResponseStatusSpec")
            .request(HttpRequest.GET("/response-status/optional").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build()))
            .run();
    }

    @Test
    default void testCustomResponseStatus() throws IOException {
        TestScenario.builder()
            .specName("ResponseStatusSpec")
            .request(HttpRequest.POST("/response-status", "foo")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("foo")
                .build()))
            .run();
    }

    @Controller("/response-status")
    @Requires(property = "spec.name", value = "ResponseStatusSpec")
    class StatusController {

        @Post(uri = "/", processes = MediaType.TEXT_PLAIN)
        @Status(HttpStatus.CREATED)
        String post(@Body String data) {
            return data;
        }

        @Get(uri = "/optional", processes = MediaType.TEXT_PLAIN)
        Optional<String> optional() {
            return Optional.empty();
        }

        @Get(uri = "/null", processes = MediaType.TEXT_PLAIN)
        String returnNull() {
            return null;
        }

        @Post(uri = "/constraint-violation", processes = MediaType.TEXT_PLAIN)
        String constraintViolation() {
            throw new ConstraintViolationException("Failed", Collections.emptySet());
        }

        @Status(HttpStatus.NO_CONTENT)
        @Delete(uri = "/delete-something", processes = MediaType.TEXT_PLAIN)
        void deleteSomething() {
            // do nothing
        }
    }
}
