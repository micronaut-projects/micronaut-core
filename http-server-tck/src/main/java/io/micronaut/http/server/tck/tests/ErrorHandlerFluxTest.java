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
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ErrorHandlerFluxTest {

    public static final String SPEC_NAME = "ErrorHandlerFluxTest";

    @Test
    void testErrorHandlerWithFluxThrownException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/errors/flux-exception"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("Your request is erroneous: Cannot process request.")
                .build()));
    }

    @Test
    void testErrorHandlerWithFluxSingleResultThrownException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/errors/flux-single-exception"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("Your request is erroneous: Cannot process request.")
                .build()));
    }

    @Test
    void testErrorHandlerWithFluxChunkedSignaledImmediateError() throws IOException {
        //NOTE - This demonstrates the current behavior of the error handler not getting invoked
        //when writing a chunked response, even if the error is signaled before any data to be
        //written to the response body. It would be ideal if in this case the error handler could
        //still be invoked with the exception from the error signal.
        asserts(SPEC_NAME,
            HttpRequest.GET("/errors/flux-chunked-immediate-error"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal Server Error: Cannot process request.")
                .build()));
    }

    @Test
    void testErrorHandlerWithFluxChunkedSignaledDelayedError() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/errors/flux-chunked-delayed-error"),
            (server, request) -> {
                Executable e = () -> server.exchange(request, String[].class);
                Assertions.assertThrows(HttpClientException.class, e);
            });
    }

    @Test
    void testErrorHandlerWithFluxSingleResultSignaledError() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/errors/flux-single-error"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("Your request is erroneous: Cannot process request.")
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/errors")
    static class ErrorController {

        @Get("/flux-exception")
        Flux<String> fluxException() {
            throw new MyTestException("Cannot process request.");
        }

        @Get("/flux-single-exception")
        @SingleResult
        Flux<String> fluxSingleException() {
            throw new MyTestException("Cannot process request.");
        }

        @Get("/flux-single-error")
        @SingleResult
        Flux<String> fluxSingleError() {
            return Flux.error(new MyTestException("Cannot process request."));
        }

        @Get("/flux-chunked-immediate-error")
        Flux<String> fluxChunkedImmediateError() {
            return Flux.error(new MyTestException("Cannot process request."));
        }

        @Get("/flux-chunked-delayed-error")
        Flux<String> fluxChunkedDelayedError() {
            return Flux.just("1", "2", "3").handle((data, sink) -> {
                if (data.equals("3")) {
                    sink.error(new MyTestException("Cannot process request."));
                } else {
                    sink.next(data);
                }
            });
        }

        @Error(global = true)
        public HttpResponse<String> handleMyTestException(HttpRequest<?> request, MyTestException exception) {
            var error = "Your request is erroneous: " + exception.getMessage();
            return HttpResponse.<String>status(HttpStatus.I_AM_A_TEAPOT, "Bad request")
                .body(error);
        }

    }

    static class MyTestException extends RuntimeException {

        public MyTestException(String message) {
            super(message);
        }
    }
}
