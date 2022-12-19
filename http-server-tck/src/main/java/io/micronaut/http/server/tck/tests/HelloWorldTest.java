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
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.TestScenario;
import io.micronaut.http.uri.UriBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface HelloWorldTest {

    @Test
    default void helloWorld() throws IOException {
        TestScenario.builder()
            .specName("HelloWorldTest")
            .request(HttpRequest.GET(UriBuilder.of("/hello").path("world").build()).accept(MediaType.TEXT_PLAIN))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "Hello World",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)))
            .run();
    }

    @Requires(property = "spec.name", value = "HelloWorldTest")
    @Controller("/hello")
    class HelloWorldController {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/world")
        String hello() {
            return "Hello World";
        }
    }
}
