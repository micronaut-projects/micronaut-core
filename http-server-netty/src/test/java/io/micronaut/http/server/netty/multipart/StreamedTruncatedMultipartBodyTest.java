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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multipart body with a large file part that is streamed to the route, and ends before its
 * closing boundary, is answered with 400 for every kind of part argument.
 */
class StreamedTruncatedMultipartBodyTest {
    private static ApplicationContext context;
    private static EmbeddedServer server;
    private static final List<Throwable> DROPPED_ERRORS = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void start() {
        Hooks.onErrorDropped(DROPPED_ERRORS::add);
        context = ApplicationContext.run(Map.of("spec.name", "StreamedTruncatedMultipartBodyTest"));
        server = context.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        context.close();
        Hooks.resetOnErrorDropped();
    }

    @ParameterizedTest
    @ValueSource(strings = {"part-data", "completed", "streaming", "multipart-body"})
    void aStreamedBodyCutBeforeItsClosingBoundaryIsABadRequest(String path) throws Exception {
        DROPPED_ERRORS.clear();
        String response = post(path, false);
        assertTrue(response.startsWith("HTTP/1.1 400 "), response);
        // the consumer of the part fails with the bad request, not with a discarded body
        assertEquals(List.of(), DROPPED_ERRORS);
        response = post(path, true);
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
    }

    private static String post(String path, boolean complete) throws IOException, InterruptedException {
        byte[] file = new byte[40_000];
        new Random(2).nextBytes(file);
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.writeBytes("--bb\r\nContent-Disposition: form-data; name=\"attr\"\r\n\r\nhello\r\n".getBytes(StandardCharsets.US_ASCII));
        full.writeBytes("--bb\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        full.writeBytes(file);
        full.writeBytes("\r\n--bb--\r\n".getBytes(StandardCharsets.US_ASCII));
        byte[] body = complete ? full.toByteArray() : Arrays.copyOf(full.toByteArray(), full.size() - 20);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /streamed-truncated-multipart/" + path + " HTTP/1.1\r\n"
                + "Host: " + server.getHost() + "\r\n"
                + "Content-Type: multipart/form-data; boundary=bb\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
            int half = body.length / 2;
            out.write(body, 0, half);
            out.flush();
            Thread.sleep(100);
            out.write(body, half, body.length - half);
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Controller("/streamed-truncated-multipart")
    @Requires(property = "spec.name", value = "StreamedTruncatedMultipartBodyTest")
    static class FormController {
        @Post(value = "/part-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> partData(@Part("file") Publisher<PartData> file) {
            return Flux.from(file).doOnNext(PartData::close).then(Mono.just("ok"));
        }

        @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String completed(@Part("attr") String attr, @Part("file") CompletedFileUpload file) throws IOException {
            try (file) {
                return "ok";
            }
        }

        @Post(value = "/streaming", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> streaming(StreamingFileUpload file) {
            return Mono.from(file.transferTo(new ByteArrayOutputStream())).thenReturn("ok");
        }

        @Post(value = "/multipart-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> multipartBody(@Body MultipartBody body) {
            return Flux.from(body).then(Mono.just("ok"));
        }
    }
}
