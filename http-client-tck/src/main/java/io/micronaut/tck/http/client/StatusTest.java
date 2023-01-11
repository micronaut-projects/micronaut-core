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
package io.micronaut.tck.http.client;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface StatusTest extends AbstractTck {

    String STATUS_TEST = "StatusTest";

    @Test
    default void returnStatus() {
        runTest(STATUS_TEST, (server, client) -> {
            HttpClientResponseException thrown = Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> Flux.from(client.exchange(HttpRequest.GET("/status/http-status"))).blockFirst()
            );
            assertEquals(HttpStatus.I_AM_A_TEAPOT, thrown.getStatus());
        });
        runBlockingTest(STATUS_TEST, (server, client) -> {
            HttpClientResponseException thrown = Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/status/http-status"))
            );
            assertEquals(HttpStatus.I_AM_A_TEAPOT, thrown.getStatus());
        });
    }

    @Test
    default void responseStatus() {
        runTest(STATUS_TEST, (server, client) -> {
            HttpClientResponseException thrown = Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> Flux.from(client.exchange(HttpRequest.GET("/status/response-status"))).blockFirst()
            );
            assertEquals(HttpStatus.I_AM_A_TEAPOT, thrown.getStatus());
        });
        runBlockingTest(STATUS_TEST, (server, client) -> {
            HttpClientResponseException thrown = Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/status/response-status"))
            );
            assertEquals(HttpStatus.I_AM_A_TEAPOT, thrown.getStatus());
        });
    }

    @Test
    default void exceptionStatus() {
        runTest(STATUS_TEST, (server, client) -> {
            HttpClientResponseException thrown = Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> Flux.from(client.exchange(HttpRequest.GET("/status/exception-status"), Argument.STRING)).blockFirst()
            );
            assertEquals(HttpStatus.I_AM_A_TEAPOT, thrown.getStatus());
        });
        runBlockingTest(STATUS_TEST, (server, client) -> {
            HttpClientResponseException thrown = Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/status/exception-status"))
            );
            assertEquals(HttpStatus.I_AM_A_TEAPOT, thrown.getStatus());
        });
    }

    @Requires(property = "spec.name", value = STATUS_TEST)
    @Controller("/status")
    static class HttpStatusController {
        @Get("/http-status")
        HttpStatus status() {
            return HttpStatus.I_AM_A_TEAPOT;
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

    class TeapotException extends RuntimeException {
    }

    @Produces
    @Singleton
    @Requires(property = "spec.name", value = STATUS_TEST)
    class TeapotExceptionHandler implements ExceptionHandler<TeapotException, HttpResponse<?>> {
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
