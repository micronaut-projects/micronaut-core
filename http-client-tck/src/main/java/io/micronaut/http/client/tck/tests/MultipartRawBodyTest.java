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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static io.micronaut.http.tck.TestScenario.asserts;

/**
 * A multipart body that is already encoded is sent as is, like an encoded form body.
 */
class MultipartRawBodyTest {
    private static final String SPEC_NAME = "MultipartRawBodyTest";
    private static final String CONTENT_TYPE = MediaType.MULTIPART_FORM_DATA + "; boundary=tck-boundary";
    private static final String BODY = """
        --tck-boundary\r
        Content-Disposition: form-data; name="firstName"\r
        \r
        Sergio\r
        --tck-boundary\r
        Content-Disposition: form-data; name="lastName"\r
        Content-Type: text/plain\r
        \r
        del Amo\r
        --tck-boundary--\r
        """;

    @Test
    void youCanSubmitAMultipartBodyAsRawBytes() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/multipart-raw/pair", BODY.getBytes(StandardCharsets.UTF_8)).contentType(CONTENT_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio del Amo")
                .build()));
    }

    @Test
    void youCanSubmitAMultipartBodyAsRawByteBuffer() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/multipart-raw/pair", ByteArrayBufferFactory.INSTANCE.wrap(BODY.getBytes(StandardCharsets.UTF_8))).contentType(CONTENT_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio del Amo")
                .build()));
    }

    @Test
    void youCanSubmitAMultipartBodyAsRawString() throws IOException {
        asserts(SPEC_NAME,
            Collections.emptyMap(),
            HttpRequest.POST("/multipart-raw/pair", BODY).contentType(CONTENT_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Sergio del Amo")
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/multipart-raw")
    static class MultipartRawController {

        @Post("/pair")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Produces(MediaType.TEXT_PLAIN)
        String pair(@Part String firstName, @Part String lastName) {
            return firstName + " " + lastName;
        }
    }
}
