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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;

/**
 * Reactive filter methods that return an empty publisher, or a {@code null} publisher when
 * {@link Nullable}, proceed with the current request or response.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterEmptyPublisherTest {
    public static final String SPEC_NAME = "FilterEmptyPublisherTest";

    @ParameterizedTest
    @ValueSource(strings = {
        "response-empty",
        "response-null",
        "response-mono-empty",
        "response-flux-empty",
        "response-delayed-empty",
        "request-empty",
        "request-null",
        "request-mono-empty",
        "request-delayed-empty",
        "around-empty"
    })
    public void emptyOrNullPublisherProceeds(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/empty-publisher/" + path))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("OK " + path)
                .build()))
            .run();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "response-non-nullable-null",
        "request-non-nullable-null"
    })
    public void nonNullableNullPublisherFails(String path) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/empty-publisher/" + path))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build()))
            .run();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter
    static class EmptyPublisherFilter {

        @ResponseFilter("/empty-publisher/response-empty")
        @Nullable
        Publisher<MutableHttpResponse<?>> responseEmpty(MutableHttpResponse<?> response) {
            return Publishers.empty();
        }

        @ResponseFilter("/empty-publisher/response-null")
        @Nullable
        Publisher<MutableHttpResponse<?>> responseNull(MutableHttpResponse<?> response) {
            return null;
        }

        @ResponseFilter("/empty-publisher/response-mono-empty")
        Mono<MutableHttpResponse<?>> responseMonoEmpty(MutableHttpResponse<?> response) {
            return Mono.empty();
        }

        @ResponseFilter("/empty-publisher/response-flux-empty")
        Flux<MutableHttpResponse<?>> responseFluxEmpty(MutableHttpResponse<?> response) {
            return Flux.empty();
        }

        @ResponseFilter("/empty-publisher/response-delayed-empty")
        Mono<MutableHttpResponse<?>> responseDelayedEmpty(MutableHttpResponse<?> response) {
            return Mono.delay(Duration.ofMillis(10)).then(Mono.empty());
        }

        @SuppressWarnings("NullAway")
        @ResponseFilter("/empty-publisher/response-non-nullable-null")
        Publisher<MutableHttpResponse<?>> responseNonNullableNull(MutableHttpResponse<?> response) {
            return null;
        }

        @RequestFilter("/empty-publisher/request-empty")
        Publisher<HttpResponse<?>> requestEmpty(HttpRequest<?> request) {
            return Publishers.empty();
        }

        @RequestFilter("/empty-publisher/request-null")
        @Nullable
        Publisher<HttpRequest<?>> requestNull(HttpRequest<?> request) {
            return null;
        }

        @RequestFilter("/empty-publisher/request-mono-empty")
        Mono<HttpRequest<?>> requestMonoEmpty(HttpRequest<?> request) {
            return Mono.empty();
        }

        @RequestFilter("/empty-publisher/request-delayed-empty")
        Mono<HttpResponse<?>> requestDelayedEmpty(HttpRequest<?> request) {
            return Mono.delay(Duration.ofMillis(10)).then(Mono.empty());
        }

        @SuppressWarnings("NullAway")
        @RequestFilter("/empty-publisher/request-non-nullable-null")
        Publisher<HttpResponse<?>> requestNonNullableNull(HttpRequest<?> request) {
            return null;
        }

        @RequestFilter("/empty-publisher/around-empty")
        Publisher<MutableHttpResponse<?>> aroundEmpty(FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation) {
            // proceeds, then lets the downstream response through unchanged
            return Mono.from(continuation.proceed()).then(Mono.empty());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/empty-publisher")
    static class EmptyPublisherController {
        @Get("/{path}")
        String index(String path) {
            return "OK " + path;
        }
    }
}
