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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Map;

/**
 * {@code micronaut.server.strict-path-check} answers an ambiguous raw path with {@code 400}
 * before routing; without it, the path is routed as before.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class StrictPathCheckTest {

    public static final String SPEC_NAME = "StrictPathCheckTest";

    private static final String STRICT = "micronaut.server.strict-path-check";
    private static final String ALLOW_SEMICOLON = "micronaut.server.strict-path-check-allow-semicolon";
    private static final String STATIC_MAPPING = "micronaut.router.static-resources.assets.mapping";
    private static final String STATIC_PATHS = "micronaut.router.static-resources.assets.paths";

    @ParameterizedTest
    @ValueSource(strings = {"/strict-path/a%2Fb", "/strict-path/%2e%2e/b", "/strict-path/a%5Cb", "/strict-path/a;b", "/strict-path/a%00b"})
    void byDefaultAnAmbiguousPathIsRouted(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET(path))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .build()))
            .run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/strict-path/a%2Fb", "/strict-path/%2e%2e/b", "/strict-path/.%2E/b", "/strict-path/a%5Cb", "/strict-path/a;b",
        "/strict-path/a%00b", "/strict-path/%c0%ae/b", "/strict-path/a%3Bb", "/strict-path/..%3B/b", "/strict-path/%252e%252e/b",
        "/strict-path/%E0%80%AE%E0%80%AE/b", "/strict-path/%ED%A0%80", "/strict-path/a%C2%85"})
    void withTheStrictCheckAnAmbiguousPathIsABadRequest(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Map.of(STRICT, true))
            .request(HttpRequest.GET(path))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .build()))
            .run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/strict-path/a", "/strict-path/a/b.txt", "/strict-path/a%20b", "/strict-path/...", "/strict-path/%C3%A9"})
    void withTheStrictCheckAnUnambiguousPathIsRouted(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Map.of(STRICT, true))
            .request(HttpRequest.GET(path))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .build()))
            .run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/strict-path/a;b", "/strict-path/a;jsessionid=1/b"})
    void theStrictCheckCanAllowSemicolons(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Map.of(STRICT, true, ALLOW_SEMICOLON, true))
            .request(HttpRequest.GET(path))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .build()))
            .run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/strict-path/%2e%2e;x/b", "/strict-path/.;x/b", "/strict-path/a%2Fb;x", "/strict-path/..%3Bx/b"})
    void withSemicolonsAllowedADotSegmentIsStillABadRequest(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Map.of(STRICT, true, ALLOW_SEMICOLON, true))
            .request(HttpRequest.GET(path))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .build()))
            .run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/%2e%2e/hello.txt", "/assets/a%2F..%2Fhello.txt", "/assets/hello.txt;x"})
    void withTheStrictCheckAnAmbiguousStaticResourcePathIsABadRequest(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Map.of(STRICT, true, STATIC_MAPPING, "/assets/**", STATIC_PATHS, "classpath:assets"))
            .request(HttpRequest.GET(path))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .build()))
            .run();
    }

    @Test
    void withTheStrictCheckAStaticResourceIsServed() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Map.of(STRICT, true, STATIC_MAPPING, "/assets/**", STATIC_PATHS, "classpath:assets"))
            .request(HttpRequest.GET("/assets/hello.txt"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("Hello World")
                    .build()))
            .run();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/strict-path")
    static class StrictPathController {
        @Get("/{+rest}")
        String get(@PathVariable String rest) {
            return "ok";
        }
    }
}
