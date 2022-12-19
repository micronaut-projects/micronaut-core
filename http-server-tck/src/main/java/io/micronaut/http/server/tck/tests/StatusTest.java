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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface StatusTest {
    /**
     * @see <a href="https://github.com/micronaut-projects/micronaut-aws/issues/1387">micronaut-aws #1387</a>
     * @param path Request Path
     */
    @ParameterizedTest
    @ValueSource(strings = {"/http-status", "/http-response-status", "/http-exception"})
    default void testControllerReturningHttpStatus(String path) throws IOException {
        TestScenario.builder()
            .specName("StatusSpec")
            .request(HttpRequest.GET(path))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .build()))
            .run();
    }

    @Requires(property = "spec.name", value = "StatusSpec")
    @Controller("/http-status")
    class HttpStatusController {
        @Get
        HttpStatus index() {
            return HttpStatus.I_AM_A_TEAPOT;
        }
    }

    @Requires(property = "spec.name", value = "StatusSpec")
    @Controller("/http-response-status")
    class HttpResponseStatusController {

        @Get
        HttpResponse<?> index() {
            return HttpResponse.status(HttpStatus.I_AM_A_TEAPOT);
        }
    }

    @Requires(property = "spec.name", value = "StatusSpec")
    @Controller("/http-exception")
    class HttpResponseErrorController {

        @Get
        HttpResponse<?> index() {
            throw new TeapotException();
        }
    }

    class TeapotException extends RuntimeException {
    }

    @Produces
    @Singleton
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
