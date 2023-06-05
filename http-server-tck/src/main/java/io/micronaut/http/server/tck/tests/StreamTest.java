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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class StreamTest {
    public static final String SPEC_NAME = "StreamTest";

    @Test
    void statusErrorAsFirstItem() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/stream/status-error-as-first-item").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .body("foo")
                .build()));
    }

    @Test
    void statusErrorImmediate() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/stream/status-error-immediate").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .body("foo")
                .build()));
    }

    @Controller("/stream")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StreamController {
        @Get(uri = "/status-error-as-first-item", processes = MediaType.TEXT_PLAIN)
        Publisher<String> statusErrorAsFirstItem() {
            return Flux.error(new HttpStatusException(HttpStatus.NOT_FOUND, (Object) "foo"));
        }

        @Get(uri = "/status-error-immediate", processes = MediaType.TEXT_PLAIN)
        Publisher<String> statusErrorImmediate() {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, (Object) "foo");
        }
    }
}
