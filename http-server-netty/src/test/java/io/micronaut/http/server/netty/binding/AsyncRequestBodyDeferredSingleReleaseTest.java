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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.form.FormParts;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A controller method that answers with a single-valued publisher it did not return directly, one
 * inside a stage or a response, keeps its {@link AsyncRequestBody} until that publisher ends: the
 * publisher is subscribed to after the route completed, and can read the elements or the parts
 * the route created. The reads are released when the publisher ends. The streams are in
 * {@link AsyncRequestBodyStreamReleaseTest}.
 */
class AsyncRequestBodyDeferredSingleReleaseTest {
    private static final String SPEC_NAME = "AsyncRequestBodyDeferredSingleReleaseTest";
    private static final String PENDING = "pending";
    private static final String BOUNDARY = "deferred-boundary";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @ParameterizedTest
    @ValueSource(strings = {"stage-mono", "stage-response-mono", "response-mono"})
    void aDeferredSingleValueReadsTheElementsTheRouteCreated(String route) throws Exception {
        try (ApplicationContext ctx = start()) {
            String response = send(ctx, "/deferred-single/elements/" + route, MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"}]", 0);

            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            assertTrue(response.contains("\"FRED\""), response);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stage-mono", "stage-response-mono", "response-mono"})
    void aDeferredSingleValueReadsThePartsTheRouteCreated(String route) throws Exception {
        try (ApplicationContext ctx = start()) {
            String form = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"name\"\r\n"
                + "\r\n"
                + "Fred\r\n"
                + "--" + BOUNDARY + "--\r\n";
            String response = send(ctx, "/deferred-single/parts/" + route, MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY, form, 0);

            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            assertTrue(response.contains("FRED"), response);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stage-mono", "stage-response-mono", "response-mono"})
    void theReadIsReleasedWhenTheDeferredSingleValueEnds(String route) throws Exception {
        try (ApplicationContext ctx = start()) {
            // the body never completes: the read the publisher started cannot complete
            String response = send(ctx, "/deferred-single/pending/" + route, MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar", 100_000);

            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            // the read was running when the publisher was subscribed to, after the route completed
            assertTrue(response.contains("\"" + PENDING + "\""), response);
            // and aborted when the publisher ended
            assertEquals(CancellationException.class.getSimpleName(), ctx.getBean(Probe.class).awaitOutcome(), response);
        }
    }

    private static ApplicationContext start() {
        ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        ctx.getBean(EmbeddedServer.class).start();
        return ctx;
    }

    /**
     * @param missing The bytes of the body that are announced and never sent
     * @return The response, read until the server closed the connection
     */
    private static String send(ApplicationContext ctx, String path, String contentType, String content, int missing) throws IOException {
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout((int) TIMEOUT.multipliedBy(3).toMillis());
            byte[] body = content.getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + path + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Connection: close\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + (body.length + missing) + "\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    record Person(String name) {
    }

    record Status(String outcome) {
    }

    /**
     * Records the outcome of the read a publisher started.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Probe {
        private final AtomicReference<String> outcome = new AtomicReference<>("none");
        private final ExecutorService executor;

        /**
         * @param executor Completes the asynchronous work of the methods: a thread of the server,
         *                 in the scope of the leak detector of the test
         */
        Probe(@Named(TaskExecutors.BLOCKING) ExecutorService executor) {
            this.executor = executor;
        }

        <T> CompletionStage<T> async(Supplier<T> supplier) {
            return CompletableFuture.supplyAsync(supplier, executor);
        }

        void track(CompletionStage<?> read) {
            outcome.set(PENDING);
            read.whenComplete((ignored, error) -> {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                outcome.set(cause == null ? "completed" : cause.getClass().getSimpleName());
            });
        }

        String outcome() {
            return outcome.get();
        }

        String awaitOutcome() throws InterruptedException {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (PENDING.equals(outcome()) && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            return outcome();
        }
    }

    @Controller("/deferred-single/elements")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ElementsController {
        private final Probe probe;

        ElementsController(Probe probe) {
            this.probe = probe;
        }

        private static Mono<Person> first(AsyncRequestBody body) {
            // created by the route, and read when the publisher is subscribed to
            BodyElements<Person> elements = body.elements(Person.class);
            return Mono.defer(() -> Mono.fromCompletionStage(elements.next()))
                .map(person -> new Person(person.orElseThrow().name().toUpperCase(Locale.ROOT)));
        }

        @Post(uri = "/stage-mono", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<Mono<Person>> stageMono(AsyncRequestBody body) {
            Mono<Person> first = first(body);
            return probe.async(() -> first);
        }

        @Post(uri = "/stage-response-mono", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<Mono<Person>>> stageResponseMono(AsyncRequestBody body) {
            Mono<Person> first = first(body);
            return probe.async(() -> HttpResponse.ok(first));
        }

        @Post(uri = "/response-mono", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<Mono<Person>> responseMono(AsyncRequestBody body) {
            return HttpResponse.ok(first(body));
        }
    }

    @Controller("/deferred-single/parts")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PartsController {
        private final Probe probe;

        PartsController(Probe probe) {
            this.probe = probe;
        }

        private static Mono<String> name(AsyncRequestBody body) {
            // created by the route, and read when the publisher is subscribed to
            FormParts parts = body.parts();
            return Mono.defer(() -> {
                AtomicReference<String> name = new AtomicReference<>("none");
                return Mono.fromCompletionStage(parts.part("name", part -> part.text().thenAccept(name::set)))
                    .map(found -> name.get().toUpperCase(Locale.ROOT));
            });
        }

        @Post(uri = "/stage-mono", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<Mono<String>> stageMono(AsyncRequestBody body) {
            Mono<String> name = name(body);
            return probe.async(() -> name);
        }

        @Post(uri = "/stage-response-mono", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<Mono<String>>> stageResponseMono(AsyncRequestBody body) {
            Mono<String> name = name(body);
            return probe.async(() -> HttpResponse.ok(name));
        }

        @Post(uri = "/response-mono", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        HttpResponse<Mono<String>> responseMono(AsyncRequestBody body) {
            return HttpResponse.ok(name(body));
        }
    }

    @Controller("/deferred-single/pending")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PendingController {
        private final Probe probe;

        PendingController(Probe probe) {
            this.probe = probe;
        }

        private Mono<Status> status(AsyncRequestBody body) {
            BodyElements<Person> elements = body.elements(Person.class);
            return Mono.fromSupplier(() -> {
                // a read that never completes, started when the publisher is subscribed to
                probe.track(elements.forEach(person -> new CompletableFuture<>()));
                return new Status(probe.outcome());
            });
        }

        @Post(uri = "/stage-mono", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<Mono<Status>> stageMono(AsyncRequestBody body) {
            Mono<Status> status = status(body);
            return probe.async(() -> status);
        }

        @Post(uri = "/stage-response-mono", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<Mono<Status>>> stageResponseMono(AsyncRequestBody body) {
            Mono<Status> status = status(body);
            return probe.async(() -> HttpResponse.ok(status));
        }

        @Post(uri = "/response-mono", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<Mono<Status>> responseMono(AsyncRequestBody body) {
            return HttpResponse.ok(status(body));
        }
    }
}
