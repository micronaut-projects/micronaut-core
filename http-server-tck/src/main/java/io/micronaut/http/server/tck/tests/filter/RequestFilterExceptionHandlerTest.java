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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.function.BiConsumer;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RequestFilterExceptionHandlerTest {
    private static final String SPEC_NAME = "RequestFilterExceptionHandlerTest";

    @Test
    public void exceptionHandlerTest() throws IOException {
        assertion(HttpRequest.GET("/foo"),
            AssertionUtils.assertThrowsStatus(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private static void assertion(HttpRequest<?> request, BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(request)
            .assertion(assertion)
            .run();
    }


    static class FooException extends RuntimeException {

    }

    @ServerFilter(value = MATCH_ALL_PATTERN)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ErrorThrowingFilter {

        @RequestFilter
        public void doFilter(MutableHttpRequest<?> request) {
            throw new FooException();
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/foo")
    static class FooController {

        @Produces(MediaType.TEXT_PLAIN)
        @Get
        String index() {
            return "Hello World";
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class FooExceptionHandler implements ExceptionHandler<FooException, HttpResponse<?>> {

        private final ErrorResponseProcessor<?> errorResponseProcessor;

        public FooExceptionHandler(ErrorResponseProcessor<?> errorResponseProcessor) {
            this.errorResponseProcessor = errorResponseProcessor;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, FooException exception) {
            return errorResponseProcessor.processResponse(ErrorContext.builder(request)
                .cause(exception)
                .build(), HttpResponse.unprocessableEntity());
        }
    }
}
