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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ProducesAmbiguityTest {
    public static final String SPEC_NAME = "ProducesAmbiguityTest";
    private static final String JSON = "{\"kind\":\"json\"}";

    @Test
    void noAcceptPrefersJson() throws IOException {
        assertOk(HttpRequest.GET("/produces-ambiguity"), JSON);
    }

    @Test
    void wildcardAcceptPrefersJson() throws IOException {
        assertOk(HttpRequest.GET("/produces-ambiguity").header(HttpHeaders.ACCEPT, MediaType.ALL), JSON);
    }

    @Test
    void textPlainAccept() throws IOException {
        assertOk(HttpRequest.GET("/produces-ambiguity").header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN), "text");
    }

    @Test
    void jsonAccept() throws IOException {
        assertOk(HttpRequest.GET("/produces-ambiguity").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON), JSON);
    }

    @Test
    void nonJsonRoutesStayAmbiguousWithoutAccept() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/produces-ambiguity/no-json"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build()));
    }

    private static void assertOk(HttpRequest<?> request, String body) throws IOException {
        asserts(SPEC_NAME, request,
            (server, r) -> AssertionUtils.assertDoesNotThrow(server, r, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(body)
                .build()));
    }

    @Controller("/produces-ambiguity")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProducesAmbiguityController {

        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String text() {
            return "text";
        }

        @Get
        @Produces(MediaType.APPLICATION_JSON)
        String json() {
            return JSON;
        }

        @Get("/no-json")
        @Produces(MediaType.TEXT_PLAIN)
        String noJsonText() {
            return "text";
        }

        @Get("/no-json")
        @Produces(MediaType.TEXT_HTML)
        String noJsonHtml() {
            return "html";
        }
    }
}
