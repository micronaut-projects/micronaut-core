package io.micronaut.http.server.netty.stream

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ResponseClosedException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "StreamingErrorHandlerSpec")
@Property(name = "micronaut.http.client.read-timeout", value = "30s")
class StreamingErrorHandlerSpec extends Specification{

    @Inject
    StreamingErrorClient client

    @Inject
    GlobalHandlerController globalHandler

    @Inject
    ErrorController controller

    def setup() {
        globalHandler.handlerInvoked = false
        controller.handlerInvoked = false
    }

    void "global error handler is invoked on an immediate error for a chunked response"() {
        when:
        client.getFluxChunkedImmediateGlobalError()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.I_AM_A_TEAPOT
        e.response.getBody(String).get() == "Your request is globally erroneous: Cannot process request."
        globalHandler.handlerInvoked
        !controller.handlerInvoked
    }

    void "global error handler is not invoked on a delayed error for a chunked response"() {
        when:
        client.getFluxChunkedDelayedGlobalError()

        then:
        def e = thrown(ResponseClosedException)
        !globalHandler.handlerInvoked
        !controller.handlerInvoked
    }

    void "local error handler is invoked on an immediate error for a chunked response"() {
        when:
        client.getFluxChunkedImmediateLocalError()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.I_AM_A_TEAPOT
        e.response.getBody(String).get() == "Your request is locally erroneous: Cannot process request."
        !globalHandler.handlerInvoked
        controller.handlerInvoked
    }

    void "local error handler is not invoked on a delayed error for a chunked response"() {
        when:
        client.getFluxChunkedDelayedLocalError()

        then:
        def e = thrown(ResponseClosedException)
        !globalHandler.handlerInvoked
        !controller.handlerInvoked
    }

    void "error handlers are not invoked for an unspecified error for a chunked response"() {
        when:
        client.getFluxChunkedUnspecifiedError()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.getBody(String).get().contains("Cannot process request.")
        !globalHandler.handlerInvoked
        !controller.handlerInvoked
    }

    @Requires(property = "spec.name", value = "StreamingErrorHandlerSpec")
    @Client("/streaming-errors")
    static interface StreamingErrorClient {

        @Get("/flux-chunked-immediate-global-error")
        HttpResponse<?> getFluxChunkedImmediateGlobalError()

        @Get("/flux-chunked-delayed-global-error")
        HttpResponse<?> getFluxChunkedDelayedGlobalError()

        @Get("/flux-chunked-immediate-local-error")
        HttpResponse<?> getFluxChunkedImmediateLocalError()

        @Get("/flux-chunked-delayed-local-error")
        HttpResponse<?> getFluxChunkedDelayedLocalError()

        @Get("/flux-chunked-immediate-unspecified-error")
        HttpResponse<?> getFluxChunkedUnspecifiedError()
    }

    @Requires(property = "spec.name", value = "StreamingErrorHandlerSpec")
    @Controller
    static class GlobalHandlerController {
        boolean handlerInvoked

        @Error(global = true)
        HttpResponse<String> handleMyGlobalTestException(HttpRequest<?> request, MyGlobalException exception) {
            handlerInvoked = true
            var error = "Your request is globally erroneous: " + exception.getMessage();
            return HttpResponse.<String>status(HttpStatus.I_AM_A_TEAPOT, "Bad request")
                    .body(error);
        }
    }

    @Requires(property = "spec.name", value = "StreamingErrorHandlerSpec")
    @Controller("/streaming-errors")
    static class ErrorController {

        boolean handlerInvoked

        @Get("/flux-chunked-immediate-global-error")
        Flux<String> fluxChunkedImmediateGlobalError() {
            return Flux.error(new MyGlobalException("Cannot process request."))
        }

        @Get("/flux-chunked-delayed-global-error")
        Flux<String> fluxChunkedDelayedGlobalError() {
            return Flux.just("1", "2", "3").handle((data, sink) -> {
                System.out.println("Handling %s".formatted(data))
                if (data.equals("3")) {
                    sink.error(new MyGlobalException("Cannot process request."))
                } else {
                    sink.next(data)
                }
            });
        }

        @Get("/flux-chunked-immediate-local-error")
        Flux<String> fluxChunkedImmediateLocalError() {
            return Flux.error(new MyLocalException("Cannot process request."))
        }

        @Get("/flux-chunked-delayed-local-error")
        Flux<String> fluxChunkedDelayedLocalError() {
            return Flux.just("1", "2", "3").handle((data, sink) -> {
                System.out.println("Handling %s".formatted(data))
                if (data.equals("3")) {
                    sink.error(new MyLocalException("Cannot process request."))
                } else {
                    sink.next(data)
                }
            });
        }

        @Get("/flux-chunked-immediate-unspecified-error")
        Flux<String> fluxChunkedImmediateUnspecifiedError() {
            return Flux.error(new MyUnhandledException("Cannot process request."))
        }

        @Error
        HttpResponse<String> handleMyLocalTestException(HttpRequest<?> request, MyLocalException exception) {
            handlerInvoked = true
            var error = "Your request is locally erroneous: " + exception.getMessage();
            return HttpResponse.<String>status(HttpStatus.I_AM_A_TEAPOT, "Bad request")
                    .body(error);
        }
    }

    static class MyGlobalException extends RuntimeException {
        MyGlobalException(String message) {
            super(message)
        }
    }

    static class MyLocalException extends RuntimeException {
        MyLocalException(String message) {
            super(message)
        }
    }

    static class MyUnhandledException extends RuntimeException {
        MyUnhandledException(String message) {
            super(message)
        }
    }
}
