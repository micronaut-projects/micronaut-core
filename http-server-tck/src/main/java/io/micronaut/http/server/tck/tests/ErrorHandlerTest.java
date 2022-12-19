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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.ServerUnderTest;
import io.micronaut.http.server.tck.ServerUnderTestProviderUtils;
import io.micronaut.http.server.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})

public interface ErrorHandlerTest {
    @Test
    default void testCustomGlobalExceptionHandlersDeclaredInController() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
                "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
                "micronaut.server.cors.enabled", StringUtils.TRUE
            ))
            .specName("ErrorHandlerTest")
            .request(HttpRequest.GET("/errors/global-ctrl")
                .header(HttpHeaders.CONTENT_TYPE, io.micronaut.http.MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "bad things happens globally",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)))
            .run();
    }

    @Test
    default void testCustomGlobalExceptionHandlers() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
                "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
                "micronaut.server.cors.enabled", StringUtils.TRUE
            ))
            .specName("ErrorHandlerTest")
            .request(HttpRequest.GET("/errors/global")
                .header(HttpHeaders.CONTENT_TYPE, io.micronaut.http.MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "Exception Handled",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)))
            .run();
    }

    @Test
    default void testCustomGlobalExceptionHandlersForPOSTWithBody() throws IOException {
        Map<String, Object> configuration = CollectionUtils.mapOf(
            "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
            "micronaut.server.cors.enabled", StringUtils.TRUE
        );
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer("ErrorHandlerTest", configuration)) {
            ObjectMapper objectMapper = server.getApplicationContext().getBean(ObjectMapper.class);
            HttpRequest<?> request = HttpRequest.POST("/json/errors/global", objectMapper.writeValueAsString(new RequestObject(101)))
                .header(HttpHeaders.CONTENT_TYPE, io.micronaut.http.MediaType.APPLICATION_JSON);
            AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "{\"message\":\"Error: bad things when post and body in request\",\"",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
        }
    }

    @Test
    default void testCustomGlobalStatusHandlersDeclaredInController() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
                "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
                "micronaut.server.cors.enabled", StringUtils.TRUE
            ))
            .specName("ErrorHandlerTest")
            .request(HttpRequest.GET("/errors/global-status-ctrl"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "global status",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)))
            .run();
    }

    @Test
    default void testLocalExceptionHandlers() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
            "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
            "micronaut.server.cors.enabled", StringUtils.TRUE))
            .specName("ErrorHandlerTest")
            .request(HttpRequest.GET("/errors/local"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "bad things",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)))
            .run();
    }

    @Test
    default void jsonMessageFormatErrorsReturn400() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
            "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
            "micronaut.server.cors.enabled", StringUtils.TRUE
        )).specName("ErrorHandlerTest")
            .request(HttpRequest.POST("/json/jsonBody", "{\"numberField\": \"textInsteadOfNumber\"}"))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .headers(Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
                    .build()
            )).run();
    }

    @Test
    default void corsHeadersArePresentAfterFailedDeserialisationWhenErrorHandlerIsUsed() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
            "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
            "micronaut.server.cors.enabled", StringUtils.TRUE
        )).specName("ErrorHandlerTest")
            .request(HttpRequest.POST("/json/errors/global", "{\"numberField\": \"string is not a number\"}")
                .header(HttpHeaders.ORIGIN, "http://localhost:8080"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Collections.singletonMap(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8080"))
                .build()))
            .run();
    }

    @Test
    default void corsHeadersArePresentAfterFailedDeserialisation() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
                "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
                "micronaut.server.cors.enabled", StringUtils.TRUE
            ))
            .specName("ErrorHandlerTest")
            .request(HttpRequest.POST("/json/jsonBody", "{\"numberField\": \"string is not a number\"}")
                .header(HttpHeaders.ORIGIN, "http://localhost:8080"))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .headers(Collections.singletonMap(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8080"))
                .build()))
            .run();
    }

    @Test
    default void corsHeadersArePresentAfterExceptions() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
            "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
            "micronaut.server.cors.enabled", StringUtils.TRUE
        ))
            .specName("ErrorHandlerTest")
            .request(HttpRequest.GET("/errors/global")
                .header(HttpHeaders.ORIGIN, "http://localhost:8080"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Collections.singletonMap(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8080"))
                .build()))
            .run();
    }

    @Test
    default void messageValidationErrorsReturn400() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
            "micronaut.server.cors.configurations.web.allowedOrigins", Collections.singletonList("http://localhost:8080"),
            "micronaut.server.cors.enabled", StringUtils.TRUE
        ))
            .specName("ErrorHandlerTest")
            .request(HttpRequest.POST("/json/jsonBody", "{\"numberField\": 0}"))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .headers(Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
                .build()))
            .run();
    }

    @Controller("/secret")
    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    class SecretController {
        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String index() {
            return "area 51 hosts an alien";
        }
    }

    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    @Controller("/errors")
    class ErrorController {

        @Get("/global")
        String globalHandler() {
            throw new MyException("bad things");
        }

        @Get("/global-ctrl")
        String globalControllerHandler() throws GloballyHandledException {
            throw new GloballyHandledException("bad things happens globally");
        }

        @Get("/global-status-ctrl")
        @Status(HttpStatus.I_AM_A_TEAPOT)
        String globalControllerHandlerForStatus() {
            return "original global status";
        }

        @Get("/local")
        String localHandler() {
            throw new AnotherException("bad things");
        }

        @Error
        @Produces(io.micronaut.http.MediaType.TEXT_PLAIN)
        @Status(HttpStatus.OK)
        String localHandler(AnotherException throwable) {
            return throwable.getMessage();
        }
    }

    @Controller(value = "/json/errors", produces = io.micronaut.http.MediaType.APPLICATION_JSON)
    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    class JsonErrorController {

        @Post("/global")
        String globalHandlerPost(@Body RequestObject object) {
            throw new RuntimeException("bad things when post and body in request");
        }

        @Error
        HttpResponse<JsonError> errorHandler(HttpRequest request, RuntimeException exception) {
            JsonError error = new JsonError("Error: " + exception.getMessage())
                .link(Link.SELF, Link.of(request.getUri()));

            return HttpResponse.<JsonError>status(HttpStatus.OK)
                .body(error);
        }
    }

    @Introspected
    class RequestObject {
        @Min(1L)
        private Integer numberField;

        public RequestObject(Integer numberField) {
            this.numberField = numberField;
        }

        public Integer getNumberField() {
            return numberField;
        }
    }

    @Controller("/json")
    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    class JsonController {
        @Post("/jsonBody")
        String jsonBody(@Valid @Body RequestObject data) {
            return "blah";
        }
    }

    @Controller("/global-errors")
    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    class GlobalErrorController {

        @Error(global = true, exception = GloballyHandledException.class)
        @Produces(io.micronaut.http.MediaType.TEXT_PLAIN)
        @Status(HttpStatus.OK)
        String globallyHandledException(GloballyHandledException throwable) {
            return throwable.getMessage();
        }

        @Error(global = true, status = HttpStatus.I_AM_A_TEAPOT)
        @Produces(io.micronaut.http.MediaType.TEXT_PLAIN)
        @Status(HttpStatus.OK)
        String globalControllerHandlerForStatus() {
            return "global status";
        }

    }

    @Singleton
    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    class CodecExceptionExceptionHandler
        implements ExceptionHandler<CodecException, HttpResponse> {

        @Override
        public HttpResponse handle(HttpRequest request, CodecException exception) {
            return HttpResponse.badRequest("Invalid JSON: " + exception.getMessage()).contentType(MediaType.APPLICATION_JSON);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    class RuntimeErrorHandler implements ExceptionHandler<RuntimeException, HttpResponse> {

        @Override
        public HttpResponse handle(HttpRequest request, RuntimeException exception) {
            return HttpResponse.serverError("Exception: " + exception.getMessage())
                .contentType(MediaType.TEXT_PLAIN);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ErrorHandlerTest")
    class MyErrorHandler implements ExceptionHandler<MyException, HttpResponse> {

        @Override
        public HttpResponse handle(HttpRequest request, MyException exception) {
            return HttpResponse.ok("Exception Handled")
                .contentType(MediaType.TEXT_PLAIN);
        }
    }


    class MyException extends RuntimeException {
        public MyException(String badThings) {
            super(badThings);
        }
    }

    class AnotherException extends RuntimeException {
        public AnotherException(String badThings) {
            super(badThings);
        }
    }

    class GloballyHandledException extends Exception {
        public GloballyHandledException(String badThingsHappensGlobally) {
            super(badThingsHappensGlobally);
        }
    }
}
