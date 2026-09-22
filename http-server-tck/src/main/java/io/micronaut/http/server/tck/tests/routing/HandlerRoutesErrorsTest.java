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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * An exception thrown by a handler function is handled exactly like the same exception thrown by
 * a controller method: by error handler functions, {@code ExceptionHandler} beans, the status of
 * an {@code HttpStatusException}, or the default error response. Status handler functions answer
 * a response status.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesErrorsTest {
    public static final String SPEC_NAME = "HandlerRoutesErrorsTest";

    /**
     * Each exception is thrown by a controller method and by a handler function.
     */
    private static final List<String> SOURCES = List.of("/errors/controller", "/errors/handler");

    @Test
    void checkedExceptionIsHandledByAnErrorHandlerFunction() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/checked"), HttpResponseAssertion.builder()
                    .status(HttpStatus.I_AM_A_TEAPOT)
                    .body("handled checked failure")
                    .build());
            }
        }
    }

    @Test
    void checkedExceptionOfAnAsyncHandlerIsHandledLikeTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            // thrown by the handler itself, not by the stage it returns
            AssertionUtils.assertThrows(server, HttpRequest.GET("/errors/handler/async-checked"), HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("handled checked failure")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/errors/handler/async-filtered"), HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("handled checked failure")
                .build());
        }
    }

    @Test
    void exceptionIsHandledByAnExceptionHandlerBean() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/bean-handled"), HttpResponseAssertion.builder()
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("bean handled")
                    .build());
            }
        }
    }

    @Test
    void httpStatusExceptionAnswersWithItsStatus() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/status"), HttpResponseAssertion.builder()
                    .status(HttpStatus.CONFLICT)
                    .body("busy")
                    .build());
            }
        }
    }

    @Test
    void unhandledExceptionIsAnInternalServerError() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/unhandled"), HttpResponseAssertion.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error")
                    .build());
            }
        }
    }

    @Test
    void exceptionOfARouteFilterIsHandledLikeTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/errors/handler/filtered"), HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("handled checked failure")
                .build());
        }
    }

    @Test
    void statusHandlerFunctionAnswersAStatus() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/errors/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .body("no route for /errors/missing")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    static final class CheckedFailure extends Exception {
        CheckedFailure(String message) {
            super(message);
        }
    }

    static final class BeanHandledFailure extends RuntimeException {
        BeanHandledFailure(String message) {
            super(message);
        }
    }

    static final class UnhandledFailure extends RuntimeException {
        UnhandledFailure(String message) {
            super(message);
        }
    }

    @Controller("/errors/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ThrowingController {
        @Get("/checked")
        String checked() throws CheckedFailure {
            throw new CheckedFailure("checked failure");
        }

        @Get("/bean-handled")
        String beanHandled() {
            throw new BeanHandledFailure("bean handled failure");
        }

        @Get("/status")
        String status() {
            throw new HttpStatusException(HttpStatus.CONFLICT, "busy");
        }

        @Get("/unhandled")
        String unhandled() {
            throw new UnhandledFailure("unhandled failure");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ThrowingRoutes implements HttpRoutes {
        @Override
        public void routes(io.micronaut.web.router.builder.HttpRouteBuilder routes) {
            routes.GET("/errors/handler/checked", (request, pathVariables) -> {
                throw new CheckedFailure("checked failure");
            });
            routes.GET("/errors/handler/bean-handled", (request, pathVariables) -> {
                throw new BeanHandledFailure("bean handled failure");
            });
            routes.GET("/errors/handler/status", (request, pathVariables) -> {
                throw new HttpStatusException(HttpStatus.CONFLICT, "busy");
            });
            routes.GET("/errors/handler/unhandled", (request, pathVariables) -> {
                throw new UnhandledFailure("unhandled failure");
            });
            routes.asyncGET("/errors/handler/async-checked", (request, pathVariables) -> {
                throw new CheckedFailure("checked failure");
            });
            routes.GET("/errors/handler/async-filtered", (request, pathVariables) -> HttpResponse.ok("not reached"))
                .beforeAsync(request -> {
                    throw new CheckedFailure("checked failure");
                });
            routes.GET("/errors/handler/filtered", (request, pathVariables) -> HttpResponse.ok("not reached"))
                .before(request -> {
                    throw new CheckedFailure("checked failure");
                });

            // error and status handler functions, for controller routes and handler routes alike
            routes.error(CheckedFailure.class, (request, error) ->
                HttpResponse.status(HttpStatus.I_AM_A_TEAPOT).body("handled " + error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.status(HttpStatus.NOT_FOUND, request ->
                HttpResponse.notFound("no route for " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BeanHandledFailureHandler implements ExceptionHandler<BeanHandledFailure, HttpResponse<String>> {
        @Override
        public HttpResponse<String> handle(HttpRequest request, BeanHandledFailure exception) {
            return HttpResponse.<String>status(HttpStatus.UNPROCESSABLE_ENTITY).body("bean handled").contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
