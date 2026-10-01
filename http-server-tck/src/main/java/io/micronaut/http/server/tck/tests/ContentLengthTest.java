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
import io.micronaut.core.annotation.Introspected;
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
import java.nio.charset.StandardCharsets;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A response whose body is complete when the route returns announces its length with {@code Content-Length}.
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
public class ContentLengthTest {
    public static final String SPEC_NAME = "ContentLengthTest";
    private static final String TEXT = "a text body of known length";
    private static final byte[] BYTES = new byte[1500];

    @Test
    void aStringBodyCarriesItsLength() throws IOException {
        assertLength("/content-length/text", TEXT.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void aByteArrayBodyCarriesItsLength() throws IOException {
        assertLength("/content-length/bytes", BYTES.length);
    }

    @Test
    void aJsonBodyCarriesItsLength() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/content-length/json"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        String body = response.getBody(String.class).orElseThrow();
                        assertEquals(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length),
                            response.getHeaders().get(HttpHeaders.CONTENT_LENGTH));
                    })
                    .build()));
    }

    private static void assertLength(String path, int expected) throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET(path),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(expected))
                    .build()));
    }

    @Controller("/content-length")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ContentLengthController {

        @Get("/text")
        @Produces(MediaType.TEXT_PLAIN)
        String text() {
            return TEXT;
        }

        @Get("/bytes")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        byte[] bytes() {
            return BYTES;
        }

        @Get("/json")
        Point json() {
            return new Point(1, 2, "a point with a name long enough to have a length worth checking");
        }
    }

    @Introspected
    record Point(int x, int y, String name) {
    }
}
