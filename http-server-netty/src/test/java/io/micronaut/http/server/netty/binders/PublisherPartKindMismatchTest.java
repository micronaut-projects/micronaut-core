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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multipart part that carries the name of a {@code Publisher<CompletedFileUpload>} argument but
 * is an attribute rather than a file upload (no {@code filename} parameter) is a malformed request
 * for that route, and is answered with 400 like the single {@code @Part CompletedFileUpload} case,
 * not with 500.
 */
class PublisherPartKindMismatchTest {
    private static final String BOUNDARY = "bnd12345";

    @Controller("/publisher-part-kind")
    @Requires(property = "spec.name", value = "PublisherPartKindMismatchTest")
    static class Ctrl {
        @Post(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA)
        @SingleResult
        Publisher<String> upload(Publisher<CompletedFileUpload> file) {
            return Flux.from(file)
                .map(upload -> {
                    long size = upload.getSize();
                    try { upload.close(); } catch (IOException e) { throw new UncheckedIOException(e); }
                    return size;
                })
                .reduce(0L, Long::sum)
                .map(String::valueOf);
        }
    }

    @Test
    void attributeWithTheBoundNameIsABadRequest() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "PublisherPartKindMismatchTest",
            "micronaut.server.port", -1
        ))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String response = post(server, form(false));
            assertTrue(response.startsWith("HTTP/1.1 400 "), response);
            assertTrue(body(response).contains("was expected to be a file upload, but is missing a file name"), response);
        }
    }

    @Test
    void fileUploadWithTheBoundNameIsStillDelivered() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "PublisherPartKindMismatchTest",
            "micronaut.server.port", -1
        ))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String response = post(server, form(true));
            assertTrue(response.startsWith("HTTP/1.1 200 "), response);
            assertEquals(String.valueOf(2 * 64 * 1024), body(response));
        }
    }

    /**
     * Two 64 KiB parts named {@code file}, with or without a {@code filename} parameter.
     */
    private static byte[] form(boolean fileName) {
        var out = new ByteArrayOutputStream();
        for (int i = 0; i < 2; i++) {
            String head = "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\""
                + (fileName ? "; filename=\"f" + i + ".bin\"" : "") + "\r\n\r\n";
            out.writeBytes(head.getBytes(StandardCharsets.US_ASCII));
            byte[] data = new byte[64 * 1024];
            Arrays.fill(data, (byte) ('a' + i));
            out.writeBytes(data);
            out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        out.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static String body(String response) {
        return response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    /**
     * Raw socket client. The response is read on its own thread, started before the body is
     * written, because the server answers the malformed request while the upload is still in
     * flight and closes the connection; a synchronous write would see a reset and lose the response.
     */
    private static String post(EmbeddedServer server, byte[] body) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            InputStream in = socket.getInputStream();
            AtomicReference<String> response = new AtomicReference<>();
            Thread reader = new Thread(() -> {
                try {
                    response.set(read(in));
                } catch (IOException e) {
                    response.set("(read failed: " + e + ")");
                }
            }, "part-kind-test-reader");
            reader.start();
            OutputStream out = socket.getOutputStream();
            try {
                out.write(("POST /publisher-part-kind/upload HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Content-Type: multipart/form-data; boundary=" + BOUNDARY + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                for (int i = 0; i < body.length; i += 8192) {
                    out.write(body, i, Math.min(8192, body.length - i));
                    out.flush();
                }
            } catch (IOException e) {
                // the server rejected the request and closed the connection while we were still
                // uploading; the reader thread has whatever response it managed to send
            }
            reader.join(30_000);
            String r = response.get();
            return r == null ? "(no response within 30s)" : r;
        }
    }

    private static String read(InputStream in) throws IOException {
        var buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        try {
            int n;
            while ((n = in.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
        } catch (IOException e) {
            if (buf.size() == 0) {
                throw e;
            }
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}
