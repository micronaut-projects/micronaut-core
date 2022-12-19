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
import io.micronaut.http.uri.UriBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface ParameterTest {
    @Test
    default void testGetAllMethod() throws IOException {
        TestScenario.builder()
            .specName("ParameterTest")
            .request(HttpRequest.GET(UriBuilder.of("/parameters-test").path("all")
                .queryParam("test", "one", "two", "three+four")
                .build()))
            .assertion((server, request) ->  AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("[\"one\",\"two\",\"three+four\"]")
                .build()))
            .run();
    }

    @Controller("/parameters-test")
    @Requires(property = "spec.name", value = "ParameterTest")
    static class BodyController {

        @Get(uri = "/all")
        List<String> all(HttpRequest request) {
            return request.getParameters().getAll("test");
        }
    }
}
