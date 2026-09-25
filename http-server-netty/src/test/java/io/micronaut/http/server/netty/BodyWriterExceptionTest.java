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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exception of a body writer, thrown before the response was sent, is handled once like an
 * exception of the route, without running the request filters again; the response filters run
 * on the response of that error. A writer that fails on the response of that error answers the
 * default {@code 500} error response, and a writer that fails after the response
 * was committed aborts it.
 */
class BodyWriterExceptionTest {

    private static final String SPEC_NAME = "BodyWriterExceptionNettyTest";
    private static final String FAULTY = "application/x-faulty";
    private static final List<String> SOURCES = List.of("/netty-writer-errors/controller", "/netty-writer-errors/handler");

    private static final AtomicInteger REQUEST_FILTER = new AtomicInteger();
    private static final AtomicInteger RESPONSE_FILTER = new AtomicInteger();
    private static final AtomicInteger HANDLED = new AtomicInteger();

    private ApplicationContext ctx;
    private EmbeddedServer server;
    private HttpClient client;

    @BeforeEach
    void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
        REQUEST_FILTER.set(0);
        RESPONSE_FILTER.set(0);
        HANDLED.set(0);
    }

    @AfterEach
    void stop() {
        client.close();
        ctx.close();
    }

    @Test
    void writerExceptionIsHandledOnceWithoutRunningTheRequestFiltersAgain() throws Exception {
        for (String source : SOURCES) {
            int handled = HANDLED.get();
            int requestFilter = REQUEST_FILTER.get();
            int responseFilter = RESPONSE_FILTER.get();

            java.net.http.HttpResponse<String> response = get(source + "/handled");

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.getCode(), response.statusCode(), source);
            assertEquals("handled writer failure", response.body(), source);
            assertEquals(handled + 1, HANDLED.get(), source);
            // the request filters ran once, for the response the writer failed on
            assertEquals(requestFilter + 1, REQUEST_FILTER.get(), source);
            // the response filters ran for the response the writer failed on and again for the
            // response of the error that replaces it
            assertEquals(responseFilter + 2, RESPONSE_FILTER.get(), source);
        }
    }

    @Test
    void writerFailingOnTheErrorResponseAnswersTheDefaultInternalServerError() throws Exception {
        for (String source : SOURCES) {
            int handled = HANDLED.get();

            java.net.http.HttpResponse<String> response = get(source + "/error-response-fails");

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getCode(), response.statusCode(), source);
            assertTrue(response.body().contains("Internal Server Error"), source + ": " + response.body());
            // handled once, not again for the failure of writing the response of the handler
            assertEquals(handled + 1, HANDLED.get(), source);
        }
    }

    @Test
    void writerFailingAfterTheResponseWasCommittedAbortsTheResponse() {
        int handled = HANDLED.get();

        // the status and the headers go out with the first item, the writer fails on the second
        assertThrows(IOException.class, () -> get("/netty-writer-errors/controller/streamed"));

        assertEquals(handled, HANDLED.get());
    }

    private java.net.http.HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(java.net.http.HttpRequest.newBuilder(URI.create(server.getURL() + path)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    /**
     * A body whose writer fails as its mode says.
     *
     * @param mode What the writer does
     */
    record Faulty(String mode) {
    }

    static final class WriterFailure extends RuntimeException {
        WriterFailure(String message) {
            super(message);
        }
    }

    @Singleton
    @Produces(FAULTY)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FaultyWriter implements MessageBodyWriter<Faulty> {
        @Override
        public void writeTo(Argument<Faulty> type, MediaType mediaType, Faulty object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            if (object.mode().equals("fail")) {
                throw new WriterFailure("writer failure");
            }
            try {
                outputStream.write(object.mode().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("Failed to write", e);
            }
        }
    }

    @Controller("/netty-writer-errors/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FaultyController {
        @Get(value = "/handled", produces = FAULTY)
        Faulty handled() {
            return new Faulty("fail");
        }

        @Get(value = "/error-response-fails", produces = FAULTY)
        Faulty errorResponseFails() {
            return new Faulty("fail");
        }

        @Get(value = "/streamed", produces = FAULTY)
        Flux<Faulty> streamed() {
            return Flux.just(new Faulty("first"), new Faulty("fail"));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FaultyRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/netty-writer-errors/handler/handled", (request, pathVariables) ->
                HttpResponse.ok(new Faulty("fail")).contentType(FAULTY));
            routes.GET("/netty-writer-errors/handler/error-response-fails", (request, pathVariables) ->
                HttpResponse.ok(new Faulty("fail")).contentType(FAULTY));
        }
    }

    @ServerFilter("/netty-writer-errors/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CountingFilter {
        @RequestFilter
        void request() {
            REQUEST_FILTER.incrementAndGet();
        }

        @ResponseFilter
        void response(MutableHttpResponse<?> response) {
            RESPONSE_FILTER.incrementAndGet();
        }
    }

    /**
     * Answers the failure of the writer, with a body whose writer fails again if the request asks
     * for that.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class WriterFailureHandler implements ExceptionHandler<WriterFailure, HttpResponse<?>> {
        @Override
        public HttpResponse<?> handle(HttpRequest request, WriterFailure exception) {
            HANDLED.incrementAndGet();
            if (request.getPath().endsWith("/error-response-fails")) {
                return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new Faulty("fail")).contentType(FAULTY);
            }
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body("handled " + exception.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
