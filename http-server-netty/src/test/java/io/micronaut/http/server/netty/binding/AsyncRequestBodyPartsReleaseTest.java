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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
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
import reactor.core.publisher.Sinks;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release of a multipart body with several parts: text fields and three files, which the
 * multipart configuration stores on disk when they are read into a {@link FormData}.
 *
 * <ul>
 *     <li>A route that walks {@link AsyncRequestBody#parts()} and completes while a part is being
 *     read, before the next part arrived: the read of the part is aborted, its staging file
 *     deleted, and the walk ends, before the response is written; the parts that arrive later
 *     are never delivered to the consumer.</li>
 *     <li>A route that reads one field of the parts and skips the others: the parts it did not
 *     read are discarded, and the connection serves the next request.</li>
 *     <li>A route with a {@link FormData} or a {@code List<FileUpload>}: the files it did not
 *     consume are deleted when the request ends, a read it started is aborted, and the file it
 *     transferred is kept.</li>
 *     <li>A streamed response made of the parts: every part is read, the body is released when
 *     the stream ends, and a client that disconnects aborts the walk.</li>
 *     <li>A filter that reads the parts of a copy of the body, whole or in part: the route gets
 *     the whole form.</li>
 *     <li>A {@link FormParts} argument, released like an {@link AsyncRequestBody}: when the route
 *     completed or failed, when its streamed response ended, and when a filter method completed,
 *     whose parts consume the body.</li>
 *     <li>A {@link FormPart} argument: a read of it that is still running when the route completed
 *     is aborted, and a part that was not read is left to the request.</li>
 *     <li>A route that fails while a part is being read, also a reactive one.</li>
 *     <li>The route that completes while a part is being read, and the {@link FormData} route,
 *     over HTTP/2.</li>
 * </ul>
 *
 * <p>The requests are written on a socket, chunked, so that a part can be withheld while the
 * route runs. A response filter records, in headers, the outcome of the reads, the parts that
 * were delivered and the files of the directories: it runs after the route completed, before the
 * response is written. The buffers are checked by the leak presence detector of the tests.</p>
 */
class AsyncRequestBodyPartsReleaseTest {
    private static final String SPEC_NAME = "AsyncRequestBodyPartsReleaseTest";
    private static final String PENDING = "pending";
    private static final String CANCELLED = CancellationException.class.getSimpleName();
    private static final String BOUNDARY = "parts-release-boundary";
    private static final String CONTENT_TYPE = MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DOC1 = "1".repeat(64 * 1024);
    private static final String DOC2 = "2".repeat(16 * 1024);
    private static final String DOC3 = "3".repeat(4 * 1024);

    @TempDir
    Path directory;

    // --- parts() in a controller, completed while a part is being read -------------------------

    @Test
    void thePartBeingReadIsAbortedAndTheLaterPartsAreNotDelivered() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead("/parts-release/partial"));
            // the field, and a part of the first file: the other files are withheld
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            int withheld = first.length() - DOC1.length() + 16 * 1024;
            writeChunk(out, first.substring(0, withheld));
            Response response = Response.read(socket.getInputStream());
            assertEquals(200, response.status(), response.head());
            // before the response was written: the transfer of the file was aborted, its staging
            // file deleted, and the walk ended
            assertEquals(CANCELLED, response.header("X-Read"), response.head());
            assertEquals(CANCELLED, response.header("X-Walk"), response.head());
            assertEquals("0", response.header("X-Staging"), response.head());
            assertEquals("Fred", response.header("X-Field"), response.head());
            assertEquals("name,d1.txt", response.header("X-Delivered"), response.head());

            // the rest of the form arrives after the response: it is discarded, and the
            // connection serves the next request
            writeChunk(out, first.substring(withheld) + file("d2.txt", DOC2) + file("d3.txt", DOC3) + end());
            out.write(lastChunk());
            out.flush();
            out.write(chunkedHead("/parts-release/skip"));
            writeChunk(out, field("name", "Next") + end());
            out.write(lastChunk());
            out.flush();
            Response next = Response.read(socket.getInputStream());
            assertEquals(200, next.status(), next.head());
            assertTrue(next.head().endsWith("true Next"), next.head());
            // the parts that arrived after the release were not delivered to the first consumer
            assertEquals(List.of("name", "d1.txt", "name"), probe.delivered());
            assertEquals("0", probe.files(probe.destinations()));
        }
    }

    @Test
    void thePartBeingReadOfAFormPartsArgumentIsAbortedBeforeTheResponse() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead("/parts-release/form-parts"));
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            int withheld = first.length() - DOC1.length() + 16 * 1024;
            writeChunk(out, first.substring(0, withheld));
            Response response = Response.read(socket.getInputStream());
            assertEquals(200, response.status(), response.head());
            // the parts of a FormParts argument are released like an AsyncRequestBody, when the
            // route completed, before the response is written: the transfer of the file was
            // aborted, its staging file deleted, and the walk ended
            assertEquals(CANCELLED, response.header("X-Read"), response.head());
            assertEquals(CANCELLED, response.header("X-Walk"), response.head());
            assertEquals("0", response.header("X-Staging"), response.head());
            assertEquals("name,d1.txt", response.header("X-Delivered"), response.head());

            // the rest of the form arrives after the response: it is discarded, and the
            // connection serves the next request
            writeChunk(out, first.substring(withheld) + file("d2.txt", DOC2) + file("d3.txt", DOC3) + end());
            out.write(lastChunk());
            out.flush();
            out.write(chunkedHead("/parts-release/skip"));
            writeChunk(out, field("name", "Next") + end());
            out.write(lastChunk());
            out.flush();
            Response next = Response.read(socket.getInputStream());
            assertEquals(200, next.status(), next.head());
            assertTrue(next.head().endsWith("true Next"), next.head());
            assertEquals(List.of("name", "d1.txt", "name"), probe.delivered());
            assertEquals("0", probe.files(probe.destinations()));
        }
    }

    @Test
    void theReadOfAFormPartArgumentIsAbortedBeforeTheResponse() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead("/parts-release/form-part"));
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            int withheld = first.length() - DOC1.length() + 16 * 1024;
            writeChunk(out, first.substring(0, withheld));
            Response response = Response.read(socket.getInputStream());
            assertEquals(200, response.status(), response.head());
            // the route completed while the transfer of its FormPart was running: it was
            // aborted, and its staging file deleted, before the response was written
            assertEquals(CANCELLED, response.header("X-Read"), response.head());
            assertEquals("0", response.header("X-Staging"), response.head());
            assertTrue(response.head().endsWith("Fred d1.txt"), response.head());
            writeChunk(out, first.substring(withheld) + file("d2.txt", DOC2) + end());
            out.write(lastChunk());
            out.flush();
            assertEquals("0", probe.files(probe.destinations()));
        }
    }

    @Test
    void aFormPartArgumentThatWasNotReadIsLeftToTheRequest() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Response response = sendWhole(socket, "/parts-release/form-part-unread");
            assertEquals(200, response.status(), response.head());
            // no read of the part was running: the route completed with the part unread, which
            // the request discards when it ends
            assertTrue(response.head().endsWith("Fred d1.txt"), response.head());
            Probe probe = ctx.getBean(Probe.class);
            assertEquals("0", probe.files(probe.destinations()));
        }
    }

    @Test
    void thePartBeingReadIsAbortedAndTheLaterPartsAreNotDeliveredOverHttp2() throws Exception {
        try (ApplicationContext ctx = start(http2())) {
            Probe probe = ctx.getBean(Probe.class);
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            int withheld = first.length() - DOC1.length() + 16 * 1024;
            Sinks.Many<ReadBuffer> body = Sinks.many().unicast().onBackpressureBuffer();
            emit(body, buffer(first.substring(0, withheld)));
            try (ByteBodyHttpResponse<?> response = exchange(ctx, "/parts-release/partial", body.asFlux())) {
                assertEquals(200, response.code());
                assertEquals("HTTP_2_0", response.getHeaders().get("X-Version"));
                assertEquals(CANCELLED, response.getHeaders().get("X-Read"));
                assertEquals(CANCELLED, response.getHeaders().get("X-Walk"));
                assertEquals("0", response.getHeaders().get("X-Staging"));
                assertEquals("name,d1.txt", response.getHeaders().get("X-Delivered"));
                // the rest of the form, if the client still sends it after the response, which
                // ended the stream: it is not delivered
                emit(body, buffer(first.substring(withheld) + file("d2.txt", DOC2) + file("d3.txt", DOC3) + end()));
                body.tryEmitComplete();
                assertEquals("started", response.byteBody().buffer().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            }
            assertEquals(List.of("name", "d1.txt"), probe.delivered());
            assertEquals("0", probe.files(probe.destinations()));
        }
    }

    // --- parts() in a controller that skips parts ----------------------------------------------

    @Test
    void thePartsARouteSkipsAreDiscardedAndTheConnectionIsReused() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            for (int i = 0; i < 3; i++) {
                out.write(chunkedHead("/parts-release/skip"));
                // a file before the field, which is skipped, and two files after it, which arrive
                // after the response
                writeChunk(out, file("d1.txt", DOC1));
                writeChunk(out, field("name", "Fred" + i) + next());
                Response response = Response.read(socket.getInputStream());
                assertEquals(200, response.status(), response.head());
                assertTrue(response.head().endsWith("true Fred" + i), response.head());
                assertEquals("completed", response.header("X-Walk"), response.head());
                writeChunk(out, rest(file("d2.txt", DOC2)));
                writeChunk(out, file("d3.txt", DOC3) + end());
                out.write(lastChunk());
                out.flush();
            }
            // only the fields were delivered: the files were discarded, and the connection
            // served the next requests
            assertEquals(List.of("name", "name", "name"), probe.delivered());
        }
    }

    // --- FormData and List<FileUpload> ----------------------------------------------------------

    @Test
    void theFilesAFormDataRouteDidNotConsumeAreDeletedAndItsReadAborted() throws Exception {
        try (ApplicationContext ctx = start(gatedIo()); Socket socket = connect(ctx)) {
            Response response = sendWhole(socket, "/parts-release/form");
            assertFormReleased(ctx.getBean(Probe.class), response.status(), response::header, response.head());
        }
    }

    @Test
    void theFilesAFileUploadListRouteDidNotConsumeAreDeletedAndItsReadAborted() throws Exception {
        try (ApplicationContext ctx = start(gatedIo()); Socket socket = connect(ctx)) {
            Response response = sendWhole(socket, "/parts-release/files");
            assertFormReleased(ctx.getBean(Probe.class), response.status(), response::header, response.head());
        }
    }

    @Test
    void theFilesAFormDataRouteDidNotConsumeAreDeletedAndItsReadAbortedOverHttp2() throws Exception {
        Map<String, Object> properties = new HashMap<>(http2());
        properties.putAll(gatedIo());
        try (ApplicationContext ctx = start(properties)) {
            Flux<ReadBuffer> body = Flux.just(buffer(wholeForm()));
            try (ByteBodyHttpResponse<?> response = exchange(ctx, "/parts-release/form", body)) {
                assertEquals("HTTP_2_0", response.getHeaders().get("X-Version"));
                assertEquals("Fred", response.byteBody().buffer().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
                assertFormReleased(ctx.getBean(Probe.class), response.code(), name -> response.getHeaders().get(name), response.getHeaders().toString());
            }
        }
    }

    private void assertFormReleased(Probe probe, int status, Function<String, String> headers, String description) throws IOException {
        assertEquals(200, status, description);
        // before the response: the first file was moved to its destination, the read of the
        // second one waits for the executor, and the other two are still stored
        assertEquals(PENDING, headers.apply("X-Read"), description);
        assertEquals("2", headers.apply("X-Stored"), description);
        // after the request ended: the read was aborted, and the stored files deleted
        assertEquals(CANCELLED, probe.awaitOutcome("read"), description);
        assertEquals("0", probe.awaitNoFiles(probe.stored()), description);
        assertEquals(List.of("kept.txt"), probe.names(probe.destinations()), description);
        assertEquals(DOC1, Files.readString(probe.destinations().resolve("kept.txt")), description);
    }

    // --- a streamed response made of the parts --------------------------------------------------

    @Test
    void aStreamedResponseReadsEveryPartAndReleasesTheBodyWhenItEnds() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            ChunkedReader in = new ChunkedReader(socket.getInputStream());
            out.write(chunkedHead("/parts-release/stream"));
            // each part is sent once the previous one was answered: the body and the response
            // stream at the same time
            writeChunk(out, field("name", "Fred") + next());
            in.readUntil("\"name\"");
            writeChunk(out, rest(file("d1.txt", DOC1)) + next());
            in.readUntil("\"d1.txt\"");
            assertEquals(PENDING, probe.outcome("walk"));
            writeChunk(out, rest(file("d2.txt", DOC2)) + next());
            in.readUntil("\"d2.txt\"");
            writeChunk(out, rest(file("d3.txt", DOC3)) + end());
            out.write(lastChunk());
            out.flush();
            String body = in.readToEnd();
            assertTrue(in.head().startsWith("HTTP/1.1 200"), in.head());
            assertTrue(body.contains("{\"name\":\"name\",\"value\":\"Fred\"}"), body);
            assertTrue(body.contains("{\"name\":\"d1.txt\",\"value\":\"" + DOC1.length() + "\"}"), body);
            assertTrue(body.contains("{\"name\":\"d2.txt\",\"value\":\"" + DOC2.length() + "\"}"), body);
            assertTrue(body.contains("{\"name\":\"d3.txt\",\"value\":\"" + DOC3.length() + "\"}"), body);
            assertEquals("completed", probe.awaitOutcome("walk"), body);
            assertEquals(List.of("name", "d1.txt", "d2.txt", "d3.txt"), probe.delivered());
            assertEquals(List.of("d1.txt", "d2.txt", "d3.txt"), probe.names(probe.destinations()));
        }
    }

    @Test
    void aClientThatDisconnectsFromAStreamOfPartsAbortsTheWalk() throws Exception {
        try (ApplicationContext ctx = start(Map.of())) {
            Probe probe = ctx.getBean(Probe.class);
            try (Socket socket = connect(ctx)) {
                OutputStream out = socket.getOutputStream();
                ChunkedReader in = new ChunkedReader(socket.getInputStream());
                out.write(chunkedHead("/parts-release/stream"));
                writeChunk(out, field("name", "Fred") + next());
                in.readUntil("\"name\"");
                writeChunk(out, rest(file("d1.txt", DOC1)) + next());
                in.readUntil("\"d1.txt\"");
                // a part of the second file: its transfer is running
                String second = rest(file("d2.txt", DOC2));
                writeChunk(out, second.substring(0, second.length() - DOC2.length() / 2));
                probe.awaitStaging();
                assertEquals(PENDING, probe.outcome("read"));
            }
            // the client disconnected: the stream is cancelled, the walk and the transfer aborted
            assertEquals(CANCELLED, probe.awaitOutcome("walk"));
            assertEquals(CANCELLED, probe.awaitOutcome("read"));
            probe.awaitNoStaging();
            assertEquals(List.of("d1.txt"), probe.names(probe.destinations()));
            assertEquals(List.of("name", "d1.txt", "d2.txt"), probe.delivered());
        }
    }

    @Test
    void aStreamedResponseOfAFormPartsArgumentReadsEveryPartAndReleasesThePartsWhenItEnds() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            ChunkedReader in = new ChunkedReader(socket.getInputStream());
            out.write(chunkedHead("/parts-release/form-parts-stream"));
            writeChunk(out, field("name", "Fred") + next());
            in.readUntil("\"name\"");
            writeChunk(out, rest(file("d1.txt", DOC1)) + next());
            in.readUntil("\"d1.txt\"");
            // the route completed when it returned the stream: the parts are kept until it ends
            assertEquals(PENDING, probe.outcome("walk"));
            writeChunk(out, rest(file("d2.txt", DOC2)) + end());
            out.write(lastChunk());
            out.flush();
            String body = in.readToEnd();
            assertTrue(in.head().startsWith("HTTP/1.1 200"), in.head());
            assertTrue(body.contains("{\"name\":\"d2.txt\",\"value\":\"" + DOC2.length() + "\"}"), body);
            assertEquals("completed", probe.awaitOutcome("walk"), body);
            assertEquals(List.of("name", "d1.txt", "d2.txt"), probe.delivered());
            assertEquals(List.of("d1.txt", "d2.txt"), probe.names(probe.destinations()));
        }
    }

    @Test
    void thePartBeingReadWhenAStreamedResponseOfAFormPartsArgumentEndsIsAborted() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            ChunkedReader in = new ChunkedReader(socket.getInputStream());
            out.write(chunkedHead("/parts-release/form-parts-stream-first"));
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            int withheld = first.length() - DOC1.length() + 16 * 1024;
            writeChunk(out, first.substring(0, withheld));
            // the stream ends once the file is being written: the parts are released when it
            // ended, before its end was written
            String body = in.readToEnd();
            assertTrue(in.head().startsWith("HTTP/1.1 200"), in.head());
            assertTrue(body.contains("\"d1.txt\""), body);
            assertEquals(CANCELLED, probe.outcome("walk"), body);
            assertEquals(CANCELLED, probe.outcome("read"), body);
            assertEquals("0", probe.staging(), body);
            assertEquals(List.of("name", "d1.txt"), probe.delivered());
        }
    }

    @Test
    void aClientThatDisconnectsFromAStreamOfAFormPartsArgumentAbortsTheWalk() throws Exception {
        try (ApplicationContext ctx = start(Map.of())) {
            Probe probe = ctx.getBean(Probe.class);
            try (Socket socket = connect(ctx)) {
                OutputStream out = socket.getOutputStream();
                ChunkedReader in = new ChunkedReader(socket.getInputStream());
                out.write(chunkedHead("/parts-release/form-parts-stream"));
                writeChunk(out, field("name", "Fred") + next());
                in.readUntil("\"name\"");
                String second = rest(file("d1.txt", DOC1));
                writeChunk(out, second.substring(0, second.length() - DOC1.length() / 2));
                probe.awaitStaging();
                assertEquals(PENDING, probe.outcome("read"));
            }
            // the client disconnected: the stream is cancelled, the walk and the transfer aborted
            assertEquals(CANCELLED, probe.awaitOutcome("walk"));
            assertEquals(CANCELLED, probe.awaitOutcome("read"));
            probe.awaitNoStaging();
            assertEquals(List.of("name", "d1.txt"), probe.delivered());
        }
    }

    // --- filters --------------------------------------------------------------------------------

    @Test
    void aFilterReadsThePartsOfACopyAndTheRouteGetsTheWholeForm() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Response response = sendWhole(socket, "/parts-release/filter/copy-all");
            assertEquals(200, response.status(), response.head());
            String expected = "name=4,d1.txt=" + DOC1.length() + ",d2.txt=" + DOC2.length() + ",d3.txt=" + DOC3.length();
            // the filter read every part of its copy, and the route every file of the form
            assertTrue(response.head().endsWith("filter[" + expected + "] route[Fred " + expected.substring("name=4,".length()) + "] completed"), response.head());
            assertEquals("0", ctx.getBean(Probe.class).awaitNoFiles(ctx.getBean(Probe.class).stored()));
        }
    }

    @Test
    void theWalkOfACopyAFilterDidNotFinishIsAbortedAndTheRouteGetsTheWholeForm() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Response response = sendWhole(socket, "/parts-release/filter/copy-partial");
            assertEquals(200, response.status(), response.head());
            String expected = "Fred d1.txt=" + DOC1.length() + ",d2.txt=" + DOC2.length() + ",d3.txt=" + DOC3.length();
            // the filter completed with its consumer of the first part pending: its walk was
            // aborted before the route ran, which read the whole form
            assertTrue(response.head().endsWith("filter[name] route[" + expected + "] " + CANCELLED), response.head());
            assertEquals("0", ctx.getBean(Probe.class).awaitNoFiles(ctx.getBean(Probe.class).stored()));
        }
    }

    @Test
    void theTransferOfACopyAFilterDidNotFinishIsAbortedBeforeTheRestOfTheFormArrives() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead("/parts-release/filter/copy-transfer"));
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            int withheld = first.length() - DOC1.length() + 16 * 1024;
            writeChunk(out, first.substring(0, withheld));
            // the filter completed while its copy was writing the first file: the transfer is
            // aborted, and its staging file deleted, before the rest of the form was sent
            assertEquals(CANCELLED, probe.awaitOutcome("read"));
            assertEquals(CANCELLED, probe.awaitOutcome("filter-walk"));
            probe.awaitNoStaging();
            writeChunk(out, first.substring(withheld) + file("d2.txt", DOC2) + file("d3.txt", DOC3) + end());
            out.write(lastChunk());
            out.flush();
            Response response = Response.read(socket.getInputStream());
            assertEquals(200, response.status(), response.head());
            String expected = "Fred d1.txt=" + DOC1.length() + ",d2.txt=" + DOC2.length() + ",d3.txt=" + DOC3.length();
            // the route read the whole form
            assertTrue(response.head().endsWith("filter[name,d1.txt] route[" + expected + "] " + CANCELLED), response.head());
            assertEquals(List.of(), probe.names(probe.destinations()));
            assertEquals("0", probe.awaitNoFiles(probe.stored()));
        }
    }

    @Test
    void theFormPartsOfAFilterAreReleasedWhenTheFilterCompleted() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead("/parts-release/filter-parts/walk"));
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            int withheld = first.length() - DOC1.length() + 16 * 1024;
            writeChunk(out, first.substring(0, withheld));
            Response response = Response.read(socket.getInputStream());
            assertEquals(200, response.status(), response.head());
            // the filter completed while its parts were writing the first file: the transfer
            // was aborted, its staging file deleted, and the walk ended, before the route ran
            assertTrue(response.head().endsWith("filter[name,d1.txt] walk=" + CANCELLED + " read=" + CANCELLED + " staging=0"), response.head());
            writeChunk(out, first.substring(withheld) + file("d2.txt", DOC2) + end());
            out.write(lastChunk());
            out.flush();
            assertEquals("0", probe.files(probe.destinations()));
        }
    }

    @Test
    void theFormPartsOfAFilterConsumeTheBody() throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            Response response = sendWhole(socket, "/parts-release/filter-parts/parts");
            // the filter read a part: the FormParts of the route cannot read the form
            assertEquals(500, response.status(), response.head());
            Probe probe = ctx.getBean(Probe.class);
            assertEquals(List.of("name"), probe.delivered());
            assertEquals("completed", probe.awaitOutcome("filter-walk"));
            assertTrue(response.head().contains("The body of the request was already read with the FormParts argument"), response.head());
            assertEquals("0", probe.awaitNoFiles(probe.stored()));
        }
    }

    // --- a route that fails ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"fails", "fails-mono"})
    void thePartBeingReadWhenTheRouteFailsIsAbortedBeforeTheErrorResponse(String route) throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead("/parts-release/" + route));
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            writeChunk(out, first.substring(0, first.length() - DOC1.length() / 2));
            Response response = Response.read(socket.getInputStream());
            assertEquals(500, response.status(), response.head());
            assertEquals(CANCELLED, response.header("X-Read"), response.head());
            assertEquals(CANCELLED, response.header("X-Walk"), response.head());
            assertEquals("0", response.header("X-Staging"), response.head());
            assertEquals("name,d1.txt", response.header("X-Delivered"), response.head());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"form-parts-fails", "form-parts-throws"})
    void thePartBeingReadOfAFormPartsArgumentWhenTheRouteFailsIsAbortedBeforeTheErrorResponse(String route) throws Exception {
        try (ApplicationContext ctx = start(Map.of()); Socket socket = connect(ctx)) {
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead("/parts-release/" + route));
            String first = field("name", "Fred") + file("d1.txt", DOC1);
            writeChunk(out, first.substring(0, first.length() - DOC1.length() / 2));
            Response response = Response.read(socket.getInputStream());
            assertEquals(500, response.status(), response.head());
            assertEquals(CANCELLED, response.header("X-Read"), response.head());
            assertEquals(CANCELLED, response.header("X-Walk"), response.head());
            assertEquals("0", response.header("X-Staging"), response.head());
            assertEquals("name,d1.txt", response.header("X-Delivered"), response.head());
        }
    }

    // --- the requests ---------------------------------------------------------------------------

    /**
     * The bodies sent by the HTTP/2 client: heap buffers.
     */
    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private static ReadBuffer buffer(String text) {
        return BODIES.readBufferFactory().copyOf(text, StandardCharsets.UTF_8);
    }

    /**
     * Send a buffer of the body, or release it if the client no longer reads the body.
     */
    private static void emit(Sinks.Many<ReadBuffer> body, ReadBuffer buffer) {
        if (body.tryEmitNext(buffer).isFailure()) {
            buffer.close();
        }
    }

    private ApplicationContext start(Map<String, Object> extra) throws IOException {
        Path stored = Files.createDirectories(directory.resolve("stored"));
        Path destinations = Files.createDirectories(directory.resolve("destinations"));
        Map<String, Object> properties = new HashMap<>(extra);
        properties.put("spec.name", SPEC_NAME);
        // the files of a FormData larger than 1KB are stored on disk
        properties.put("micronaut.server.multipart.mixed", true);
        properties.put("micronaut.server.multipart.threshold", 1024);
        properties.put("micronaut.server.multipart.location", stored.toString());
        ApplicationContext ctx = ApplicationContext.run(properties);
        Probe probe = ctx.getBean(Probe.class);
        probe.stored = stored;
        probe.destinations = destinations;
        ctx.getBean(EmbeddedServer.class).start();
        return ctx;
    }

    private static Map<String, Object> http2() {
        return Map.of(
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false,
            "micronaut.http.client.plaintext-mode", "h2c_prior_knowledge");
    }

    /**
     * One thread for the disk work of the form, which a route can hold, see
     * {@link Probe#holdIo}.
     */
    private static Map<String, Object> gatedIo() {
        return Map.of(
            "micronaut.executors.blocking.type", "fixed",
            "micronaut.executors.blocking.number-of-threads", 1);
    }

    private static ByteBodyHttpResponse<?> exchange(ApplicationContext ctx, String path, Publisher<ReadBuffer> body) throws Exception {
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
        RawHttpClient client = ctx.getBean(RawHttpClient.class);
        HttpRequest<?> request = HttpRequest.POST(server.getURI().resolve(path), null).contentType(CONTENT_TYPE);
        return (ByteBodyHttpResponse<?>) Mono.from(client.exchange(request, BODIES.adapt(body), null))
            .toFuture()
            .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private static Socket connect(ApplicationContext ctx) throws IOException {
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
        Socket socket = new Socket(server.getHost(), server.getPort());
        socket.setSoTimeout((int) TIMEOUT.multipliedBy(3).toMillis());
        return socket;
    }

    private static Response sendWhole(Socket socket, String path) throws IOException {
        OutputStream out = socket.getOutputStream();
        byte[] body = wholeForm().getBytes(StandardCharsets.UTF_8);
        out.write(("POST " + path + " HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Content-Type: " + CONTENT_TYPE + "\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
        return Response.read(socket.getInputStream());
    }

    private static String wholeForm() {
        return field("name", "Fred") + file("d1.txt", DOC1) + file("d2.txt", DOC2) + file("d3.txt", DOC3) + end();
    }

    private static byte[] chunkedHead(String path) {
        return ("POST " + path + " HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Content-Type: " + CONTENT_TYPE + "\r\n"
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

    private static byte[] lastChunk() {
        return "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static String field(String name, String value) {
        return "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
            + "\r\n"
            + value + "\r\n";
    }

    private static String file(String fileName, String content) {
        return "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"docs\"; filename=\"" + fileName + "\"\r\n"
            + "Content-Type: text/plain\r\n"
            + "\r\n"
            + content + "\r\n";
    }

    /**
     * The delimiter of the next part, which ends the part before it: a part sent with it can be
     * read whole. The next part is then sent without it, see {@link #rest}.
     */
    private static String next() {
        return "--" + BOUNDARY;
    }

    /**
     * @param part A part, or the end of the form
     * @return The part without its delimiter, which was sent with the part before it
     */
    private static String rest(String part) {
        return part.substring(next().length());
    }

    private static String end() {
        return "--" + BOUNDARY + "--\r\n";
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

        String readUntil(String text) throws IOException {
            head();
            while (!body.toString(StandardCharsets.UTF_8).contains(text)) {
                if (!readChunk()) {
                    throw new IllegalStateException("The response ended before " + text + ": " + head + body.toString(StandardCharsets.UTF_8));
                }
            }
            return body.toString(StandardCharsets.UTF_8);
        }

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

    // --- the application ------------------------------------------------------------------------

    /**
     * Records the outcome of the reads, and the parts delivered to the consumers.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Probe {
        private final Map<String, String> outcomes = new ConcurrentHashMap<>();
        private final List<String> delivered = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Void> io = new CompletableFuture<>();
        private final ExecutorService executor;
        volatile @Nullable String field;
        volatile @Nullable Path stored;
        volatile @Nullable Path destinations;

        /**
         * @param executor Completes the asynchronous work of the routes, and does the disk work of
         *                 the forms: a thread of the server, in the scope of the leak detector
         */
        Probe(@Named(TaskExecutors.BLOCKING) ExecutorService executor) {
            this.executor = executor;
        }

        <T> CompletionStage<T> async(Supplier<T> supplier) {
            return CompletableFuture.supplyAsync(supplier, executor);
        }

        <T> CompletionStage<T> track(String name, CompletionStage<T> read) {
            outcomes.put(name, PENDING);
            read.whenComplete((ignored, error) -> {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                outcomes.put(name, cause == null ? "completed" : cause.getClass().getSimpleName());
            });
            return read;
        }

        String outcome(String name) {
            return outcomes.getOrDefault(name, "none");
        }

        void deliver(String name) {
            delivered.add(name);
        }

        List<String> delivered() {
            return List.copyOf(delivered);
        }

        /**
         * Hold the one thread of the disk work until the request ends: the work queued after this
         * runs once the request released the form.
         *
         * @param request The request
         */
        void holdIo(HttpRequest<?> request) {
            executor.execute(() -> {
                try {
                    io.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("The request did not end", e);
                }
            });
            // after the resources of the form, which were added when it was read
            ((LifecycleHttpRequest<?>) request).addDisposalResource(() -> io.complete(null));
        }

        Path stored() {
            return Objects.requireNonNull(stored);
        }

        Path destinations() {
            return Objects.requireNonNull(destinations);
        }

        Path destination(String fileName) {
            return destinations().resolve(fileName);
        }

        String files(Path dir) {
            return String.valueOf(names(dir).size());
        }

        List<String> names(Path dir) {
            try (Stream<Path> list = Files.list(dir)) {
                return list.map(p -> p.getFileName().toString()).sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * @return The number of staging files of the transfers to the destinations
         */
        String staging() {
            return String.valueOf(names(destinations()).stream().filter(name -> name.startsWith(".upload-")).count());
        }

        String awaitOutcome(String name) {
            return await(() -> !PENDING.equals(outcome(name)) && !"none".equals(outcome(name)), () -> outcome(name));
        }

        String awaitNoFiles(Path dir) {
            return await(() -> "0".equals(files(dir)), () -> files(dir));
        }

        void awaitStaging() {
            String count = await(() -> !"0".equals(staging()), this::staging);
            if ("0".equals(count)) {
                throw new IllegalStateException("The staging file of the transfer was not created");
            }
        }

        void awaitNoStaging() {
            assertEquals("0", await(() -> "0".equals(staging()), this::staging));
        }

        private static String await(Supplier<Boolean> condition, Supplier<String> value) {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (!condition.get() && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return value.get();
        }
    }

    /**
     * A consumer of a part that never completes.
     */
    private static <T> CompletionStage<T> never() {
        return new CompletableFuture<>();
    }

    record PartValue(String name, String value) {
    }

    /**
     * The fields and the files of a form, with the sizes of their contents, which are checked.
     */
    private static CompletionStage<String> describe(FormData form, List<FileUpload> files) {
        CompletionStage<StringBuilder> result = CompletableFuture.completedFuture(new StringBuilder(form.get("name", String.class)).append(' '));
        for (FileUpload file : files) {
            result = result.thenCompose(builder -> file.text(1 << 20).thenApply(text -> {
                check(file.fileName(), text);
                return builder.append(file.fileName()).append('=').append(text.length()).append(',');
            }));
        }
        return result.thenApply(builder -> builder.substring(0, builder.length() - 1));
    }

    private static void check(String fileName, String text) {
        String expected = switch (fileName) {
            case "d1.txt" -> DOC1;
            case "d2.txt" -> DOC2;
            case "d3.txt" -> DOC3;
            default -> throw new IllegalStateException("Unexpected file " + fileName);
        };
        if (!expected.equals(text)) {
            throw new IllegalStateException("The content of " + fileName + " changed");
        }
    }

    @Controller("/parts-release")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PartsController {
        private final Probe probe;

        PartsController(Probe probe) {
            this.probe = probe;
        }

        @Post(uri = "/partial", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> partial(AsyncRequestBody body) {
            startWalk(body.parts());
            // answers once the file is being written
            return probe.async(() -> {
                probe.awaitStaging();
                return HttpResponse.ok("started");
            });
        }

        @Post(uri = "/form-parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> formParts(FormParts parts) {
            startWalk(parts);
            return probe.async(() -> {
                probe.awaitStaging();
                return HttpResponse.ok("started");
            });
        }

        @Post(uri = "/form-parts-fails", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> formPartsFails(FormParts parts) {
            startWalk(parts);
            // fails once the file is being written
            return probe.async(() -> {
                probe.awaitStaging();
                throw new IllegalStateException("The route failed while a part was being read");
            });
        }

        @Post(uri = "/form-parts-throws", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        HttpResponse<String> formPartsThrows(FormParts parts) {
            startWalk(parts);
            // throws once the file is being written
            probe.awaitStaging();
            throw new IllegalStateException("The route threw while a part was being read");
        }

        @Post(uri = "/form-part", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> formPart(String name, FormPart docs) {
            probe.track("read", docs.transferTo(probe.destination(Objects.requireNonNull(docs.fileName()))));
            // answers once the file is being written, without waiting for the transfer
            return probe.async(() -> {
                probe.awaitStaging();
                return HttpResponse.ok(name + " " + docs.fileName());
            });
        }

        @Post(uri = "/form-part-unread", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String formPartUnread(String name, FormPart docs) {
            return name + " " + docs.fileName();
        }

        @Post(uri = "/form-parts-stream", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<PartValue> formPartsStream(FormParts parts) {
            return streamParts(parts);
        }

        @Post(uri = "/form-parts-stream-first", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<PartValue> formPartsStreamFirst(FormParts parts) {
            // one item for each part that is delivered, and the stream ends once the first file
            // is being written, whose consumer never completes
            return Flux.create(sink -> {
                startWalk(parts, name -> sink.next(new PartValue(name, "")));
                probe.async(() -> {
                    probe.awaitStaging();
                    return null;
                }).whenComplete((ignored, error) -> sink.complete());
            });
        }

        @Post(uri = "/fails", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> fails(AsyncRequestBody body) {
            startWalk(body.parts());
            // fails once the file is being written
            return probe.async(() -> {
                probe.awaitStaging();
                throw new IllegalStateException("The route failed while a part was being read");
            });
        }

        @Post(uri = "/fails-mono", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<HttpResponse<String>> failsMono(AsyncRequestBody body) {
            startWalk(body.parts());
            // a reactive route that fails once the file is being written
            return Mono.fromCompletionStage(() -> probe.async(() -> {
                probe.awaitStaging();
                throw new IllegalStateException("The route failed while a part was being read");
            }));
        }

        /**
         * Read the text fields whole, and start the transfer of the first file, whose consumer
         * never completes.
         */
        private void startWalk(FormParts parts) {
            startWalk(parts, name -> {
            });
        }

        /**
         * Like {@link #startWalk(FormParts)}, and tell each part that is delivered.
         */
        private void startWalk(FormParts parts, Consumer<String> delivered) {
            probe.track("walk", parts.forEach(part -> {
                String name = part.isFile() ? Objects.requireNonNull(part.fileName()) : part.name();
                probe.deliver(name);
                delivered.accept(name);
                if (!part.isFile()) {
                    return part.text().thenAccept(text -> probe.field = text);
                }
                probe.track("read", part.transferTo(probe.destination(Objects.requireNonNull(part.fileName()))));
                return never();
            }));
        }

        @Post(uri = "/skip", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> skip(AsyncRequestBody body) {
            // the field only: the files are not read
            CompletionStage<Boolean> found = probe.track("walk", body.parts().part("name", part -> {
                probe.deliver(part.name());
                return part.text().thenAccept(text -> probe.field = text);
            }));
            return found.thenApply(f -> f + " " + probe.field);
        }

        @Post(uri = "/form", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> form(FormData form, HttpRequest<?> request) {
            return consumeOne(form.getFiles("docs"), request).thenApply(ignored -> form.get("name", String.class));
        }

        @Post(uri = "/files", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> files(List<FileUpload> docs, String name, HttpRequest<?> request) {
            return consumeOne(docs, request).thenApply(ignored -> name);
        }

        /**
         * Transfer the first file, start reading the second one without waiting for it, and
         * ignore the third one.
         */
        private CompletionStage<Void> consumeOne(List<FileUpload> docs, HttpRequest<?> request) {
            if (docs.size() != 3) {
                throw new IllegalStateException("Expected 3 files: " + docs);
            }
            return docs.get(0).transferTo(probe.destination("kept.txt")).thenRun(() -> {
                // the read of the second file runs once the request ended
                probe.holdIo(request);
                probe.track("read", docs.get(1).bytes(1 << 20));
            });
        }

        @Post(uri = "/stream", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<PartValue> stream(AsyncRequestBody body) {
            return streamParts(body.parts());
        }

        private Publisher<PartValue> streamParts(FormParts parts) {
            // one item for each part, as it is read
            return Flux.create(sink -> probe.track("walk", parts.forEach(part -> {
                String name = part.isFile() ? Objects.requireNonNull(part.fileName()) : part.name();
                probe.deliver(name);
                CompletionStage<String> value;
                if (part.isFile()) {
                    Path destination = probe.destination(name);
                    value = probe.track("read", part.transferTo(destination)).thenApply(ignored -> {
                        try {
                            return String.valueOf(Files.size(destination));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } else {
                    value = part.text();
                }
                return value.thenAccept(v -> sink.next(new PartValue(name, v)));
            })).whenComplete((done, error) -> {
                if (error != null) {
                    sink.error(error);
                } else {
                    sink.complete();
                }
            }));
        }

        @Post(uri = "/filter/{read}", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> filtered(String read, FormData form) {
            // the whole form, after the filter read the parts of its copy
            return describe(form, form.getFiles("docs")).thenApply(route ->
                "filter[" + String.join(",", probe.delivered()) + "] route[" + route + "] " + probe.outcome("filter-walk"));
        }
    }

    @ServerFilter("/parts-release/filter")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CopyFilter {
        private final Probe probe;

        CopyFilter(Probe probe) {
            this.probe = probe;
        }

        @RequestFilter("/copy-all")
        CompletionStage<@Nullable HttpResponse<?>> copyAll(AsyncRequestBody body) {
            // every part of the copy, with the size of its content
            return probe.track("filter-walk", body.copy().parts().forEach(part -> part.text(1 << 20).thenAccept(text -> {
                if (part.isFile()) {
                    check(Objects.requireNonNull(part.fileName()), text);
                    probe.deliver(part.fileName() + "=" + text.length());
                } else {
                    probe.deliver(part.name() + "=" + text.length());
                }
            }))).thenApply(done -> null);
        }

        @RequestFilter("/copy-partial")
        CompletionStage<@Nullable HttpResponse<?>> copyPartial(AsyncRequestBody body) {
            CompletableFuture<@Nullable HttpResponse<?>> first = new CompletableFuture<>();
            // the filter completes once the first part was delivered, whose consumer never does
            probe.track("filter-walk", body.copy().parts().forEach(part -> {
                probe.deliver(part.name());
                first.complete(null);
                return never();
            })).whenComplete((done, error) -> {
                if (error != null) {
                    first.completeExceptionally(error);
                }
            });
            return first;
        }

        @RequestFilter("/copy-transfer")
        CompletionStage<@Nullable HttpResponse<?>> copyTransfer(AsyncRequestBody body) {
            // the text of the field, and the transfer of the first file, whose consumer never
            // completes
            probe.track("filter-walk", body.copy().parts().forEach(part -> {
                if (!part.isFile()) {
                    probe.deliver(part.name());
                    return part.text();
                }
                String fileName = Objects.requireNonNull(part.fileName());
                probe.deliver(fileName);
                probe.track("read", part.transferTo(probe.destination(fileName)));
                return never();
            }));
            // the filter completes once the file is being written
            return probe.async(() -> {
                probe.awaitStaging();
                return null;
            });
        }
    }

    @Controller("/parts-release/filter-parts")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FilterPartsController {
        private final Probe probe;

        FilterPartsController(Probe probe) {
            this.probe = probe;
        }

        @Post(uri = "/walk", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String walk() {
            // the route does not read the body: what the filter left open was released before
            return "filter[" + String.join(",", probe.delivered()) + "] walk=" + probe.outcome("filter-walk")
                + " read=" + probe.outcome("read") + " staging=" + probe.staging();
        }

        @Post(uri = "/parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String parts(FormParts parts) {
            return "not reached";
        }

        @Error(IllegalStateException.class)
        HttpResponse<String> failed(IllegalStateException e) {
            return HttpResponse.<String>serverError().body(e.getMessage());
        }
    }

    @ServerFilter("/parts-release/filter-parts")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PartsFilter {
        private final Probe probe;

        PartsFilter(Probe probe) {
            this.probe = probe;
        }

        @RequestFilter("/walk")
        CompletionStage<@Nullable HttpResponse<?>> walk(FormParts parts) {
            // the text of the field, and the transfer of the first file, whose consumer never
            // completes
            probe.track("filter-walk", parts.forEach(part -> {
                if (!part.isFile()) {
                    probe.deliver(part.name());
                    return part.text();
                }
                String fileName = Objects.requireNonNull(part.fileName());
                probe.deliver(fileName);
                probe.track("read", part.transferTo(probe.destination(fileName)));
                return never();
            }));
            // the filter completes once the file is being written
            return probe.async(() -> {
                probe.awaitStaging();
                return null;
            });
        }

        @RequestFilter("/parts")
        CompletionStage<@Nullable HttpResponse<?>> parts(FormParts parts) {
            // the field only: the rest of the form is discarded when the filter completed
            return probe.track("filter-walk", parts.part("name", part -> {
                probe.deliver(part.name());
                return part.text();
            })).thenApply(found -> null);
        }
    }

    @ServerFilter("/parts-release/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OutcomeFilter implements Ordered {
        private final Probe probe;

        OutcomeFilter(Probe probe) {
            this.probe = probe;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @ResponseFilter
        void outcome(HttpRequest<?> request, MutableHttpResponse<?> response) {
            // after the route completed, before the response is written
            response.header("X-Version", request.getHttpVersion().name());
            response.header("X-Walk", probe.outcome("walk"));
            response.header("X-Read", probe.outcome("read"));
            response.header("X-Staging", probe.staging());
            response.header("X-Stored", probe.files(probe.stored()));
            response.header("X-Delivered", String.join(",", probe.delivered()));
            String field = probe.field;
            if (field != null) {
                response.header("X-Field", field);
            }
        }
    }

}
