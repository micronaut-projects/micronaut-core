package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.DefaultRouteBuilder;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exception thrown by a {@link MessageBodyWriter} while the response body is written, before
 * anything is sent, goes through the exception handlers and the error and status routes.
 */
public class WriterErrorHandlingTest {
    private static final String SPEC = "WriterErrorHandlingTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
        ctx.close();
    }

    @BeforeEach
    void reset() {
        ctx.getBean(AlwaysFailingHandler.class).calls.set(0);
        ctx.getBean(WriterErrorFilter.class).requests.set(0);
    }

    private static java.net.http.HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
            java.net.http.HttpRequest.newBuilder(server.getURI().resolve(path)).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void statusExceptionFromWriter() throws Exception {
        var response = get("/writer-errors/not-found");
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("writer says not found"), response.body());
    }

    @Test
    void statusExceptionFromWriterRunsLocalStatusRoute() throws Exception {
        var response = get("/writer-errors-status/not-found");
        assertEquals(404, response.statusCode());
        assertEquals("status route", response.body());
    }

    @Test
    void exceptionHandlerForWriterException() throws Exception {
        var response = get("/writer-errors/custom");
        assertEquals(418, response.statusCode());
        assertEquals("handled: custom writer failure", response.body());
    }

    @Test
    void exceptionHandlerForBlockingWriterException() throws Exception {
        var response = get("/writer-errors/blocking");
        assertEquals(418, response.statusCode());
        assertEquals("handled: blocking writer failure", response.body());
    }

    @Test
    void errorRouteForWriterException() throws Exception {
        var response = get("/writer-errors-local/custom");
        assertEquals(409, response.statusCode());
        assertEquals("local error route: custom writer failure", response.body());
    }

    @Test
    void functionalRoute() throws Exception {
        var response = get("/writer-errors-functional/custom");
        assertEquals(418, response.statusCode());
        assertEquals("handled: custom writer failure", response.body());
    }

    @Test
    void errorResponseWriterFailsToo() throws Exception {
        var response = get("/writer-errors/always");
        assertEquals(500, response.statusCode());
        assertEquals(1, ctx.getBean(AlwaysFailingHandler.class).calls.get());
    }

    @Test
    void responseFiltersRunOnTheErrorResponseAndRequestFiltersDoNotRunAgain() throws Exception {
        var response = get("/writer-errors/custom");
        assertEquals(418, response.statusCode());
        assertEquals("true", response.headers().firstValue("X-Filtered").orElse(null));
        assertEquals(1, ctx.getBean(WriterErrorFilter.class).requests.get());
    }

    @Test
    void responseFilterReplacesTheErrorResponse() throws Exception {
        var response = get("/writer-errors/replaced");
        assertEquals(202, response.statusCode());
        assertEquals("replaced by filter", response.body());
    }

    @Test
    void responseFilterFailingOnTheErrorResponse() throws Exception {
        var response = get("/writer-errors/filter-fails");
        assertEquals(503, response.statusCode());
        assertEquals("filter failure handled", response.body());
    }

    @Test
    void streamingFailureAfterFirstChunkAborts() {
        assertThrows(IOException.class, () -> get("/writer-errors/stream"));
    }

    record NotFoundBody() {
    }

    record CustomBody(String message) {
    }

    record BlockingBody(String message) {
    }

    record AlwaysFailingBody() {
    }

    static final class CustomWriterException extends RuntimeException {
        CustomWriterException(String message) {
            super(message);
        }
    }

    static final class AlwaysFailingException extends RuntimeException {
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/writer-errors")
    static class WriterErrorController {
        @Get(value = "/not-found", produces = MediaType.TEXT_PLAIN)
        NotFoundBody notFound() {
            return new NotFoundBody();
        }

        @Get(value = "/custom", produces = MediaType.TEXT_PLAIN)
        CustomBody custom() {
            return new CustomBody("custom writer failure");
        }

        @Get(value = "/blocking", produces = MediaType.TEXT_PLAIN)
        BlockingBody blocking() {
            return new BlockingBody("blocking writer failure");
        }

        @Get(value = "/always", produces = MediaType.TEXT_PLAIN)
        AlwaysFailingBody always() {
            return new AlwaysFailingBody();
        }

        @Get(value = "/replaced", produces = MediaType.TEXT_PLAIN)
        CustomBody replaced() {
            return new CustomBody("custom writer failure");
        }

        @Get(value = "/filter-fails", produces = MediaType.TEXT_PLAIN)
        CustomBody filterFails() {
            return new CustomBody("custom writer failure");
        }

        @Get(value = "/stream", produces = MediaType.TEXT_PLAIN)
        Flux<CustomBody> stream() {
            return Flux.just(new CustomBody("first"), new CustomBody("fail"));
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/writer-errors-status")
    static class StatusRouteController {
        @Get(value = "/not-found", produces = MediaType.TEXT_PLAIN)
        NotFoundBody notFound() {
            return new NotFoundBody();
        }

        @Error(status = HttpStatus.NOT_FOUND)
        @Produces(MediaType.TEXT_PLAIN)
        String notFoundStatus() {
            return "status route";
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/writer-errors-local")
    static class ErrorRouteController {
        @Get(value = "/custom", produces = MediaType.TEXT_PLAIN)
        CustomBody custom() {
            return new CustomBody("custom writer failure");
        }

        @Error(CustomWriterException.class)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> onCustom(CustomWriterException e) {
            return HttpResponse.<String>status(HttpStatus.CONFLICT).body("local error route: " + e.getMessage());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class FunctionalTarget {
        @Executable
        CustomBody custom() {
            return new CustomBody("custom writer failure");
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class FunctionalRoutes extends DefaultRouteBuilder {
        FunctionalRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
            super(executionHandleLocator, uriNamingStrategy);
            GET("/writer-errors-functional/custom", FunctionalTarget.class, "custom").produces(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    static final class FilterFailure extends RuntimeException {
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerFilter("/writer-errors/**")
    static class WriterErrorFilter {
        final AtomicInteger requests = new AtomicInteger();

        @RequestFilter
        void request() {
            requests.incrementAndGet();
        }

        @ResponseFilter
        HttpResponse<?> response(HttpRequest<?> request, MutableHttpResponse<?> response) {
            response.header("X-Filtered", "true");
            if (response.code() == HttpStatus.I_AM_A_TEAPOT.getCode()) {
                String path = request.getPath();
                if (path.endsWith("/replaced")) {
                    return HttpResponse.<String>status(HttpStatus.ACCEPTED).body("replaced by filter").contentType(MediaType.TEXT_PLAIN_TYPE);
                }
                if (path.endsWith("/filter-fails")) {
                    throw new FilterFailure();
                }
            }
            return response;
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class FilterFailureHandler implements ExceptionHandler<FilterFailure, HttpResponse<String>> {
        @Override
        public HttpResponse<String> handle(HttpRequest request, FilterFailure exception) {
            return HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("filter failure handled");
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class NotFoundWriter implements MessageBodyWriter<NotFoundBody> {
        @Override
        public void writeTo(Argument<NotFoundBody> type, MediaType mediaType, NotFoundBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "writer says not found");
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class CustomWriter implements MessageBodyWriter<CustomBody> {
        @Override
        public void writeTo(Argument<CustomBody> type, MediaType mediaType, CustomBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            if (object.message().equals("first")) {
                try {
                    outputStream.write("first".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new CodecException("write failed", e);
                }
                return;
            }
            throw new CustomWriterException(object.message());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class BlockingWriter implements MessageBodyWriter<BlockingBody> {
        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public void writeTo(Argument<BlockingBody> type, MediaType mediaType, BlockingBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new CustomWriterException(object.message());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class AlwaysFailingWriter implements MessageBodyWriter<AlwaysFailingBody> {
        @Override
        public void writeTo(Argument<AlwaysFailingBody> type, MediaType mediaType, AlwaysFailingBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new AlwaysFailingException();
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class CustomWriterExceptionHandler implements ExceptionHandler<CustomWriterException, HttpResponse<String>> {
        @Override
        public HttpResponse<String> handle(HttpRequest request, CustomWriterException exception) {
            return HttpResponse.<String>status(HttpStatus.I_AM_A_TEAPOT).body("handled: " + exception.getMessage());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    static class AlwaysFailingHandler implements ExceptionHandler<AlwaysFailingException, HttpResponse<AlwaysFailingBody>> {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public HttpResponse<AlwaysFailingBody> handle(HttpRequest request, AlwaysFailingException exception) {
            calls.incrementAndGet();
            return HttpResponse.<AlwaysFailingBody>status(HttpStatus.CONFLICT).body(new AlwaysFailingBody());
        }
    }
}
