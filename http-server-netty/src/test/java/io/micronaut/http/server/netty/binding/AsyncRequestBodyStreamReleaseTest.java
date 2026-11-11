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
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A controller method that answers with a streamed response keeps its {@link AsyncRequestBody}
 * until the stream ends: the stream can be made of the reads of the body, and the reads are
 * released, aborted if they did not complete, when the stream completes, fails or is cancelled
 * because the client disconnected. The single-value cases are in
 * {@link AsyncRequestBodyReleaseTest}.
 *
 * <p>The requests are written on a socket, so the body can be sent while the response streams,
 * and the client can disconnect in the middle of the stream. The buffers are checked by the leak
 * presence detector of the tests.</p>
 */
class AsyncRequestBodyStreamReleaseTest {
    private static final String SPEC_NAME = "AsyncRequestBodyStreamReleaseTest";
    private static final String PENDING = "pending";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path uploads;

    @Test
    void aStreamedResponseAnswersEachElementAsItArrives() throws Exception {
        try (ApplicationContext ctx = start(); Socket socket = connect(ctx)) {
            OutputStream out = socket.getOutputStream();
            ChunkedReader in = new ChunkedReader(socket.getInputStream());
            out.write(chunkedHead("/stream-release/echo", MediaType.APPLICATION_JSON));
            int count = 20;
            for (int i = 0; i < count; i++) {
                // each element is sent once the previous one was answered: the body and the
                // response stream at the same time
                writeChunk(out, (i == 0 ? "[" : ",") + "{\"name\":\"item-" + i + "\"}");
                in.readUntil("ITEM-" + i + "\"");
            }
            writeChunk(out, "]");
            out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String body = in.readToEnd();
            assertTrue(in.head().startsWith("HTTP/1.1 200"), in.head());
            for (int i = 0; i < count; i++) {
                assertTrue(body.contains("\"ITEM-" + i + "\""), body);
            }
            // the read completed, and the body was released when the stream completed
            Probe probe = ctx.getBean(Probe.class);
            assertEquals("completed", probe.awaitOutcome(), body);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stage-response-echo", "stage-echo", "mono-response-echo"})
    void aStreamReturnedInsideAnAsynchronousResultIsMadeOfTheElementsOfTheBody(String route) throws Exception {
        try (ApplicationContext ctx = start(); Socket socket = connect(ctx)) {
            OutputStream out = socket.getOutputStream();
            ChunkedReader in = new ChunkedReader(socket.getInputStream());
            out.write(chunkedHead("/stream-release/" + route, MediaType.APPLICATION_JSON));
            int count = 5;
            for (int i = 0; i < count; i++) {
                // the stage, or the single-valued publisher, completed with the stream before the
                // body was read: the stream keeps the body, as one the route returned directly
                writeChunk(out, (i == 0 ? "[" : ",") + "{\"name\":\"item-" + i + "\"}");
                in.readUntil("ITEM-" + i + "\"");
            }
            writeChunk(out, "]");
            out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String body = in.readToEnd();
            assertTrue(in.head().startsWith("HTTP/1.1 200"), in.head() + body);
            for (int i = 0; i < count; i++) {
                assertTrue(body.contains("\"ITEM-" + i + "\""), body);
            }
            // the read completed, and the body was released when the stream completed
            Probe probe = ctx.getBean(Probe.class);
            assertEquals("completed", probe.awaitOutcome(), body);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stage-response-disconnect", "stage-disconnect"})
    void theReadIsReleasedWhenTheClientDisconnectsFromAStreamReturnedInsideAStage(String route) throws Exception {
        try (ApplicationContext ctx = start()) {
            Probe probe = ctx.getBean(Probe.class);
            try (Socket socket = connect(ctx)) {
                sendPartial(socket, "/stream-release/" + route, MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
                ChunkedReader in = new ChunkedReader(socket.getInputStream());
                String first = in.readUntil("}");
                assertTrue(first.contains("\"outcome\":\"" + PENDING + "\""), in.head() + first);
                assertEquals(PENDING, probe.outcome());
            }
            // the client disconnected: the stream is cancelled, and the read aborted
            assertEquals(CancellationException.class.getSimpleName(), probe.awaitOutcome());
            assertTrue(probe.cancelled.await(), "the stream was cancelled");
        }
    }

    @Test
    void theFileOfAStreamingRouteIsKeptUntilTheStreamEndsAndDeletedThen() throws Exception {
        try (ApplicationContext ctx = start(); Socket socket = connect(ctx)) {
            sendPartial(socket, "/stream-release/transfer", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
            ChunkedReader in = new ChunkedReader(socket.getInputStream());
            String body = in.readToEnd();
            assertTrue(in.head().startsWith("HTTP/1.1 200"), in.head() + body);
            // while the stream ran, the file was being written
            assertTrue(body.contains("\"outcome\":\"" + PENDING + "\""), body);
            assertTrue(body.contains("\"files\":\"1\""), body);
            // the stream completed: the transfer was aborted, and its file deleted
            Probe probe = ctx.getBean(Probe.class);
            assertEquals(CancellationException.class.getSimpleName(), probe.awaitOutcome(), body);
            assertEquals("0", probe.awaitNoFiles(), body);
        }
    }

    @Test
    void theReadIsReleasedWhenTheClientDisconnectsFromTheStream() throws Exception {
        try (ApplicationContext ctx = start()) {
            Probe probe = ctx.getBean(Probe.class);
            try (Socket socket = connect(ctx)) {
                sendPartial(socket, "/stream-release/disconnect", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
                ChunkedReader in = new ChunkedReader(socket.getInputStream());
                // the first item of a stream that does not end: the read is still running
                String first = in.readUntil("}");
                assertTrue(first.contains("\"outcome\":\"" + PENDING + "\""), in.head() + first);
                assertEquals(PENDING, probe.outcome());
            }
            // the client disconnected: the stream is cancelled, and the read aborted
            assertEquals(CancellationException.class.getSimpleName(), probe.awaitOutcome());
            assertTrue(probe.cancelled.await(), "the stream was cancelled");
        }
    }

    @Test
    void theReadIsReleasedWhenTheStreamFails() throws Exception {
        try (ApplicationContext ctx = start(); Socket socket = connect(ctx)) {
            sendPartial(socket, "/stream-release/fails", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
            ChunkedReader in = new ChunkedReader(socket.getInputStream());
            String first = in.readUntil("}");
            assertTrue(first.contains("\"outcome\":\"" + PENDING + "\""), in.head() + first);
            // the stream fails after its first item: the read is aborted
            Probe probe = ctx.getBean(Probe.class);
            assertEquals(PENDING, probe.outcome());
            probe.fail();
            assertEquals(CancellationException.class.getSimpleName(), probe.awaitOutcome(), first);
        }
    }

    private ApplicationContext start() {
        ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        ctx.getBean(Probe.class).directory = uploads;
        ctx.getBean(EmbeddedServer.class).start();
        return ctx;
    }

    private static Socket connect(ApplicationContext ctx) throws IOException {
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
        Socket socket = new Socket(server.getHost(), server.getPort());
        socket.setSoTimeout((int) TIMEOUT.multipliedBy(3).toMillis());
        return socket;
    }

    private static byte[] chunkedHead(String path, String contentType) {
        return ("POST " + path + " HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeChunk(OutputStream out, String chunk) throws IOException {
        byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
        out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    /**
     * Send the head of a request and a part of its body, which is never completed.
     */
    private static void sendPartial(Socket socket, String path, String contentType, String partialBody) throws IOException {
        OutputStream out = socket.getOutputStream();
        byte[] body = partialBody.getBytes(StandardCharsets.UTF_8);
        String head = "POST " + path + " HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Content-Type: " + contentType + "\r\n"
            // more than is sent: the body never completes
            + "Content-Length: " + (body.length + 100_000) + "\r\n"
            + "\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    /**
     * Reads a chunked response: its head, and its body as it arrives.
     */
    private static final class ChunkedReader {
        private final InputStream in;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private @Nullable String head;
        private boolean ended;

        ChunkedReader(InputStream in) {
            this.in = in;
        }

        String head() throws IOException {
            if (head == null) {
                head = line(true);
            }
            return head;
        }

        /**
         * Read until the body contains the text.
         *
         * @return The body read so far
         */
        String readUntil(String text) throws IOException {
            head();
            while (!body.toString(StandardCharsets.UTF_8).contains(text)) {
                if (!readChunk()) {
                    throw new IllegalStateException("The response ended before " + text + ": " + head + body.toString(StandardCharsets.UTF_8));
                }
            }
            return body.toString(StandardCharsets.UTF_8);
        }

        /**
         * Read until the last chunk, or the connection closed.
         *
         * @return The body
         */
        String readToEnd() throws IOException {
            head();
            while (readChunk()) {
                // read the next chunk
            }
            return body.toString(StandardCharsets.UTF_8);
        }

        private boolean readChunk() throws IOException {
            if (ended) {
                return false;
            }
            String size = line(false);
            if (size.isEmpty()) {
                ended = true;
                return false;
            }
            int length = Integer.parseInt(size.trim(), 16);
            if (length == 0) {
                line(false);
                ended = true;
                return false;
            }
            body.write(in.readNBytes(length));
            line(false);
            return true;
        }

        /**
         * @param block Read up to an empty line, the end of the head
         * @return The line, or the lines of the head, empty at the end of the stream
         */
        private String line(boolean block) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int matched = 0;
            int end = block ? 4 : 2;
            while (matched < end) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                bytes.write(b);
                matched = (b == '\r' && matched % 2 == 0) || (b == '\n' && matched % 2 == 1) ? matched + 1 : 0;
            }
            return bytes.toString(StandardCharsets.US_ASCII).trim();
        }
    }

    /**
     * Records the outcome of the read a method started.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Probe {
        private final AtomicReference<String> outcome = new AtomicReference<>("none");
        final Latch cancelled = new Latch();
        final CompletableFuture<Status> failure = new CompletableFuture<>();
        private final ExecutorService executor;
        volatile @Nullable Path directory;

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

        <T> CompletionStage<T> track(CompletionStage<T> read) {
            outcome.set(PENDING);
            read.whenComplete((ignored, error) -> {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                outcome.set(cause == null ? "completed" : cause.getClass().getSimpleName());
            });
            return read;
        }

        String outcome() {
            return outcome.get();
        }

        /**
         * Fail the stream of the route that waits for it, on a thread of the server.
         */
        void fail() {
            executor.execute(() -> failure.completeExceptionally(new IllegalStateException("The stream failed after it started")));
        }

        /**
         * Wait until the read ended.
         */
        String awaitOutcome() {
            return await(() -> !PENDING.equals(outcome()), this::outcome);
        }

        /**
         * Wait until the files of the upload directory were deleted.
         */
        String awaitNoFiles() {
            return await(() -> "0".equals(files()), this::files);
        }

        Path destination() {
            return Objects.requireNonNull(directory).resolve("upload.txt");
        }

        /**
         * Wait until the file of a transfer is being written.
         */
        String awaitFile() {
            return await(() -> !"0".equals(files()), this::files);
        }

        String files() {
            try (Stream<Path> list = Files.list(Objects.requireNonNull(directory))) {
                return String.valueOf(list.count());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        Status status() {
            return new Status(outcome(), files());
        }

        private static String await(Supplier<Boolean> condition, Supplier<String> value) {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (!condition.get() && System.nanoTime() < deadline) {
                sleep();
            }
            return value.get();
        }

        private static void sleep() {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Set once.
     */
    static final class Latch {
        private final CompletableFuture<Void> set = new CompletableFuture<>();

        void set() {
            set.complete(null);
        }

        boolean await() {
            Probe.await(set::isDone, () -> "");
            return set.isDone();
        }
    }

    record Person(String name) {
    }

    record Status(String outcome, String files) {
    }

    /**
     * A consumer of an element that never completes.
     */
    private static <T> CompletionStage<T> never() {
        return new CompletableFuture<>();
    }

    @Controller("/stream-release")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StreamReleaseController {
        private final Probe probe;

        StreamReleaseController(Probe probe) {
            this.probe = probe;
        }

        @Post(uri = "/echo", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Person> echo(AsyncRequestBody body) {
            BodyElements<Person> elements = body.elements(Person.class);
            // the elements are read as the stream of the response is subscribed to
            return Flux.create(sink -> probe.track(elements.forEach(person -> {
                sink.next(new Person(person.name().toUpperCase(Locale.ROOT)));
                return CompletableFuture.completedFuture(null);
            })).whenComplete((done, error) -> {
                if (error != null) {
                    sink.error(error);
                } else {
                    sink.complete();
                }
            }));
        }

        @Post(uri = "/stage-response-echo", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        CompletionStage<HttpResponse<Publisher<Person>>> stageResponseEcho(AsyncRequestBody body) {
            return probe.async(() -> HttpResponse.ok(echo(body)));
        }

        @Post(uri = "/stage-echo", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        CompletionStage<Publisher<Person>> stageEcho(AsyncRequestBody body) {
            return probe.async(() -> echo(body));
        }

        @Post(uri = "/mono-response-echo", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        Mono<HttpResponse<Publisher<Person>>> monoResponseEcho(AsyncRequestBody body) {
            return Mono.fromCompletionStage(() -> probe.async(() -> HttpResponse.ok(echo(body))));
        }

        @Post(uri = "/stage-response-disconnect", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        CompletionStage<HttpResponse<Publisher<Status>>> stageResponseDisconnect(AsyncRequestBody body) {
            return probe.async(() -> HttpResponse.ok(disconnect(body)));
        }

        @Post(uri = "/stage-disconnect", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        CompletionStage<Publisher<Status>> stageDisconnect(AsyncRequestBody body) {
            return probe.async(() -> disconnect(body));
        }

        @Post(uri = "/transfer", consumes = MediaType.TEXT_PLAIN, produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Status> transfer(AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            // one item once the file is being written, then the stream completes
            return Mono.fromCompletionStage(() -> probe.async(() -> {
                probe.awaitFile();
                return probe.status();
            })).flux();
        }

        @Post(uri = "/disconnect", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Status> disconnect(AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            // one item, and the stream never ends
            return Flux.concat(Mono.fromSupplier(probe::status), Flux.<Status>never())
                .doOnCancel(probe.cancelled::set);
        }

        @Post(uri = "/fails", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Status> fails(AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            // one item, then the stream fails when the test says so
            return Flux.concat(Mono.fromSupplier(probe::status), Mono.fromCompletionStage(probe.failure));
        }
    }
}
