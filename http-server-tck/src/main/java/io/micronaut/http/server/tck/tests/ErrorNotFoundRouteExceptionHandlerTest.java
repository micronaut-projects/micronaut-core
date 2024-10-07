/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.NotAllowedException;
import io.micronaut.http.server.exceptions.NotAllowedExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
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
public class ErrorNotFoundRouteExceptionHandlerTest {
    public static final String SPEC_NAME = "ErrorNotFoundRouteExceptionHandlerTest";

    @Test
    void testCatchingRouteNotFoundExceptions() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/no-existing"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(
                server,
                request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.builder().body("IT'S FINE: Method [GET] not allowed for URI [/no-existing]. Allowed methods: [POST]").equals())
                    .headers(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN))
                    .build()
            )
        );
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/no-existing")
    static class ExceptionController {

        @Post
        String simplePost() {
            return "Hello";
        }
    }


    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    @Singleton
    @Primary
    static class MyExceptionHandler extends NotAllowedExceptionHandler {

        public MyExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
            super(responseProcessor);
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, NotAllowedException exception) {
            return HttpResponse.ok("IT'S FINE: " + exception.getMessage());
        }
    }
}
