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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface FluxTest {

    @Test
    default void testControllerReturningAFlux() throws IOException {
        TestScenario.builder()
            .specName("FluxTest")
            .request(HttpRequest.GET("/users"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("[{\"name\":\"Joe\"},{\"name\":\"Lewis\"}]")
                .build()))
            .run();
    }

    @Controller("/users")
    @Requires(property = "spec.name", value = "FluxTest")
    class UserController {
        @Get
        Flux<Map<String, String>> getAll() {
            return Flux.fromIterable(Arrays.asList(Collections.singletonMap("name", "Joe"), Collections.singletonMap("name", "Lewis")));
        }
    }
}
