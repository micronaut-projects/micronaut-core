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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FiltersTest {
    public static final String SPEC_NAME = "FiltersTest";
    public static final String PROP_MICRONAUT_SERVER_CORS_ENABLED = "micronaut.server.cors.enabled";

    @Test
    void testFiltersAreRunCorrectly() throws IOException {
        Map<String, Object> configuration = CollectionUtils.mapOf(
            PROP_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        );
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration)) {
            HttpRequest<?> request = HttpRequest.GET("/filter-test/ok");
            AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("OK")
                    .headers(Collections.singletonMap("X-Test-Filter", StringUtils.TRUE))
                .build());
        }
    }

    @Test
    void filtersAreAppliedOnNonMatchingMethodsCorsFilterWorks() throws IOException {
        Map<String, Object> configuration = CollectionUtils.mapOf(
            PROP_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        );
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration)) {
            HttpRequest<?> request = HttpRequest.OPTIONS("/filter-test/ok").header("Origin", "https://micronaut.io")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
            AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Collections.singletonMap(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://micronaut.io"))
                .build());
        }
    }

    @Test
    void filtersAreAppliedOnNonMatchingMethodsCorsFilterDisableIfNotPreflight() throws IOException {
        Map<String, Object> configuration = CollectionUtils.mapOf(
            PROP_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        );
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration)) {
            HttpRequest<?> request = HttpRequest.OPTIONS("/filter-test/ok");
            AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void testFiltersAreRunCorrectlyWithCustomExceptionHandler() throws IOException {
        Map<String, Object> configuration = CollectionUtils.mapOf(
            PROP_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        );
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration)) {
            HttpRequest<?> request = HttpRequest.GET("/filter-test/exception");
            AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                    .body("Exception Handled")
                .headers(Collections.singletonMap("X-Test-Filter", StringUtils.TRUE))
                .build());
        }
    }

    @Controller("/filter-test")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TestController {
        @Get("/ok")
        String ok() {
            return "OK";
        }

        @Get("/exception")
        void exception() {
            throw new CustomException();
        }
    }

    @Filter("/filter-test/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TestFilter implements HttpServerFilter {
        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.map(chain.proceed(request), httpResponse -> {
                httpResponse.getHeaders().add("X-Test-Filter", "true");
                return httpResponse;
            });
        }
    }

    static class CustomException extends RuntimeException {
    }

    @Produces
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CustomExceptionHandler implements ExceptionHandler<CustomException, HttpResponse<?>> {
        @Override
        public HttpResponse handle(HttpRequest request, CustomException exception) {
            return HttpResponse.ok("Exception Handled");
        }

    }
}
