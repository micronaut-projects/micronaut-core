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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A multipart route that rejects the request without reading the {@link MultipartBody} (or after
 * cancelling it right away) must release every buffer of the request body.
 */
class RejectedMultipartBodyLeakTest {
    static final int REQUESTS = 50;
    static final int CONCURRENCY = 32;
    static final byte[] BODY = multipartBody();

    static ApplicationContext context;
    static EmbeddedServer server;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of(
            "spec.name", "RejectedMultipartBodyLeakTest",
            "micronaut.server.max-request-size", "10GB",
            "micronaut.server.multipart.max-file-size", "10GB"
        ));
        server = context.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/rejected-multipart/throw",
        "/rejected-multipart/take0-throw",
        "/rejected-multipart/take0-not-found",
        "/rejected-multipart/mono-error",
        "/rejected-multipart/take0-mono-error",
    })
    void rejectedMultipartBodyIsReleased(String path) throws Exception {
        // the class-level leak check fails the test if a request body buffer stays open
        send(path, REQUESTS);
    }

    private static void send(String path, int count) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                results.add(pool.submit(() -> request(path)));
            }
            for (Future<String> result : results) {
                assertEquals("404", result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static String request(String path) throws IOException {
        try (Socket s = new Socket(server.getHost(), server.getPort())) {
            s.setSoTimeout(10_000);
            OutputStream out = s.getOutputStream();
            out.write(("POST " + path + "?key=wrong HTTP/1.1\r\nHost: x\r\nContent-Type: multipart/form-data; boundary=bb\r\n"
                + "Content-Length: " + BODY.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            try {
                out.write(BODY);
                out.flush();
            } catch (IOException ignored) {
                // the server may answer and close before the whole body is sent
            }
            InputStream in = s.getInputStream();
            byte[] status = in.readNBytes(12);
            return new String(status, StandardCharsets.US_ASCII).substring(9);
        }
    }

    private static byte[] multipartBody() {
        byte[] file = new byte[128 * 1024];
        new Random(1).nextBytes(file);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("--bb\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.writeBytes(file);
        body.writeBytes("\r\n--bb--\r\n".getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    @Controller("/rejected-multipart")
    @Requires(property = "spec.name", value = "RejectedMultipartBodyLeakTest")
    static class Ctrl {
        @Post(value = "/throw", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<HttpResponse<?>> wrongKey(@QueryValue String key, @Body MultipartBody parts) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "unknown key");
        }

        @Post(value = "/take0-throw", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<HttpResponse<?>> take0Throw(@QueryValue String key, @Body MultipartBody parts) {
            Flux.from(parts).take(0).subscribe();
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "unknown key");
        }

        @Post(value = "/take0-not-found", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<HttpResponse<?>> take0NotFound(@QueryValue String key, @Body MultipartBody parts) {
            Flux.from(parts).take(0).subscribe();
            return Mono.just(HttpResponse.notFound());
        }

        @Post(value = "/mono-error", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<HttpResponse<?>> monoError(@QueryValue String key, @Body MultipartBody parts) {
            return Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND, "unknown key"));
        }

        @Post(value = "/take0-mono-error", consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<HttpResponse<?>> take0MonoError(@QueryValue String key, @Body MultipartBody parts) {
            Flux.from(parts).take(0).subscribe();
            return Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND, "unknown key"));
        }
    }
}
