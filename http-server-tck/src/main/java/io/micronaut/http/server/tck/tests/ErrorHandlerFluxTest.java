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
                .status(HttpStatus.BAD_REQUEST)
                .body("Your request is erroneous: Cannot process request.")
                .build()));
    }

    @Test
    void testErrorHandlerWithFluxSingleResultThrownException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/errors/flux-single-exception"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .body("Your request is erroneous: Cannot process request.")
                .build()));
    }

    @Test
    void testErrorHandlerWithFluxChunkedSignaledImmediateError() throws IOException {
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
                Executable e = () -> server.exchange(request);
                Assertions.assertThrows(HttpClientException.class, e);
            });
    }

    @Test
    void testErrorHandlerWithFluxSingleResultSignaledError() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/errors/flux-single-error"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
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
        public HttpResponse<String> entityNotFoundHandler(HttpRequest<?> request, MyTestException exception) {
            var error = "Your request is erroneous: " + exception.getMessage();
            return HttpResponse.<String>status(HttpStatus.BAD_REQUEST, "Bad request")
                .body(error);
        }

    }

    static class MyTestException extends RuntimeException {

        public MyTestException(String message) {
            super(message);
        }
    }
}
