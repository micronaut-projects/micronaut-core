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
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ResponseStatusTest {
    public static final String SPEC_NAME = "ResponseStatusTest";

    @Test
    void testConstraintViolationCauses400() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-status/constraint-violation", "").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build()));
    }

    @Test
    void testVoidMethodsDoesNotCause404() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.DELETE("/response-status/delete-something").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NO_CONTENT)
                .build()));
    }

    @Test
    void testNullCauses404() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-status/null").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build()));
    }

    @Test
    void testOptionalCauses404() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-status/optional").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build()));
    }

    @Test
    void testCustomResponseStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-status", "foo").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("foo")
                .build()));
    }

    @Controller("/response-status")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StatusController {

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
