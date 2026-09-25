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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.FormRequestHandler;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Large uploads to handler routes that the client abandons part way: every reader of the body
 * ends, the staging and temporary files are removed, nothing leaks, and the server keeps serving.
 */
class HandlerRouteCancelledUploadTest {
    private static final String SPEC_NAME = "HandlerRouteCancelledUploadTest";
    private static final String BOUNDARY = "cancelled-upload-boundary";
    private static final int FILE_SIZE = 4 * 1024 * 1024;
    private static final int SENT = 256 * 1024;
    private static final long TIMEOUT_MILLIS = 10_000;

    @TempDir
    static Path destination;
    @TempDir
    static Path multipartLocation;

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static Outcomes outcomes;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "spec.destination", destination.toString(),
            "micronaut.server.port", -1,
            "micronaut.server.max-request-size", "32MB",
            "micronaut.server.max-request-buffer-size", "32MB",
            "micronaut.server.multipart.location", multipartLocation.toString(),
            "micronaut.server.multipart.disk", true,
            "micronaut.server.multipart.max-file-size", "32MB"
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
        outcomes = ctx.getBean(Outcomes.class);
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void streamedPartsWrittenToFiles() throws Exception {
        abandonMultipart("/cancel/parts", () -> !stagingFiles().isEmpty());
        assertHandlerFailed("parts");
        assertClean();
    }

    @Test
    void collectedForm() throws Exception {
        abandonMultipart("/cancel/form", () -> !multipartFiles().isEmpty());
        assertHandlerFailed("form");
        assertClean();
    }

    @Test
    void bodyWrittenToAFile() throws Exception {
        abandonOctets("/cancel/transfer", () -> !stagingFiles().isEmpty());
        assertHandlerFailed("transfer");
        assertClean();
    }

    @Test
    void bodyReadIntoMemory() throws Exception {
        abandonOctets("/cancel/bytes", () -> outcomes.started.containsKey("bytes"));
        assertHandlerFailed("bytes");
        assertClean();
    }

    @Test
    void bodyReadAsText() throws Exception {
        abandonOctets("/cancel/text", () -> outcomes.started.containsKey("text"));
        assertHandlerFailed("text");
        assertClean();
    }

    @Test
    void formOfASynchronousFormHandler() throws Exception {
        abandonMultipart("/cancel/sync-form", () -> !multipartFiles().isEmpty());
        assertClean();
        assertFalse(outcomes.started.containsKey("sync-form"), "the handler is not called with a form that never arrived");
    }

    @Test
    void formTheHandlerDidNotWaitFor() throws Exception {
        byte[] head = multipartHead();
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(requestHead("/cancel/form-unawaited", "multipart/form-data; boundary=" + BOUNDARY, head.length + FILE_SIZE + tail.length));
            out.write(head);
            out.write(new byte[SENT]);
            out.flush();
            String response = readHead(socket.getInputStream());
            assertTrue(response.startsWith("HTTP/1.1 200 "), response);
            // the client keeps sending: the form is not read after the handler completed
            try {
                for (int sent = SENT; sent < FILE_SIZE; sent += SENT) {
                    out.write(new byte[SENT]);
                }
                out.write(tail);
                out.flush();
            } catch (IOException e) {
                // the server may close the connection instead of reading the rest
            }
        }
        CompletionStage<Object> form = outcomes.outcome("form-unawaited");
        Object outcome = form.toCompletableFuture().get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertInstanceOf(CancellationException.class, outcome, "the collection stopped when the handler completed");
        assertClean();
    }

    private void abandonMultipart(String path, BooleanSupplier inProgress) throws Exception {
        byte[] head = multipartHead();
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        abandon(path, "multipart/form-data; boundary=" + BOUNDARY, head.length + FILE_SIZE + tail.length, head, inProgress);
    }

    private void abandonOctets(String path, BooleanSupplier inProgress) throws Exception {
        abandon(path, MediaType.APPLICATION_OCTET_STREAM, FILE_SIZE, new byte[0], inProgress);
    }

    /**
     * Send the head of a large request and a part of its body, wait until the server is reading
     * it, and reset the connection.
     */
    private void abandon(String path, String contentType, long contentLength, byte[] head, BooleanSupplier inProgress) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(requestHead(path, contentType, contentLength));
            out.write(head);
            out.write(new byte[SENT]);
            out.flush();
            await("the server reads the body of " + path, inProgress);
            // abruptly: a reset, not a graceful close
            socket.setSoLinger(true, 0);
        }
    }

    private static byte[] requestHead(String path, String contentType, long contentLength) {
        return ("POST " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Content-Length: " + contentLength + "\r\n"
            + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] multipartHead() {
        return ("--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"title\"\r\n"
            + "\r\n"
            + "abandoned\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"large.bin\"\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static void assertHandlerFailed(String route) throws Exception {
        Object outcome = outcomes.outcome(route).toCompletableFuture().get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertTrue(outcome instanceof Throwable, "the stage of the handler failed: " + outcome);
    }

    /**
     * No staging file, temporary file or published file is left, and the server answers the
     * next request.
     */
    private static void assertClean() throws Exception {
        await("the staging files are removed", () -> stagingFiles().isEmpty());
        await("the temporary files of the uploads are removed", () -> multipartFiles().isEmpty());
        assertEquals(List.of(), files(destination), "nothing was published");
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /cancel/ping HTTP/1.1\r\nHost: " + server.getHost() + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = readHead(socket.getInputStream());
            assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        }
    }

    private static List<Path> stagingFiles() {
        return files(destination).stream().filter(p -> p.getFileName().toString().startsWith(".upload-")).toList();
    }

    private static List<Path> multipartFiles() {
        return files(multipartLocation);
    }

    private static List<Path> files(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting until " + what);
            }
            Thread.sleep(20);
        }
    }

    /**
     * Read a response head, up to the empty line.
     */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            head.write(b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        return head.toString(StandardCharsets.US_ASCII);
    }

    /**
     * What the handlers did: which ones started, and how their stages completed.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class Outcomes {
        final Map<String, Boolean> started = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<Object>> outcomes = new ConcurrentHashMap<>();

        CompletionStage<Object> outcome(String route) {
            return outcomes.computeIfAbsent(route, r -> new CompletableFuture<>());
        }

        <T> CompletionStage<HttpResponse<?>> record(String route, CompletionStage<T> stage) {
            started.put(route, Boolean.TRUE);
            return stage.handle((value, error) -> {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                outcomes.computeIfAbsent(route, r -> new CompletableFuture<>()).complete(cause != null ? cause : "completed");
                return HttpResponse.ok();
            });
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        HttpRoutes cancelledUploadRoutes(Outcomes outcomes, @Value("${spec.destination}") String destination) {
            Path directory = Path.of(destination);
            return routes -> {
                routes.asyncPOST("/cancel/parts", (request, pathVariables, body) -> outcomes.record("parts", body.parts()
                    .forEach(part -> part.isFile()
                        ? part.file().transferTo(directory.resolve(UUID.randomUUID() + ".bin"))
                        : part.text()))).consumesAll();
                routes.asyncPOST("/cancel/form", (request, pathVariables, body) ->
                    outcomes.record("form", body.form())).consumesAll();
                routes.asyncPOST("/cancel/form-unawaited", (request, pathVariables, body) -> {
                    outcomes.record("form-unawaited", body.form());
                    return CompletableFuture.completedFuture(HttpResponse.ok());
                }).consumesAll();
                routes.asyncPOST("/cancel/transfer", (request, pathVariables, body) ->
                    outcomes.record("transfer", body.transferTo(directory.resolve(UUID.randomUUID() + ".bin")))).consumesAll();
                routes.asyncPOST("/cancel/bytes", (request, pathVariables, body) ->
                    outcomes.record("bytes", body.bytes(32 * 1024 * 1024))).consumesAll();
                routes.asyncPOST("/cancel/text", (request, pathVariables, body) ->
                    outcomes.record("text", body.text())).consumesAll();
                routes.POST("/cancel/sync-form", (FormRequestHandler) (request, pathVariables, form) -> {
                    outcomes.started.put("sync-form", Boolean.TRUE);
                    return HttpResponse.ok();
                }).consumesAll();
                routes.asyncGET("/cancel/ping", (request, pathVariables) ->
                    CompletableFuture.completedFuture(HttpResponse.ok("pong")));
            };
        }
    }
}
