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
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Collections;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class PublisherExceptionHandlerTest {
    private static final String SPEC_NAME = "PublisherExceptionHandlerTest";

    // https://github.com/micronaut-projects/micronaut-core/issues/6395
    @Test
    public void publisherError() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/publisher-error?msg=foo"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("handled: foo")
                    .build());
            })
            .run();
    }

    @Test
    public void validationIsWorking() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/publisher-error?msg="))
            .assertion((server, request) -> {
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .headers(Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
                        .build()
                );
            })
            .run();
    }

    @Controller("/publisher-error")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MyController {
        @Get
        // @NotBlank makes validation intercept this method. Validation makes the method return
        // a Publishers.just(error) instead of throwing the error.
        public Publisher<String> errorPublisher(@NotBlank String msg) throws MyException {
            throw new MyException(msg);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MyExceptionHandler implements ExceptionHandler<MyException, String> {
        @Override
        public String handle(HttpRequest request, MyException exception) {
            return "handled: " + exception.getMessage();
        }
    }

    static class MyException extends Exception {
        public MyException(String message) {
            super(message);
        }
    }
}
