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
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})

public class ErrorHandlerTest {
    public static final String SPEC_NAME = "ErrorHandlerTest";
    public static final String PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS = "micronaut.server.cors.configurations.web.allowed-origins";
    public static final String PROPERTY_MICRONAUT_SERVER_CORS_ENABLED = "micronaut.server.cors.enabled";
    public static final String LOCALHOST = "http://localhost:8080";

    @Test
    void testCustomGlobalExceptionHandlersDeclaredInController() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
                PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
            ),
            HttpRequest.GET("/errors/global-ctrl").header(HttpHeaders.CONTENT_TYPE, io.micronaut.http.MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "bad things happens globally",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)));
    }

    @Test
    void testCustomGlobalExceptionHandlers() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
                PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
            ), HttpRequest.GET("/errors/global")
                .header(HttpHeaders.CONTENT_TYPE, io.micronaut.http.MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "Exception Handled",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)));
    }

    @Test
    void testCustomGlobalExceptionHandlersForPOSTWithBody() throws IOException {
        Map<String, Object> configuration = CollectionUtils.mapOf(
            PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
            PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        );
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration)) {
            ObjectMapper objectMapper = server.getApplicationContext().getBean(ObjectMapper.class);
            HttpRequest<?> request = HttpRequest.POST("/json/errors/global", objectMapper.writeValueAsString(new RequestObject(101)))
                .header(HttpHeaders.CONTENT_TYPE, io.micronaut.http.MediaType.APPLICATION_JSON);
            AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "\"message\":\"Error: bad things when post and body in request\"",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
        }
    }

    @Test
    void testCustomGlobalStatusHandlersDeclaredInController() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
                PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
            ), HttpRequest.GET("/errors/global-status-ctrl"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "global status",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)));
    }

    @Test
    void testLocalExceptionHandlers() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
            PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
            PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            HttpRequest.GET("/errors/local"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "bad things",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)));
    }

    @Test
    void jsonMessageFormatErrorsReturn400() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
            PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
            PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            HttpRequest.POST("/json/jsonBody", "{\"numberField\": \"textInsteadOfNumber\"}"),
            (server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .headers(Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
                    .build()
            ));
    }

    @Test
    void corsHeadersArePresentAfterFailedDeserialisationWhenErrorHandlerIsUsed() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
            PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
            PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        ), HttpRequest.POST("/json/errors/global", "{\"numberField\": \"string is not a number\"}")
                .header(HttpHeaders.ORIGIN, LOCALHOST),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Collections.singletonMap(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCALHOST))
                .build()));
    }

    @Test
    void corsHeadersArePresentAfterFailedDeserialisation() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
                PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList(LOCALHOST),
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
            ), HttpRequest.POST("/json/jsonBody", "{\"numberField\": \"string is not a number\"}")
                .header(HttpHeaders.ORIGIN, LOCALHOST),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .headers(Collections.singletonMap(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCALHOST))
                .build()));
    }

    @Test
    void corsHeadersArePresentAfterExceptions() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
            PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList(LOCALHOST),
            PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        ), HttpRequest.GET("/errors/global").header(HttpHeaders.ORIGIN, LOCALHOST),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Collections.singletonMap(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCALHOST))
                .build()));
    }

    @Test
    void messageValidationErrorsReturn400() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
                PROPERTY_MICRONAUT_SERVER_CORS_CONFIGURATIONS_WEB_ALLOWED_ORIGINS, Collections.singletonList("http://localhost:8080"),
            PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        ), HttpRequest.POST("/json/jsonBody", "{\"numberField\": 0}"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .headers(Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
                .build()));
    }

    @Controller("/secret")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SecretController {
        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String index() {
            return "area 51 hosts an alien";
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/errors")
    static class ErrorController {

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
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class JsonErrorController {

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
    static class RequestObject {
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
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class JsonController {
        @Post("/jsonBody")
        String jsonBody(@Valid @Body RequestObject data) {
            return "blah";
        }
    }

    @Controller("/global-errors")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GlobalErrorController {

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
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CodecExceptionExceptionHandler
        implements ExceptionHandler<CodecException, HttpResponse> {

        @Override
        public HttpResponse handle(HttpRequest request, CodecException exception) {
            return HttpResponse.badRequest("Invalid JSON: " + exception.getMessage()).contentType(MediaType.APPLICATION_JSON);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RuntimeErrorHandler implements ExceptionHandler<RuntimeException, HttpResponse> {

        @Override
        public HttpResponse handle(HttpRequest request, RuntimeException exception) {
            return HttpResponse.serverError("Exception: " + exception.getMessage())
                .contentType(MediaType.TEXT_PLAIN);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MyErrorHandler implements ExceptionHandler<MyException, HttpResponse> {

        @Override
        public HttpResponse handle(HttpRequest request, MyException exception) {
            return HttpResponse.ok("Exception Handled")
                .contentType(MediaType.TEXT_PLAIN);
        }
    }


    static class MyException extends RuntimeException {
        public MyException(String badThings) {
            super(badThings);
        }
    }

    static class AnotherException extends RuntimeException {
        public AnotherException(String badThings) {
            super(badThings);
        }
    }

    static class GloballyHandledException extends Exception {
        public GloballyHandledException(String badThingsHappensGlobally) {
            super(badThingsHappensGlobally);
        }
    }
}
