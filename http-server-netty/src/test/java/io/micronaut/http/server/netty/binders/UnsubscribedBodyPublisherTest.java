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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A handler that never subscribes to its streaming body argument (or fails or cancels before
 * reading all of it) must not leak the body or leave the keep-alive connection stuck.
 */
class UnsubscribedBodyPublisherTest {
    static final int[] SIZES = {1000, 150_000, 300_000};

    static ApplicationContext context;
    static EmbeddedServer server;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of(
            "spec.name", "UnsubscribedBodyPublisherTest",
            "micronaut.server.max-request-size", 1024 * 1024,
            "micronaut.server.multipart.max-file-size", 400 * 1024
        ));
        server = context.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/unsubscribed/part-data",
        "/unsubscribed/multipart-body",
        "/unsubscribed/bytes",
        "/unsubscribed/bytes-throws",
        "/unsubscribed/bytes-first",
        "/unsubscribed/part-data-first",
        "/unsubscribed/input-stream",
        "/unsubscribed/streaming-upload",
        "/unsubscribed/raw-field",
    })
    void keepAliveConnectionStaysUsable(String path) throws IOException {
        boolean multipart = !path.contains("bytes") && !path.contains("input-stream");
        for (int size : SIZES) {
            byte[] file = new byte[size];
            new Random(1).nextBytes(file);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (multipart) {
                body.writeBytes("--bb\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.bin\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                body.writeBytes(file);
                body.writeBytes("\r\n--bb--\r\n".getBytes(StandardCharsets.UTF_8));
            } else {
                body.writeBytes(file);
            }
            String json = "{\"a\":\"x\"}";
            try (Socket s = new Socket(server.getHost(), server.getPort())) {
                s.setSoTimeout(5000);
                OutputStream out = s.getOutputStream();
                InputStream in = s.getInputStream();
                out.write(("POST " + path + " HTTP/1.1\r\nHost: x\r\nContent-Type: " + (multipart ? "multipart/form-data; boundary=bb" : "application/octet-stream")
                    + "\r\nContent-Length: " + body.size() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body.toByteArray());
                out.flush();
                String first = readResponse(in);
                assertTrue(first.startsWith("200 ok") || first.startsWith("418 "), "first response, body of " + size + " bytes: " + first);
                out.write(("POST /unsubscribed/json HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\nContent-Length: " + json.length() + "\r\n\r\n" + json).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                assertEquals("200 json", readResponse(in), "the request after a body of " + size + " bytes was not answered");
            }
        }
    }

    @Test
    void subscribedBodyIsLeftAlone() throws Exception {
        int size = 300_000;
        byte[] file = new byte[size];
        new Random(1).nextBytes(file);
        Ctrl.BACKGROUND = new CompletableFuture<>();
        try (Socket s = new Socket(server.getHost(), server.getPort())) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write(("POST /unsubscribed/bytes-background HTTP/1.1\r\nHost: x\r\nContent-Type: application/octet-stream\r\nContent-Length: " + size + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            // the route returns before the body is sent, the response is written first
            assertEquals("200 ok", readResponse(in));
            out.write(file);
            out.flush();
            // the subscription the route made before returning still receives the whole body
            assertEquals((long) size, Ctrl.BACKGROUND.get(5, TimeUnit.SECONDS));
        }
    }

    static String readResponse(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        try {
            while (!head.toString().endsWith("\r\n\r\n")) {
                int b = in.read();
                if (b < 0) {
                    return head.isEmpty() ? "EOF" : "EOF in head " + head;
                }
                head.append((char) b);
            }
        } catch (SocketTimeoutException e) {
            return "TIMEOUT";
        }
        String h = head.toString();
        int len = 0;
        for (String l : h.split("\r\n")) {
            if (l.toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(l.substring(15).trim());
            }
        }
        byte[] body = in.readNBytes(len);
        return h.substring(9, h.indexOf("\r\n") - 3).trim() + " " + new String(body, StandardCharsets.UTF_8);
    }

    @Controller("/unsubscribed")
    @Requires(property = "spec.name", value = "UnsubscribedBodyPublisherTest")
    static class Ctrl {
        static volatile CompletableFuture<Long> BACKGROUND = new CompletableFuture<>();

        @Post(value = "/part-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String partData(@Part("file") Publisher<PartData> file) {
            return "ok";
        }

        @Post(value = "/multipart-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String multipartBody(@Body MultipartBody body) {
            return "ok";
        }

        @Post(value = "/bytes", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String bytes(@Body Publisher<byte[]> body) {
            return "ok";
        }

        @Post(value = "/input-stream", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String inputStream(@Body InputStream body) {
            return "ok";
        }

        @Post(value = "/streaming-upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String streamingUpload(StreamingFileUpload file) {
            return "ok";
        }

        @Post(value = "/raw-field", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String rawField(RawFormField file) {
            return "ok";
        }

        @Post(value = "/bytes-background", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String bytesBackground(@Body Publisher<byte[]> body) {
            Flux.from(body).map(b -> (long) b.length).reduce(0L, Long::sum).subscribe(BACKGROUND::complete, BACKGROUND::completeExceptionally);
            return "ok";
        }

        @Post(value = "/bytes-throws", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String bytesThrows(@Body Publisher<byte[]> body) {
            throw new HttpStatusException(HttpStatus.I_AM_A_TEAPOT, "no");
        }

        @Post(value = "/bytes-first", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Mono<String> bytesFirst(@Body Publisher<byte[]> body) {
            return Flux.from(body).next().map(b -> "ok").defaultIfEmpty("ok");
        }

        @Post(value = "/part-data-first", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> partDataFirst(@Part("file") Publisher<PartData> file) {
            return Flux.from(file).next().map(pd -> {
                pd.close();
                return "ok";
            }).defaultIfEmpty("ok");
        }

        @Post(value = "/json", consumes = MediaType.APPLICATION_JSON, produces = MediaType.TEXT_PLAIN)
        String json(@Body Map<String, Object> body) {
            return "json";
        }
    }
}
