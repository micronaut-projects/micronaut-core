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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

/**
 * A pre-matching request filter that changes the URI of the request changes the route the request
 * is matched to, whether it returns the changed request or changes it in place.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class PreMatchingUriFilterTest {
    public static final String SPEC_NAME = "PreMatchingUriFilterTest";

    @Test
    void returnedRequestWithANewUriIsMatched() throws IOException {
        TestScenario.asserts(SPEC_NAME,
            HttpRequest.GET("/pre-matching/returned"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("target returned")
                .build()));
    }

    @Test
    void requestChangedInPlaceIsMatched() throws IOException {
        TestScenario.asserts(SPEC_NAME,
            HttpRequest.GET("/pre-matching/in-place"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("target in-place")
                .build()));
    }

    @Test
    void queryOfTheUriChangedInPlaceIsBound() throws IOException {
        TestScenario.asserts(SPEC_NAME,
            HttpRequest.GET("/pre-matching/in-place-query"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("target in-place-query")
                .build()));
    }

    @Test
    void headerChangedInPlaceIsKept() throws IOException {
        TestScenario.asserts(SPEC_NAME,
            HttpRequest.GET("/pre-matching/target/header"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("target header")
                .build()));
    }

    @ServerFilter("/pre-matching/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class UriFilter {

        @RequestFilter
        @PreMatching
        @Nullable
        MutableHttpRequest<?> returned(MutableHttpRequest<?> request) {
            if (request.getPath().equals("/pre-matching/returned")) {
                return request.mutate().uri(URI.create("/pre-matching/target/returned"));
            }
            return null;
        }

        @RequestFilter
        @PreMatching
        void inPlace(MutableHttpRequest<?> request) {
            if (request.getPath().equals("/pre-matching/in-place")) {
                request.uri(URI.create("/pre-matching/target/in-place"));
            } else if (request.getPath().equals("/pre-matching/in-place-query")) {
                request.uri(URI.create("/pre-matching/query?name=in-place-query"));
            } else if (request.getPath().equals("/pre-matching/target/header")) {
                request.header("X-Name", "header");
            }
        }
    }

    @Controller("/pre-matching")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class TargetController {

        @Get("/target/{name}")
        String target(String name, HttpRequest<?> request) {
            return "target " + request.getHeaders().get("X-Name", String.class).orElse(name);
        }

        @Get("/query")
        String query(@QueryValue String name) {
            return "target " + name;
        }
    }
}
