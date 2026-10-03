/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FormUrlEncodedTest {
    private static final String SPEC_NAME = "FormUrlEncodedTest";

    @Test
    void youCanSubmitAFormAsUrlEncoded() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/form/submit", Map.of("firstName", "Sergio")).contentType(MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("Sergio", response.getBody(String.class).get());
                })
                .build()));
    }

    @Test
    void youCanSubmitAFormAsRawBytes() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/form/pair", "firstName=Sergio&lastName=del+Amo".getBytes(StandardCharsets.UTF_8)).contentType(MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio del Amo")
                .build()));
    }

    @Test
    void youCanSubmitAFormAsRawByteBuffer() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/form/pair", ByteArrayBufferFactory.INSTANCE.wrap("firstName=Sergio&lastName=del+Amo".getBytes(StandardCharsets.UTF_8))).contentType(MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio del Amo")
                .build()));
    }

    @Test
    void youCanSubmitAFormAsRawString() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/form/pair", "firstName=Sergio&lastName=del+Amo").contentType(MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio del Amo")
                .build()));
    }

    @Test
    void youCanSubmitAFormWithMultipleValues() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/form/multi", Map.of("name", List.of("Sergio", "Tim"))).contentType(MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio,Tim")
                .build()));
    }

    @Test
    void youCanSubmitAFormWithMultipleValuesAsRawBytes() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/form/multi", "name=Sergio&name=Tim".getBytes(StandardCharsets.UTF_8)).contentType(MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio,Tim")
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/form")
    static class EncodingTestController {
        @Post("/submit")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Produces(MediaType.TEXT_HTML)
        String submit(@Body Map<String, String> form) {
            return form.get("firstName");
        }

        @Post("/pair")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Produces(MediaType.TEXT_PLAIN)
        String pair(@Body Map<String, String> form) {
            return form.get("firstName") + " " + form.get("lastName");
        }

        @Post("/multi")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Produces(MediaType.TEXT_PLAIN)
        String multi(@Body Map<String, List<String>> form) {
            return String.join(",", form.get("name"));
        }
    }
}
