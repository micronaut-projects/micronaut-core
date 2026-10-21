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
package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.BodyElements;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code max-content-length} applies to the decompressed bytes of a response read with the
 * {@link AsyncStreamingHttpClient}, like it does for the reactive streaming client.
 */
class AsyncStreamingDecompressedLimitTest {
    private static final long WAIT = 30;

    @Test
    void decompressedEventStreamLargerThanMaxContentLengthFails() throws Exception {
        byte[] compressed = gzip("data:1\n\n", 4 << 20);
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread serverThread = Thread.ofVirtual().start(() -> serve(serverSocket, compressed));
            try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.http.client.max-content-length", "1048576"));
                 StreamingHttpClient client = ctx.createBean(StreamingHttpClient.class, URI.create("http://127.0.0.1:" + serverSocket.getLocalPort()))) {
                AsyncStreamingHttpClient async = client.toAsyncStreaming();
                ExecutionException e = assertThrows(ExecutionException.class, () -> readFirst(async));
                assertTrue(exceedsLimit(e), () -> "unexpected failure: " + e.getCause());
            } finally {
                serverThread.interrupt();
            }
        }
    }

    private static void readFirst(AsyncStreamingHttpClient async) throws Exception {
        BodyElements<?> elements = async.eventStream(HttpRequest.GET("/"), String.class).toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        try (elements) {
            elements.next().toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        }
    }

    private static boolean exceedsLimit(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("exceeds the maximum allowed bufferable length")) {
                return true;
            }
        }
        return false;
    }

    private static byte[] gzip(String element, int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = element.getBytes(StandardCharsets.UTF_8);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            for (int written = 0; written < size; written += bytes.length) {
                gzip.write(bytes);
            }
        }
        return out.toByteArray();
    }

    private static void serve(ServerSocket serverSocket, byte[] compressed) {
        try (Socket socket = serverSocket.accept()) {
            InputStream in = socket.getInputStream();
            readRequestHead(in);
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Encoding: gzip\r\nContent-Length: "
                + compressed.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(compressed);
            out.flush();
            // keep the connection open until the client closes it
            while (in.read() >= 0) {
                // discard
            }
        } catch (IOException ignored) {
            // the client closed the connection
        }
    }

    private static void readRequestHead(InputStream in) throws IOException {
        int newlines = 0;
        while (newlines < 2) {
            int b = in.read();
            if (b < 0) {
                return;
            }
            if (b == '\n') {
                newlines++;
            } else if (b != '\r') {
                newlines = 0;
            }
        }
    }
}
