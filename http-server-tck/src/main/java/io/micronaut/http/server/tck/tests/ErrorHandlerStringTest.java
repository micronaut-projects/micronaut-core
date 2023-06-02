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
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.tck.AssertionUtils;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ErrorHandlerStringTest {
    public static final String SPEC_NAME = "ErrorHandlerStringTest";

    @Test
    void testErrorHandlerWithStringReturn() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/exception/my"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(
                server,
                request,
                HttpStatus.OK,
                "{\"message\":\"hello\"}",
                Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            )
        );
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/exception")
    static class ExceptionController {

        @Get("/my")
        void throwsMy() {
            throw new MyException("bad");
        }
    }

    static class MyException extends RuntimeException {
        public MyException(String badThings) {
            super(badThings);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class MyExceptionHandler implements ExceptionHandler<MyException, String> {

        @Override
        public String handle(HttpRequest request, MyException exception) {
            return ""{\"message\":\"hello\"}"";
        }
    }
}
