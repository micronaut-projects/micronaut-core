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
package io.micronaut.http.server.tck.tests.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An exception that a {@link MessageBodyWriter} throws before the response was sent is handled
 * like an exception of the route: by {@link ExceptionHandler} beans, error routes and status
 * routes, for controller routes and handler routes alike. A writer that fails on the response of
 * that error answers the default {@code 500} error response instead of handling the error again.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class BodyWriterExceptionTest {
    public static final String SPEC_NAME = "BodyWriterExceptionTest";

    static final String FAULTY = "application/x-faulty";

    /**
     * Each body is returned by a controller method and by a handler function.
     */
    private static final List<String> SOURCES = List.of("/writer-errors/controller", "/writer-errors/handler");

    @Test
    void writerExceptionIsHandledByAnExceptionHandlerBean() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/handled"), HttpResponseAssertion.builder()
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("handled writer failure")
                    .build());
            }
        }
    }

    @Test
    void exceptionOfABlockingWriterIsHandledByAnExceptionHandlerBean() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/blocking-handled"), HttpResponseAssertion.builder()
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("handled writer failure")
                    .build());
            }
        }
    }

    @Test
    void writerExceptionIsHandledByAnErrorRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/writer-errors/controller/routed"), HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("controller error route: routed writer failure")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/writer-errors/handler/routed"), HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("error route: routed writer failure")
                .build());
        }
    }

    @Test
    void httpStatusExceptionOfAWriterIsAnsweredByAStatusRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/writer-errors/controller/status"), HttpResponseAssertion.builder()
                .status(HttpStatus.GONE)
                .body("controller status route")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/writer-errors/handler/status"), HttpResponseAssertion.builder()
                .status(HttpStatus.GONE)
                .body("status route")
                .build());
        }
    }

    @Test
    void unhandledWriterExceptionIsAnInternalServerError() throws IOException {
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
    void writerFailingOnTheErrorResponseAnswersAPlainInternalServerError() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                int handled = ErrorResponseFailureHandler.INVOCATIONS.get();
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/error-response-fails"), HttpResponseAssertion.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
                // handled once, not again for the failure of writing the response of the handler
                assertEquals(handled + 1, ErrorResponseFailureHandler.INVOCATIONS.get());
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    static void write(String mode, OutputStream outputStream) {
        switch (mode) {
            case "handled" -> throw new WriterFailure("writer failure");
            case "routed" -> throw new RoutedWriterFailure("routed writer failure");
            case "status" -> throw new HttpStatusException(HttpStatus.GONE, "gone");
            case "unhandled" -> throw new UnhandledWriterFailure("unhandled writer failure");
            case "error-response-fails" -> throw new ErrorResponseFailure("error response failure");
            default -> {
                try {
                    outputStream.write(mode.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new CodecException("Failed to write", e);
                }
            }
        }
    }

    /**
     * A body whose writer fails as its mode says.
     *
     * @param mode What the writer does
     */
    record Faulty(String mode) {
    }

    /**
     * A body whose blocking writer fails as its mode says.
     *
     * @param mode What the writer does
     */
    record BlockingFaulty(String mode) {
    }

    static final class WriterFailure extends RuntimeException {
        WriterFailure(String message) {
            super(message);
        }
    }

    static final class RoutedWriterFailure extends RuntimeException {
        RoutedWriterFailure(String message) {
            super(message);
        }
    }

    static final class UnhandledWriterFailure extends RuntimeException {
        UnhandledWriterFailure(String message) {
            super(message);
        }
    }

    static final class ErrorResponseFailure extends RuntimeException {
        ErrorResponseFailure(String message) {
            super(message);
        }
    }

    @Singleton
    @Produces(FAULTY)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FaultyWriter implements MessageBodyWriter<Faulty> {
        @Override
        public void writeTo(Argument<Faulty> type, MediaType mediaType, Faulty object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            write(object.mode(), outputStream);
        }
    }

    @Singleton
    @Produces(FAULTY)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BlockingFaultyWriter implements MessageBodyWriter<BlockingFaulty> {
        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public void writeTo(Argument<BlockingFaulty> type, MediaType mediaType, BlockingFaulty object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            write(object.mode(), outputStream);
        }
    }

    @Controller("/writer-errors/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FaultyController {
        @Get(value = "/handled", produces = FAULTY)
        Faulty handled() {
            return new Faulty("handled");
        }

        @Get(value = "/blocking-handled", produces = FAULTY)
        BlockingFaulty blockingHandled() {
            return new BlockingFaulty("handled");
        }

        @Get(value = "/routed", produces = FAULTY)
        Faulty routed() {
            return new Faulty("routed");
        }

        @Get(value = "/status", produces = FAULTY)
        Faulty status() {
            return new Faulty("status");
        }

        @Get(value = "/unhandled", produces = FAULTY)
        Faulty unhandled() {
            return new Faulty("unhandled");
        }

        @Get(value = "/error-response-fails", produces = FAULTY)
        Faulty errorResponseFails() {
            return new Faulty("error-response-fails");
        }

        @Error(RoutedWriterFailure.class)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> routedWriterFailure(RoutedWriterFailure failure) {
            return HttpResponse.<String>status(HttpStatus.I_AM_A_TEAPOT).body("controller error route: " + failure.getMessage());
        }

        @Error(status = HttpStatus.GONE)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> gone() {
            return HttpResponse.<String>status(HttpStatus.GONE).body("controller status route");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FaultyRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            for (String mode : List.of("handled", "routed", "status", "unhandled", "error-response-fails")) {
                routes.GET("/writer-errors/handler/" + mode, (request, pathVariables) ->
                    HttpResponse.ok(new Faulty(mode)).contentType(FAULTY));
            }
            routes.GET("/writer-errors/handler/blocking-handled", (request, pathVariables) ->
                HttpResponse.ok(new BlockingFaulty("handled")).contentType(FAULTY));

            routes.error(RoutedWriterFailure.class, (request, error) ->
                HttpResponse.status(HttpStatus.I_AM_A_TEAPOT).body("error route: " + error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.status(HttpStatus.GONE, request ->
                HttpResponse.status(HttpStatus.GONE).body("status route").contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class WriterFailureHandler implements ExceptionHandler<WriterFailure, HttpResponse<String>> {
        @Override
        public HttpResponse<String> handle(HttpRequest request, WriterFailure exception) {
            return HttpResponse.<String>status(HttpStatus.UNPROCESSABLE_ENTITY).body("handled " + exception.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    /**
     * Answers with a body whose writer fails again.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ErrorResponseFailureHandler implements ExceptionHandler<ErrorResponseFailure, HttpResponse<Faulty>> {
        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public HttpResponse<Faulty> handle(HttpRequest request, ErrorResponseFailure exception) {
            INVOCATIONS.incrementAndGet();
            return HttpResponse.<Faulty>status(HttpStatus.UNPROCESSABLE_ENTITY).body(new Faulty("error-response-fails")).contentType(FAULTY);
        }
    }
}
