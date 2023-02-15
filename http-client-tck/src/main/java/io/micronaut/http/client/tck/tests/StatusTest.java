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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension",
})
public class StatusTest {

    private static final String SPEC_NAME = "StatusTest";

    @Test
    void returnStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/status/http-status"),
            (server, request) ->
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.I_AM_A_TEAPOT)
                        .build())
        );
    }

    @Test
    void responseStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/status/response-status"),
            (server, request) ->
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.I_AM_A_TEAPOT)
                        .build())
        );
    }

    @Test
    void atStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/status/at-status"),
            (server, request) ->
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.I_AM_A_TEAPOT)
                        .build())
        );
    }

    @Test
    void exceptionStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/status/exception-status"),
            (server, request) ->
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.I_AM_A_TEAPOT)
                        .build())
        );
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/status")
    static class HttpStatusController {
        @Get("/http-status")
        HttpStatus status() {
            return HttpStatus.I_AM_A_TEAPOT;
        }

        @Get("/at-status")
        @Status(HttpStatus.I_AM_A_TEAPOT)
        void atstatus() {
            // Does nothing, just returns a status
        }

        @Get("/response-status")
        HttpResponse<?> response() {
            return HttpResponse.status(HttpStatus.I_AM_A_TEAPOT);
        }

        @Get("/exception-status")
        HttpResponse<?> exception() {
            throw new TeapotException();
        }
    }

    static class TeapotException extends RuntimeException {
    }

    @Produces
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TeapotExceptionHandler implements ExceptionHandler<TeapotException, HttpResponse<?>> {
        private final ErrorResponseProcessor<?> errorResponseProcessor;

        TeapotExceptionHandler(ErrorResponseProcessor<?> errorResponseProcessor) {
            this.errorResponseProcessor = errorResponseProcessor;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, TeapotException e) {
            return errorResponseProcessor.processResponse(ErrorContext.builder(request)
                .cause(e)
                .build(), HttpResponse.status(HttpStatus.I_AM_A_TEAPOT));
        }
    }
}
