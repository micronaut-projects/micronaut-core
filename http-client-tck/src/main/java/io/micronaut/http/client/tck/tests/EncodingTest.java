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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EncodingTest {
    private static final String SPEC_NAME = "EncodingTest";

    @Test
    void bytesDecodedCorrectly() throws IOException {
        asserts(SPEC_NAME,
            Map.of(),
            HttpRequest.GET("/encoding/bytes"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("foo", response.getBody(String.class).get());
                })
                .build()));
    }

    @Test
    void stringDecodedCorrectly() throws IOException {
        asserts(SPEC_NAME,
            Map.of(),
            HttpRequest.GET("/encoding/string"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("foo", response.getBody(String.class).get());
                })
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/encoding")
    static class EncodingTestController {

        @Get("/bytes")
        @Produces("text/plain; charset=utf-16")
        byte[] bytes() {
            return "foo".getBytes(StandardCharsets.UTF_16);
        }

        @Get("/string")
        @Produces("text/plain; charset=utf-16")
        String string() {
            return "foo";
        }
    }

}
