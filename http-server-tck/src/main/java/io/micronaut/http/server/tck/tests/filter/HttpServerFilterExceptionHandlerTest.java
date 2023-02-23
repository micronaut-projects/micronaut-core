package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.ServerUnderTest;
import io.micronaut.http.server.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.function.BiConsumer;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

public class HttpServerFilterExceptionHandlerTest {
    private static final String SPEC_NAME = "FilterErrorHandlerTest";

    @Test
    public void exceptionHandlerTest() throws IOException {
        assertion(HttpRequest.GET("/foo"),
            AssertionUtils.assertThrowsStatus(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private static void assertion(HttpRequest<?> request, BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(request)
            .assertion(assertion)
            .run();
    }


    static class FooException extends RuntimeException {

    }

    @Filter(value = MATCH_ALL_PATTERN)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ErrorThrowningFilter implements HttpServerFilter {

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Mono.error(new FooException());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/foo")
    static class FooController {

        @Produces(MediaType.TEXT_PLAIN)
        @Get
        String index() {
            return "Hello World";
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class FooExceptionHandler implements ExceptionHandler<FooException, HttpResponse<?>> {

        private final ErrorResponseProcessor<?> errorResponseProcessor;

        public FooExceptionHandler(ErrorResponseProcessor<?> errorResponseProcessor) {
            this.errorResponseProcessor = errorResponseProcessor;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest request, FooException exception) {
            return errorResponseProcessor.processResponse(ErrorContext.builder(request)
                .cause(exception)
                .build(), HttpResponse.unprocessableEntity());
        }
    }
}
