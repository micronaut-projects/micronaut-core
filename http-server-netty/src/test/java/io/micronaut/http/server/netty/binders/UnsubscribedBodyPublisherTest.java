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
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A handler that throws before it subscribes to its streaming body argument (or cancels before
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
        "/unsubscribed/part-data-throws",
        "/unsubscribed/multipart-body-throws",
        "/unsubscribed/bytes-throws",
        "/unsubscribed/bytes-first",
    })
    void keepAliveConnectionStaysUsable(String path) throws IOException {
        boolean multipart = !path.contains("bytes");
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
        @Post(value = "/part-data-throws", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String partDataThrows(@Part("file") Publisher<PartData> file) {
            throw new HttpStatusException(HttpStatus.I_AM_A_TEAPOT, "no");
        }

        @Post(value = "/multipart-body-throws", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String multipartBodyThrows(@Body MultipartBody body) {
            throw new HttpStatusException(HttpStatus.I_AM_A_TEAPOT, "no");
        }

        @Post(value = "/bytes-throws", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String bytesThrows(@Body Publisher<byte[]> body) {
            throw new HttpStatusException(HttpStatus.I_AM_A_TEAPOT, "no");
        }

        @Post(value = "/bytes-first", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Mono<String> bytesFirst(@Body Publisher<byte[]> body) {
            return Flux.from(body).next().map(b -> "ok").defaultIfEmpty("ok");
        }

        @Post(value = "/json", consumes = MediaType.APPLICATION_JSON, produces = MediaType.TEXT_PLAIN)
        String json(@Body Map<String, Object> body) {
            return "json";
        }
    }
}
