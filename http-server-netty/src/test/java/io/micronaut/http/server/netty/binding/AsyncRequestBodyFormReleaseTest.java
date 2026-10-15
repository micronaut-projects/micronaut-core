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
import io.micronaut.http.form.FormData;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release of the form an {@link AsyncRequestBody#form()} collects:
 *
 * <ul>
 *     <li>a controller or a request filter that completes while the form is still arriving: the
 *     collection is cancelled when it completed, before the response, and the files stored so far
 *     are deleted when the request ends; the connection serves the next request;</li>
 *     <li>a request that ends while its form is being collected, e.g. the client disconnected:
 *     the stage of the form completes exceptionally, and its callbacks run.</li>
 * </ul>
 *
 * <p>The requests are written on a socket, chunked, so that a part can be withheld. The buffers
 * are checked by the leak presence detector of the tests.</p>
 */
class AsyncRequestBodyFormReleaseTest {
    private static final String SPEC_NAME = "AsyncRequestBodyFormReleaseTest";
    private static final String PENDING = "pending";
    private static final String COPIED_NAME = "copied-name";
    private static final String COPIED_FORM = "copied-form";
    private static final String CANCELLED = CancellationException.class.getSimpleName();
    private static final String BOUNDARY = "form-release-boundary";
    private static final String CONTENT_TYPE = MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DOC1 = "1".repeat(4 * 1024);
    private static final String DOC2 = "2".repeat(64 * 1024);

    @TempDir
    Path directory;

    @Test
    void theFormOfAControllerIsCancelledWhenItCompletesBeforeTheUploadArrived() throws Exception {
        assertCancelledBeforeTheResponse("/form-release/controller");
    }

    @Test
    void theFormOfAFilterIsCancelledWhenItCompletesBeforeTheUploadArrived() throws Exception {
        assertCancelledBeforeTheResponse("/form-release/filter");
    }

    private void assertCancelledBeforeTheResponse(String path) throws Exception {
        try (ApplicationContext ctx = start(); Socket socket = connect(ctx)) {
            Probe probe = ctx.getBean(Probe.class);
            OutputStream out = socket.getOutputStream();
            out.write(chunkedHead(path));
            // the field and the first file, which is stored, and a part of the second file
            String second = file("d2.txt", DOC2);
            writeChunk(out, field("name", "Fred") + file("d1.txt", DOC1) + second.substring(0, second.length() - DOC2.length() / 2));
            Response response = Response.read(socket.getInputStream());
            assertEquals(200, response.status(), response.head());
            // the handler completed before the form arrived: its collection was cancelled
            // before the response was written
            assertEquals(CANCELLED, response.header("X-Form"), response.head());
            assertEquals("true", response.header("X-Callback"), response.head());

            // the rest of the form arrives after the response: it is discarded, and the
            // connection serves the next request
            writeChunk(out, second.substring(second.length() - DOC2.length() / 2) + end());
            out.write(lastChunk());
            out.flush();
            out.write(chunkedHead("/form-release/whole"));
            writeChunk(out, field("name", "Next") + end());
            out.write(lastChunk());
            out.flush();
            Response next = Response.read(socket.getInputStream());
            assertEquals(200, next.status(), next.head());
            assertTrue(next.head().endsWith("Next"), next.head());
            // no stored file is left
            assertEquals("0", probe.awaitNoFiles());
        }
    }

    @Test
    void theFormCompletesExceptionallyWhenTheRequestEndsWhileItIsCollected() throws Exception {
        try (ApplicationContext ctx = start()) {
            Probe probe = ctx.getBean(Probe.class);
            try (Socket socket = connect(ctx)) {
                OutputStream out = socket.getOutputStream();
                out.write(chunkedHead("/form-release/copy"));
                String second = file("d2.txt", DOC2);
                writeChunk(out, field("name", "Fred") + file("d1.txt", DOC1) + second.substring(0, second.length() - DOC2.length() / 2));
                Response response = Response.read(socket.getInputStream());
                assertEquals(200, response.status(), response.head());
                // the form of a copy is left to the request: it is still being collected
                assertEquals(PENDING, response.header("X-Form"), response.head());
            }
            // the client disconnected: the request ended while its form was being collected, and
            // the form failed
            assertEquals(CANCELLED, probe.awaitOutcome());
            assertEquals("true", probe.awaitCallback());
            assertEquals("0", probe.awaitNoFiles());
        }
    }

    @Test
    void theFilesOfTheFormOfACopyAreStoredOnceAndReleasedWhenTheRouteReadsTheBytes() throws Exception {
        for (String route : List.of("text", "data")) {
            try (ApplicationContext ctx = start(); Socket socket = connect(ctx)) {
                Probe probe = ctx.getBean(Probe.class);
                OutputStream out = socket.getOutputStream();
                String form = field("name", "Fred") + file("d1.txt", DOC1) + end();
                out.write(chunkedHead("/form-release/copied/" + route));
                writeChunk(out, form);
                out.write(lastChunk());
                out.flush();
                Response response = Response.read(socket.getInputStream());
                assertEquals(200, response.status(), response.head());
                // the filter read the form of a copy, and the route the bytes of the body, or
                // the same form: the file was stored once
                assertTrue(response.head().endsWith("text".equals(route) ? "Fred 1 " + form.length() : "Fred 1 same"), response.head());
                // and is released when the request ends
                assertEquals("0", probe.awaitNoFiles());
            }
        }
    }

    // --- the requests ---------------------------------------------------------------------------

    private ApplicationContext start() throws IOException {
        Path stored = Files.createDirectories(directory.resolve("stored"));
        ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            // the files larger than 1KB are stored on disk
            "micronaut.server.multipart.mixed", true,
            "micronaut.server.multipart.threshold", 1024,
            "micronaut.server.multipart.location", stored.toString()));
        ctx.getBean(Probe.class).stored = stored;
        ctx.getBean(EmbeddedServer.class).start();
        return ctx;
    }

    private static Socket connect(ApplicationContext ctx) throws IOException {
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
        Socket socket = new Socket(server.getHost(), server.getPort());
        socket.setSoTimeout((int) TIMEOUT.multipliedBy(3).toMillis());
        return socket;
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

    // --- the application ------------------------------------------------------------------------

    /**
     * Records the outcome of the form, and whether its callback ran.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Probe {
        private final Map<String, String> outcomes = new ConcurrentHashMap<>();
        volatile @Nullable Path stored;

        void track(CompletionStage<FormData> form) {
            outcomes.put("form", PENDING);
            form.whenComplete((data, error) -> {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                if (data != null) {
                    // the files the handler did not consume are released by the request
                    data.close();
                }
                outcomes.put("form", cause == null ? "completed" : cause.getClass().getSimpleName());
                outcomes.put("callback", "true");
            });
        }

        String outcome() {
            return outcomes.getOrDefault("form", "none");
        }

        String callback() {
            return outcomes.getOrDefault("callback", "false");
        }

        String awaitOutcome() {
            return await(() -> !PENDING.equals(outcome()), this::outcome);
        }

        String awaitCallback() {
            return await(() -> "true".equals(callback()), this::callback);
        }

        String files() {
            try (Stream<Path> list = Files.list(Objects.requireNonNull(stored))) {
                return String.valueOf(list.count());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String awaitNoFiles() {
            return await(() -> "0".equals(files()), this::files);
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

    @Controller("/form-release")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormController {
        private final Probe probe;

        FormController(Probe probe) {
            this.probe = probe;
        }

        @Post(uri = "/controller", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String controller(AsyncRequestBody body) {
            // completes without waiting for the form
            probe.track(body.form());
            return "started";
        }

        @Post(uri = "/filter", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String filter() {
            // the filter read the form: the route does not read the body
            return "filtered";
        }

        @Post(uri = "/copy", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String copy(AsyncRequestBody body) {
            // completes without waiting for the form of a copy, which the request releases
            probe.track(body.copy().form());
            return "started";
        }

        @Post(uri = "/copied/text", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> copiedText(HttpRequest<?> request, AsyncRequestBody body) {
            // the filter read the form of a copy: the bytes are left to the route
            return body.text().thenApply(text -> request.getAttribute(COPIED_NAME, String.class).orElse(null) + " " + probe.files() + " " + text.length());
        }

        @Post(uri = "/copied/data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        @SuppressWarnings("ReferenceEquality") // the same form
        String copiedData(HttpRequest<?> request, FormData form) {
            // the form the filter read, which is not decoded a second time
            String same = request.getAttribute(COPIED_FORM).orElse(null) == form ? "same" : "different";
            return form.getString("name") + " " + probe.files() + " " + same;
        }

        @Post(uri = "/whole", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> whole(AsyncRequestBody body) {
            return body.form().thenApply(form -> {
                try (form) {
                    return form.get("name", String.class);
                }
            });
        }
    }

    @ServerFilter("/form-release/filter")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormFilter {
        private final Probe probe;

        FormFilter(Probe probe) {
            this.probe = probe;
        }

        @RequestFilter
        @Nullable HttpResponse<?> filter(AsyncRequestBody body) {
            // completes without waiting for the form
            probe.track(body.form());
            return null;
        }
    }

    @ServerFilter("/form-release/copied/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CopiedFormFilter {

        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> filter(HttpRequest<?> request, AsyncRequestBody body) {
            // waits for the form of a copy, which leaves the body to the route
            return body.copy().form().thenApply(form -> {
                request.setAttribute(COPIED_NAME, form.getString("name"));
                request.setAttribute(COPIED_FORM, form);
                return null;
            });
        }
    }

    @ServerFilter("/form-release/**")
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
            // after the handlers completed, before the response is written
            response.header("X-Form", probe.outcome());
            response.header("X-Callback", probe.callback());
        }
    }
}
