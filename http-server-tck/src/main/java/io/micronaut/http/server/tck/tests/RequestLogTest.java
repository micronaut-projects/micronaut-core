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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.context.event.HttpRequestLog;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link HttpRequestLog} of {@link HttpRequestTerminatedEvent}, with the request log enabled.
 * A server that keeps no record is skipped: its fields must be absent rather than wrong, which the
 * absence of the record covers.
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
public class RequestLogTest {
    public static final String SPEC_NAME = "RequestLogTest";

    @Test
    void success() throws IOException {
        logged(
            HttpRequest.POST("/request-log/echo/abc", "hello").contentType(MediaType.TEXT_PLAIN),
            (server, request) -> {
                Recorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder().status(HttpStatus.OK).body("hello-abc").build());
                HttpRequestLog log = recorder.await();
                assertEquals(200, log.status().getAsInt());
                assertEquals("/request-log/echo/{id}", log.routeTemplate().orElseThrow());
                // the suites run this over HTTP/1.1 and over HTTP/2
                HttpVersion version = log.httpVersion().orElseThrow();
                assertTrue(version == HttpVersion.HTTP_1_1 || version == HttpVersion.HTTP_2_0, version::toString);
                assertTrue(log.isCompleted());
                assertTrue(log.failure().isEmpty());
                assertTrue(log.failurePhase().isEmpty());
                assertTimesOrdered(log);
                log.requestBodyBytes().ifPresent(n -> assertEquals(5, n));
                log.responseBodyBytes().ifPresent(n -> assertEquals(9, n));
            });
    }

    @Test
    void handlerException() throws IOException {
        logged(
            HttpRequest.GET("/request-log/boom"),
            (server, request) -> {
                Recorder recorder = recorder(server);
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder().status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                HttpRequestLog log = recorder.await();
                assertEquals(500, log.status().getAsInt());
                assertEquals("/request-log/boom", log.routeTemplate().orElseThrow());
                assertEquals(HttpRequestLog.FailurePhase.HANDLER, log.failurePhase().orElseThrow());
                assertEquals("boom", log.failure().orElseThrow().getMessage());
                // the error response itself was written completely
                assertTrue(log.isCompleted());
                assertTimesOrdered(log);
            });
    }

    @Test
    void streamedResponse() throws IOException {
        logged(
            HttpRequest.GET("/request-log/stream"),
            (server, request) -> {
                Recorder recorder = recorder(server);
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder().status(HttpStatus.OK).body("0123456789").build());
                HttpRequestLog log = recorder.await();
                assertEquals(200, log.status().getAsInt());
                assertEquals("/request-log/stream", log.routeTemplate().orElseThrow());
                assertTrue(log.isCompleted());
                assertTrue(log.failure().isEmpty());
                assertTimesOrdered(log);
                log.requestBodyBytes().ifPresent(n -> assertEquals(0, n));
                log.responseBodyBytes().ifPresent(n -> assertEquals(10, n));
            });
    }

    @Test
    void notFound() throws IOException {
        logged(
            HttpRequest.GET("/request-log/missing"),
            (server, request) -> {
                Recorder recorder = recorder(server);
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder().status(HttpStatus.NOT_FOUND).build());
                HttpRequestLog log = recorder.await();
                assertEquals(404, log.status().getAsInt());
                // no route matched: no template, so the raw path never becomes a tag
                assertFalse(log.routeTemplate().isPresent());
                assertTrue(log.isCompleted());
            });
    }

    /**
     * Run a scenario with the request log enabled. The property is the Netty server's; a server
     * without it keeps no record, and the test is skipped.
     */
    private static void logged(HttpRequest<?> request, BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Map.of("micronaut.server.netty.request-log-enabled", true))
            .request(request)
            .assertion(assertion)
            .run();
    }

    static void assertTimesOrdered(HttpRequestLog log) {
        long produced = log.responseProducedNanos().orElseThrow();
        log.requestStartNanos().ifPresent(start -> assertTrue(start <= produced));
        log.headersReadNanos().ifPresent(headers -> assertTrue(headers <= produced));
        log.responseWrittenNanos().ifPresent(written -> assertTrue(produced <= written));
        log.totalDurationNanos().ifPresent(total -> assertTrue(total >= 0));
    }

    private static Recorder recorder(ServerUnderTest server) {
        Recorder recorder = server.getApplicationContext().getBean(Recorder.class);
        recorder.reset();
        return recorder;
    }

    @Controller("/request-log")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class LogController {

        @Post("/echo/{id}")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String echo(String id, @Body String body) {
            return body + "-" + id;
        }

        @Get("/boom")
        @Produces(MediaType.TEXT_PLAIN)
        String boom() {
            throw new IllegalStateException("boom");
        }

        @Get("/stream")
        @Produces(MediaType.TEXT_PLAIN)
        Flux<byte[]> stream() {
            return Flux.just("01234", "56789").map(s -> s.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        private final AtomicReference<CompletableFuture<HttpRequestTerminatedEvent>> event = new AtomicReference<>(new CompletableFuture<>());

        void reset() {
            event.set(new CompletableFuture<>());
        }

        HttpRequestLog await() {
            HttpRequestTerminatedEvent e;
            try {
                e = event.get().get(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new AssertionError("no terminated event", ex);
            }
            Assumptions.assumeTrue(e.getRequestLog().isPresent(), "the server keeps no request log");
            return e.getRequestLog().get();
        }

        @Override
        public void onApplicationEvent(HttpRequestTerminatedEvent e) {
            event.get().complete(e);
        }
    }
}
