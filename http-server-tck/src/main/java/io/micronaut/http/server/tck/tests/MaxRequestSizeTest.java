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
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

/**
 * {@code micronaut.server.max-request-size} applies to every request body, not only to multipart uploads.
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
public class MaxRequestSizeTest {
    public static final String SPEC_NAME = "MaxRequestSizeTest";
    private static final Map<String, Object> CONFIGURATION = Map.of("micronaut.server.max-request-size", "1KB");

    @Test
    void aBodyWithinTheLimitIsAccepted() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(CONFIGURATION)
            .request(HttpRequest.POST("/max-request-size/text", "x".repeat(512)).contentType(MediaType.TEXT_PLAIN_TYPE))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("512")
                    .build()))
            .run();
    }

    @Test
    void aTextBodyOverTheLimitIsRefusedWith413() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(CONFIGURATION)
            .request(HttpRequest.POST("/max-request-size/text", "x".repeat(4096)).contentType(MediaType.TEXT_PLAIN_TYPE))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                    .build()))
            .run();
    }

    @Test
    void aJsonBodyOverTheLimitIsRefusedWith413() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(CONFIGURATION)
            .request(HttpRequest.POST("/max-request-size/json", Map.of("value", "x".repeat(4096))))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                    .build()))
            .run();
    }

    @Test
    void aFormBodyOverTheLimitIsRefusedWith413() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(CONFIGURATION)
            .request(HttpRequest.POST("/max-request-size/form", Map.of("value", "x".repeat(4096)))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                    .build()))
            .run();
    }

    @Controller("/max-request-size")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MaxRequestSizeController {

        @Post("/text")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String text(@Body String body) {
            return String.valueOf(body.length());
        }

        @Post("/json")
        @Produces(MediaType.TEXT_PLAIN)
        String json(@Body Map<String, String> body) {
            return String.valueOf(body.get("value").length());
        }

        @Post("/form")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Produces(MediaType.TEXT_PLAIN)
        String form(String value) {
            return String.valueOf(value.length());
        }
    }
}
