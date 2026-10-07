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
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.netty.channel.EventLoop;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactivestreams.Publisher;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A controller or request filter method that starts a read of its {@link AsyncRequestBody} and
 * completes without waiting for it: the read is aborted, and what it buffered or staged is
 * released, when the method completed, before the response is written or the filter chain
 * continues, not when the request ends.
 *
 * <p>The body is sent in part only, so the reads cannot complete, and the consumers of the reads
 * never complete their stages. A response filter records the outcome of the read, and the files
 * of the upload directory, in headers: it runs after the method completed, before the response is
 * written. A route after a filter answers the outcome of the filter's read. The buffers are
 * checked by the leak presence detector of the tests.</p>
 */
class AsyncRequestBodyReleaseTest {
    private static final String SPEC_NAME = "AsyncRequestBodyReleaseTest";
    private static final String PENDING = "pending";
    private static final String BOUNDARY = "release-boundary";

    @TempDir
    Path uploads;

    @Test
    void theElementsARouteDidNotWaitForAreReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/elements", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theElementsOfARouteThatAnswersWithAStageAreReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/elements-stage", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theElementsOfAReactiveRouteAreReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/elements-mono", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void thePartsARouteDidNotWaitForAreReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/parts", MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY, partialForm());
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theFileARouteDidNotWaitForIsDeletedBeforeTheResponse() throws Exception {
        Response response = send("/release/transfer", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
        // the route answered once the file was being written: it is gone, and was not published
        assertEquals("0", response.header("X-Files"), response.head());
    }

    @Test
    void theReadOfARouteThatFailsIsReleasedBeforeTheErrorResponse() throws Exception {
        Response response = send("/release/fails", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertEquals(500, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theFileOfARouteThatFailsIsDeletedBeforeTheErrorResponse() throws Exception {
        Response response = send("/release/transfer-fails", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertEquals(500, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
        assertEquals("0", response.header("X-Files"), response.head());
    }

    @Test
    void theReadOfAFilterIsReleasedBeforeTheRouteRuns() throws Exception {
        Response response = send("/release/filter/elements", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertEquals(200, response.status(), response.head());
        // the route answers the outcome of the filter's read when it runs
        assertTrue(response.head().endsWith(CancellationException.class.getSimpleName()), response.head());
    }

    @Test
    void theReadOfACopyInAFilterIsReleasedBeforeTheRouteRuns() throws Exception {
        Response response = send("/release/filter/copy", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertEquals(200, response.status(), response.head());
        assertTrue(response.head().endsWith(CancellationException.class.getSimpleName()), response.head());
    }

    @Test
    void theFileOfAFilterThatAnswersIsDeletedBeforeTheResponse() throws Exception {
        Response response = send("/release/filter/answer", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
        assertEquals("0", response.header("X-Files"), response.head());
    }

    @Test
    void theDecodedBodyARouteDidNotWaitForIsReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/body", MediaType.APPLICATION_JSON, "{\"name\":\"Fred\",\"nick\":\"Fre");
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theDecodedBodyOfACopyARouteDidNotWaitForIsReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/body-copy", MediaType.APPLICATION_JSON, "{\"name\":\"Fred\",\"nick\":\"Fre");
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theTextARouteDidNotWaitForIsReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/text", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theBytesARouteDidNotWaitForAreReleasedBeforeTheResponse() throws Exception {
        Response response = send("/release/bytes", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
    }

    @Test
    void theDecodedBodyOfAFilterIsReleasedBeforeTheRouteRuns() throws Exception {
        Response response = send("/release/filter/body", MediaType.APPLICATION_JSON, "{\"name\":\"Fred\",\"nick\":\"Fre");
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
        // the route answers the outcome of the filter's read when it runs
        assertTrue(response.head().endsWith(CancellationException.class.getSimpleName()), response.head());
    }

    @Test
    void theDecodedBodyOfACopyInAFilterIsReleasedBeforeTheRouteRuns() throws Exception {
        Response response = send("/release/filter/body-copy", MediaType.APPLICATION_JSON, "{\"name\":\"Fred\",\"nick\":\"Fre");
        assertEquals(200, response.status(), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
        // the route answers the outcome of the filter's read when it runs
        assertTrue(response.head().endsWith(CancellationException.class.getSimpleName()), response.head());
    }

    @Test
    void theElementsOfARouteAFilterStoppedWaitingForAreReleasedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/elements", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertFallback(response);
    }

    @Test
    void theElementsOfAReactiveRouteAFilterStoppedWaitingForAreReleasedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/elements-mono", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertFallback(response);
    }

    @Test
    void thePartsOfARouteAFilterStoppedWaitingForAreReleasedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/parts", MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY, partialForm());
        assertFallback(response);
    }

    @Test
    void thePartsOfAReactiveRouteAFilterStoppedWaitingForAreReleasedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/parts-mono", MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY, partialForm());
        assertFallback(response);
    }

    @Test
    void theFileOfARouteAFilterStoppedWaitingForIsDeletedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/transfer", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertFallback(response);
    }

    @Test
    void theFileOfAReactiveRouteAFilterStoppedWaitingForIsDeletedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/transfer-mono", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertFallback(response);
    }

    @Test
    void theElementsOfAFilterAnotherFilterStoppedWaitingForAreReleasedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/filter/elements", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertFallback(response);
    }

    @Test
    void theElementsOfAReactiveFilterAnotherFilterStoppedWaitingForAreReleasedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/filter/elements-mono", MediaType.APPLICATION_JSON, "[{\"name\":\"Fred\"},{\"name\":\"Bar");
        assertFallback(response);
    }

    @Test
    void theFileOfAFilterAnotherFilterStoppedWaitingForIsDeletedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/filter/transfer", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertFallback(response);
    }

    @Test
    void theFileOfAReactiveFilterAnotherFilterStoppedWaitingForIsDeletedBeforeTheFallbackResponse() throws Exception {
        Response response = send("/release/cancel/filter/transfer-mono", MediaType.TEXT_PLAIN, "x".repeat(16 * 1024));
        assertFallback(response);
    }

    /**
     * The filter that stopped waiting for the rest of the chain answered: the read the cancelled
     * method started was aborted, and the file it staged is gone, when its response is written,
     * while the request is open.
     */
    private static void assertFallback(Response response) {
        assertEquals(200, response.status(), response.head());
        assertTrue(response.head().endsWith("fallback"), response.head());
        assertEquals(CancellationException.class.getSimpleName(), response.header("X-Outcome"), response.head());
        assertEquals("0", response.header("X-Files"), response.head());
    }

    /**
     * Send the head of a request and a part of its body, which is never completed, and read the
     * response.
     */
    private Response send(String path, String contentType, String partialBody) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            ctx.getBean(Probe.class).directory = uploads;
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                byte[] body = partialBody.getBytes(StandardCharsets.UTF_8);
                String head = "POST " + path + " HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    // more than is sent: the body never completes
                    + "Content-Length: " + (body.length + 100_000) + "\r\n"
                    + "\r\n";
                out.write(head.getBytes(StandardCharsets.US_ASCII));
                out.write(body);
                out.flush();
                return Response.read(socket.getInputStream());
            }
        }
    }

    private static String partialForm() {
        return "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"name\"\r\n"
            + "\r\n"
            + "Fred\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.txt\"\r\n"
            + "Content-Type: text/plain\r\n"
            + "\r\n"
            + "x".repeat(16 * 1024);
    }

    /**
     * A response: its head, and the body of a response with a length.
     */
    private record Response(int status, Map<String, String> headers, String head) {

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        static Response read(InputStream in) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                bytes.write(b);
                matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
            }
            String head = bytes.toString(StandardCharsets.US_ASCII);
            String[] lines = head.split("\r\n");
            int status = Integer.parseInt(lines[0].split(" ")[1]);
            Map<String, String> headers = new TreeMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) {
                    headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
                }
            }
            String length = headers.get("content-length");
            if (length != null) {
                head += new String(in.readNBytes(Integer.parseInt(length)), StandardCharsets.UTF_8);
            }
            return new Response(status, headers, head);
        }
    }

    /**
     * Records the outcome of the read a method started.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Probe {
        private final AtomicReference<String> outcome = new AtomicReference<>("none");
        private final CompletableFuture<Boolean> waiting = new CompletableFuture<>();
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
         * A method that never completes started its read: the filter that waits for it stops
         * waiting, once the method returned and the filter subscribed to its result, which is
         * when the event loop the method runs on is free to run a task.
         */
        void waiting(HttpRequest<?> request) {
            eventLoop(request).execute(() -> waiting.complete(true));
        }

        /**
         * Tell the filter that waits once the method returned, and the file of the transfer is
         * being written.
         */
        void waitingForFile(HttpRequest<?> request) {
            eventLoop(request).execute(() -> async(() -> {
                awaitFile();
                return waiting.complete(true);
            }));
        }

        /**
         * Nobody waits for the release of a cancelled method, and it may continue on another
         * thread, e.g. to delete a file: the filter that stopped waiting answers once the read
         * ended and its file is gone, or, when the body is not released, after a while, while
         * the request is still open.
         */
        CompletionStage<Boolean> whenReleased() {
            return async(() -> {
                for (int i = 0; i < 200 && (PENDING.equals(outcome()) || !"0".equals(files())); i++) {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                return true;
            });
        }

        private static EventLoop eventLoop(HttpRequest<?> request) {
            EventLoop eventLoop = ((NettyHttpRequest<?>) request).getChannelHandlerContext().channel().eventLoop();
            if (!eventLoop.inEventLoop()) {
                throw new IllegalStateException("The method does not run on the event loop");
            }
            return eventLoop;
        }

        CompletionStage<Boolean> whenWaiting() {
            return waiting;
        }

        Path destination() {
            return Objects.requireNonNull(directory).resolve("upload.txt");
        }

        /**
         * Wait until the file of a transfer is being written.
         */
        void awaitFile() {
            for (int i = 0; i < 200 && "0".equals(files()); i++) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            if ("0".equals(files())) {
                throw new IllegalStateException("The file of the transfer was not created");
            }
        }

        String files() {
            try (Stream<Path> list = Files.list(Objects.requireNonNull(directory))) {
                return String.valueOf(list.count());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * A consumer of an element or a part that never completes.
     */
    private static <T> CompletionStage<T> never() {
        return new CompletableFuture<>();
    }

    record Person(String name) {
    }

    @Controller("/release")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReleaseController {
        private final Probe probe;

        ReleaseController(Probe probe) {
            this.probe = probe;
        }

        @Post(uri = "/elements", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<String> elements(AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            return HttpResponse.ok("started");
        }

        @Post(uri = "/elements-stage", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> elementsStage(AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            return probe.async(() -> HttpResponse.ok("started"));
        }

        @Post(uri = "/elements-mono", consumes = MediaType.APPLICATION_JSON)
        Mono<HttpResponse<String>> elementsMono(AsyncRequestBody body) {
            return Mono.fromSupplier(() -> {
                probe.track(body.elements(Person.class).forEach(person -> never()));
                return HttpResponse.ok("started");
            });
        }

        @Post(uri = "/parts", consumes = MediaType.MULTIPART_FORM_DATA)
        CompletionStage<HttpResponse<String>> parts(AsyncRequestBody body) {
            probe.track(body.parts().forEach(part -> never()));
            return CompletableFuture.completedFuture(HttpResponse.ok("started"));
        }

        @Post(uri = "/transfer", consumes = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> transfer(AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            // answers once the file is being written
            return probe.async(() -> {
                probe.awaitFile();
                return HttpResponse.ok("started");
            });
        }

        @Post(uri = "/body", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<String> body(AsyncRequestBody body) {
            probe.track(body.body(Person.class));
            return HttpResponse.ok("started");
        }

        @Post(uri = "/body-copy", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<String> bodyCopy(AsyncRequestBody body) {
            probe.track(body.copy().body(Person.class));
            return HttpResponse.ok("started");
        }

        @Post(uri = "/text", consumes = MediaType.TEXT_PLAIN)
        HttpResponse<String> text(AsyncRequestBody body) {
            probe.track(body.text());
            return HttpResponse.ok("started");
        }

        @Post(uri = "/bytes", consumes = MediaType.TEXT_PLAIN)
        HttpResponse<String> bytes(AsyncRequestBody body) {
            probe.track(body.bytes(1024 * 1024));
            return HttpResponse.ok("started");
        }

        @Post(uri = "/fails", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<String> fails(AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            throw new IllegalStateException("The route failed after it started a read");
        }

        @Post(uri = "/transfer-fails", consumes = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> transferFails(AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            // fails once the file is being written
            return probe.async(() -> {
                probe.awaitFile();
                throw new IllegalStateException("The route failed after it started a read");
            });
        }

        @Post(uri = "/filter/{read}", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String filtered(String read) {
            // the route does not read the body: it answers the outcome of the filter's read
            return probe.outcome();
        }
    }

    @ServerFilter("/release/filter")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReadingFilter {
        private final Probe probe;

        ReadingFilter(Probe probe) {
            this.probe = probe;
        }

        @RequestFilter("/elements")
        CompletionStage<@Nullable HttpResponse<?>> elements(AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            return CompletableFuture.completedFuture(null);
        }

        @RequestFilter("/copy")
        CompletionStage<@Nullable HttpResponse<?>> copy(AsyncRequestBody body) {
            probe.track(body.copy().elements(Person.class).forEach(person -> never()));
            return CompletableFuture.completedFuture(null);
        }

        @RequestFilter("/body")
        CompletionStage<@Nullable HttpResponse<?>> body(AsyncRequestBody body) {
            probe.track(body.body(Person.class));
            return CompletableFuture.completedFuture(null);
        }

        @RequestFilter("/body-copy")
        CompletionStage<@Nullable HttpResponse<?>> bodyCopy(AsyncRequestBody body) {
            probe.track(body.copy().body(Person.class));
            return CompletableFuture.completedFuture(null);
        }

        @RequestFilter("/answer")
        CompletionStage<@Nullable HttpResponse<?>> answer(AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            // answers once the file is being written
            return probe.async(() -> {
                probe.awaitFile();
                return HttpResponse.ok("answered");
            });
        }
    }

    /**
     * Routes that start a read and never complete: the filter around them stops waiting.
     */
    @Controller("/release/cancel")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CancelledController {
        private final Probe probe;

        CancelledController(Probe probe) {
            this.probe = probe;
        }

        @Post(uri = "/elements", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> elements(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            probe.waiting(request);
            return never();
        }

        @Post(uri = "/elements-mono", consumes = MediaType.APPLICATION_JSON)
        Mono<HttpResponse<String>> elementsMono(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            probe.waiting(request);
            return Mono.never();
        }

        @Post(uri = "/parts", consumes = MediaType.MULTIPART_FORM_DATA)
        CompletionStage<HttpResponse<String>> parts(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.parts().forEach(part -> never()));
            probe.waiting(request);
            return never();
        }

        @Post(uri = "/parts-mono", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<HttpResponse<String>> partsMono(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.parts().forEach(part -> never()));
            probe.waiting(request);
            return Mono.never();
        }

        @Post(uri = "/transfer", consumes = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> transfer(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            probe.waitingForFile(request);
            return never();
        }

        @Post(uri = "/transfer-mono", consumes = MediaType.TEXT_PLAIN)
        Mono<HttpResponse<String>> transferMono(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            probe.waitingForFile(request);
            return Mono.never();
        }

        @Post(uri = "/filter/{read}", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String filtered(String read) {
            return "not reached";
        }
    }

    /**
     * Stops waiting for the rest of the chain once the method in it started its read, as a
     * timeout would, and answers instead: the rest of the chain is cancelled. Its answer takes
     * as long as the release of the body the cancelled method read.
     */
    @ServerFilter("/release/cancel/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TimeoutFilter implements Ordered {
        private final Probe probe;

        TimeoutFilter(Probe probe) {
            this.probe = probe;
        }

        @Override
        public int getOrder() {
            // inside the filter of the outcome, around the filters that read
            return -100;
        }

        @RequestFilter
        Publisher<HttpResponse<?>> timeout(FilterContinuation<Publisher<HttpResponse<?>>> continuation) {
            return Mono.from(continuation.proceed())
                .timeout(Mono.fromCompletionStage(probe.whenWaiting()))
                .onErrorResume(TimeoutException.class, error -> Mono.fromCompletionStage(probe.whenReleased())
                    .map(released -> HttpResponse.ok("fallback")));
        }
    }

    /**
     * Request filters that start a read and never complete: the filter around them stops waiting.
     */
    @ServerFilter("/release/cancel/filter")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CancelledReadingFilter {
        private final Probe probe;

        CancelledReadingFilter(Probe probe) {
            this.probe = probe;
        }

        @RequestFilter("/elements")
        CompletionStage<@Nullable HttpResponse<?>> elements(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            probe.waiting(request);
            return never();
        }

        @RequestFilter("/elements-mono")
        Mono<HttpResponse<?>> elementsMono(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.elements(Person.class).forEach(person -> never()));
            probe.waiting(request);
            return Mono.never();
        }

        @RequestFilter("/transfer")
        CompletionStage<@Nullable HttpResponse<?>> transfer(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            probe.waitingForFile(request);
            return never();
        }

        @RequestFilter("/transfer-mono")
        Mono<HttpResponse<?>> transferMono(HttpRequest<?> request, AsyncRequestBody body) {
            probe.track(body.transferTo(probe.destination()));
            probe.waitingForFile(request);
            return Mono.never();
        }
    }

    @ServerFilter("/release/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OutcomeFilter implements Ordered {
        private final Probe probe;

        OutcomeFilter(Probe probe) {
            this.probe = probe;
        }

        @Override
        public int getOrder() {
            // around the other filters: the response of a filter that answers passes through it
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @ResponseFilter
        void outcome(MutableHttpResponse<?> response) {
            // after the method completed, before the response is written
            response.header("X-Outcome", probe.outcome());
            response.header("X-Files", probe.files());
        }
    }
}
