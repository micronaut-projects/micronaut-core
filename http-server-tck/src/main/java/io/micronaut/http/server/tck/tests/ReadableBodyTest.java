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
import io.micronaut.core.io.Readable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ReadableBodyTest {

    public static final String SPEC_NAME = "ReadableBodyTest";

    @Test
    void readsTheBodyAsAStream() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/readable/stream", "hello world").contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("hello world").equals())
                .build()));
    }

    @Test
    void readsTheBodyAsCharacters() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/readable/reader", "héllo")
                .contentType(MediaType.TEXT_PLAIN + ";charset=UTF-8"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("héllo").equals())
                .build()));
    }

    @Test
    void readsTheBodyAsCharactersWithTheCharsetOfTheRequest() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/readable/reader", "héllo à börk")
                .contentType(MediaType.TEXT_PLAIN + ";charset=ISO-8859-1"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("héllo à börk").equals())
                .build()));
    }

    @Test
    void bindsAStringBodyWithTheCharsetOfTheRequest() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/readable/string", "héllo à börk")
                .contentType(MediaType.TEXT_PLAIN + ";charset=ISO-8859-1"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("héllo à börk").equals())
                .build()));
    }

    @Controller("/readable")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class ReadableController {

        @Post(value = "/stream", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String stream(@Body Readable body) throws IOException {
            try (InputStream in = body.asInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Post(value = "/string", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN + ";charset=UTF-8")
        String string(@Body String body) {
            return body;
        }

        @Post(value = "/reader", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN + ";charset=UTF-8")
        String reader(@Body Readable body) throws IOException {
            try (Reader reader = body.asReader()) {
                StringWriter out = new StringWriter();
                reader.transferTo(out);
                return out.toString();
            }
        }
    }
}
