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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

/**
 * Among controller routes with the same literal length and number of variables, the route with
 * fewer variables constrained by a regular expression is more specific.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class PatternVariableSpecificityTest {
    public static final String SPEC_NAME = "PatternVariableSpecificityTest";

    @ParameterizedTest
    @CsvSource({
        // both match: the variable without a regular expression wins
        "/sel/1, plain 1",
        "/sel2/1/2, plain pair 1 2",
        // only the constrained route matches
        "/sel/1/2, pattern 1/2",
        "/sel2/1/2/3, pattern pair 1 2/3"
    })
    void theUnconstrainedVariableIsMoreSpecific(String path, String body) throws IOException {
        TestScenario.asserts(SPEC_NAME,
            HttpRequest.GET(path),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(body)
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    @Produces(MediaType.TEXT_PLAIN)
    static class SelectionController {

        @Get("/sel/{id:.+}")
        String pattern(String id) {
            return "pattern " + id;
        }

        @Get("/sel/{id}")
        String plain(String id) {
            return "plain " + id;
        }

        @Get("/sel2/{a}/{b:.+}")
        String patternPair(String a, String b) {
            return "pattern pair " + a + " " + b;
        }

        @Get("/sel2/{a}/{b}")
        String plainPair(String a, String b) {
            return "plain pair " + a + " " + b;
        }
    }
}
