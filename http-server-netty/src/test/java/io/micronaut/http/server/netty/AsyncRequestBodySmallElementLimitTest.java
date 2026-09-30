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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The elements of a body read with {@link AsyncRequestBody#elements} are limited one at a time by
 * a {@code micronaut.server.max-request-buffer-size} that is smaller than what arrives before the
 * handler reads the elements, e.g. in the first network read with the headers: the bytes that
 * arrived before are not limited as a buffered body.
 */
class AsyncRequestBodySmallElementLimitTest {

    static Stream<Arguments> cases() {
        List<Arguments> cases = new ArrayList<>();
        for (int limit : new int[]{1024}) {
            for (String contentType : new String[]{MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM}) {
                for (boolean chunked : new boolean[]{false, true}) {
                    cases.add(Arguments.of(limit, contentType, chunked));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "limit={0}, {1}, chunked={2}")
    @MethodSource("cases")
    void smallElementsOfABodyLargerThanTheLimitAreRead(int limit, String contentType, boolean chunked) throws Exception {
        int count = 0;
        StringBuilder json = new StringBuilder();
        boolean array = contentType.equals(MediaType.APPLICATION_JSON);
        if (array) {
            json.append('[');
        }
        // several times the limit, in elements far below it
        while (json.length() < 4 * limit) {
            if (array && count > 0) {
                json.append(',');
            }
            json.append("{\"name\":\"element").append(count).append("\"}");
            if (!array) {
                json.append('\n');
            }
            count++;
        }
        if (array) {
            json.append(']');
        }
        try (ApplicationContext ctx = run(limit)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, json.toString(), contentType, chunked);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("count=" + count, response.body());
        }
    }

    @ParameterizedTest(name = "limit={0}, {1}, chunked={2}")
    @MethodSource("cases")
    void anElementLargerThanTheLimitIsRejected(int limit, String contentType, boolean chunked) throws Exception {
        String large = "{\"name\":\"" + "x".repeat(2 * limit) + "\"}";
        String json = contentType.equals(MediaType.APPLICATION_JSON)
            ? "[{\"name\":\"a\"}," + large + "]"
            : "{\"name\":\"a\"}\n" + large + "\n";
        try (ApplicationContext ctx = run(limit)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, json, contentType, chunked);
            assertEquals(413, response.statusCode(), response.body());
        }
    }

    @ParameterizedTest(name = "limit={0}, {1}, chunked={2}")
    @MethodSource("cases")
    void aBodyThatArrivesWholeIsReadElementByElement(int limit, String contentType, boolean chunked) throws Exception {
        // a little more than the limit: it arrives with the headers, before the handler runs
        StringBuilder json = new StringBuilder();
        boolean array = contentType.equals(MediaType.APPLICATION_JSON);
        int count = 0;
        while (json.length() < limit + limit / 2) {
            json.append(array ? (count == 0 ? "[" : ",") : "").append("{\"name\":\"").append(count).append("\"}").append(array ? "" : "\n");
            count++;
        }
        if (array) {
            json.append(']');
        }
        try (ApplicationContext ctx = run(limit)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, json.toString(), contentType, chunked);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("count=" + count, response.body());
        }
    }

    @ParameterizedTest(name = "limit={0}, chunked={1}")
    @MethodSource("limits")
    void theWholeBodyIsLimitedForTheOtherReads(int limit, boolean chunked) throws Exception {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; json.length() < 4 * limit; i++) {
            json.append(i == 0 ? "" : ",").append("{\"name\":\"element").append(i).append("\"}");
        }
        json.append(']');
        try (ApplicationContext ctx = run(limit)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            for (String path : new String[]{"/small-elements/body", "/small-elements/text", "/small-elements/bytes", "/small-elements/argument"}) {
                java.net.http.HttpResponse<String> response = post(server, path, json.toString(), MediaType.APPLICATION_JSON, chunked);
                assertEquals(413, response.statusCode(), path + ": " + response.body());
            }
        }
    }

    @ParameterizedTest(name = "limit={0}, chunked={1}")
    @MethodSource("limits")
    void aBodyLargerThanTheLimitIsWrittenToAFile(int limit, boolean chunked) throws Exception {
        String body = "x".repeat(8 * limit);
        try (ApplicationContext ctx = run(limit)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            java.net.http.HttpResponse<String> response = post(server, "/small-elements/file", body, MediaType.TEXT_PLAIN, chunked);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("length=" + body.length(), response.body());
        }
    }

    @ParameterizedTest(name = "limit={0}, chunked={1}")
    @MethodSource("limits")
    void aStreamingReadOfACopyIsLimitedAsAWhole(int limit, boolean chunked) throws Exception {
        // numbers far below the limit: the elements are, the body they make is not
        String large = numbers(6 * limit);
        String small = numbers(limit / 2);
        int smallCount = small.split(",").length;
        try (ApplicationContext ctx = run(limit)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            for (String path : new String[]{"/small-elements/copy-elements", "/small-elements/copy-elements-only", "/small-elements/copy-file", "/small-elements/copy-file-only"}) {
                // the body cannot keep the bytes the copy streams for the route: the copy fails,
                // whether or not the route reads the body after it
                java.net.http.HttpResponse<String> response = post(server, path, large, MediaType.APPLICATION_JSON, chunked);
                assertEquals(413, response.statusCode(), path + ": " + response.body());
            }
            // below the limit the copy is read, and the route then reads the whole body
            java.net.http.HttpResponse<String> elements = post(server, "/small-elements/copy-elements", small, MediaType.APPLICATION_JSON, chunked);
            assertEquals(200, elements.statusCode(), elements.body());
            assertEquals("count=" + smallCount + " " + small, elements.body());
            java.net.http.HttpResponse<String> file = post(server, "/small-elements/copy-file", small, MediaType.APPLICATION_JSON, chunked);
            assertEquals(200, file.statusCode(), file.body());
            assertEquals("length=" + small.length() + " " + small, file.body());
        }
    }

    /**
     * @return A JSON array of small numbers, of at least the length
     */
    private static String numbers(int length) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; json.length() < length; i++) {
            json.append(i == 0 ? "" : ",").append(i % 100);
        }
        return json.append(']').toString();
    }

    static Stream<Arguments> limits() {
        List<Arguments> cases = new ArrayList<>();
        for (int limit : new int[]{1024}) {
            for (boolean chunked : new boolean[]{false, true}) {
                cases.add(Arguments.of(limit, chunked));
            }
        }
        return cases.stream();
    }

    private static java.net.http.HttpResponse<String> post(EmbeddedServer server, String body, String contentType, boolean chunked) throws Exception {
        return post(server, "/small-elements", body, contentType, chunked);
    }

    private static java.net.http.HttpResponse<String> post(EmbeddedServer server, String path, String body, String contentType, boolean chunked) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher publisher = chunked
            // an unknown length: the body is sent chunked
            ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
            : HttpRequest.BodyPublishers.ofByteArray(bytes);
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(server.getURL() + path))
                .header("Content-Type", contentType)
                .POST(publisher)
                .build();
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        }
    }

    private static ApplicationContext run(int limit) {
        return ApplicationContext.run(Map.of("spec.name", "AsyncRequestBodySmallElementLimitTest", "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", limit));
    }

    @Controller("/small-elements")
    @Requires(property = "spec.name", value = "AsyncRequestBodySmallElementLimitTest")
    static class ElementsController {
        @Post(consumes = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM})
        CompletionStage<HttpResponse<String>> elements(AsyncRequestBody body) {
            AtomicInteger count = new AtomicInteger();
            return body.elements(Argument.mapOf(String.class, String.class))
                .forEach(element -> {
                    count.incrementAndGet();
                    return CompletableFuture.completedStage(null);
                })
                .thenApply(ignored -> HttpResponse.ok("count=" + count.get()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }

        @Post(uri = "/body", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> body(AsyncRequestBody body) {
            return body.body(Argument.listOf(Argument.mapOf(String.class, String.class)))
                .thenApply(list -> HttpResponse.ok("count=" + list.size()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }

        @Post(uri = "/text", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> text(AsyncRequestBody body) {
            return body.text().thenApply(text -> HttpResponse.ok("length=" + text.length()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }

        @Post(uri = "/bytes", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> bytes(AsyncRequestBody body) {
            return body.bytes(2048).thenApply(bytes -> HttpResponse.ok("length=" + bytes.length).contentType(MediaType.TEXT_PLAIN_TYPE));
        }

        @Post(uri = "/file", consumes = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> file(AsyncRequestBody body) throws IOException {
            Path file = Files.createTempDirectory("small-elements").resolve("body");
            return body.transferTo(file).thenApply(done -> {
                try {
                    return HttpResponse.ok("length=" + Files.size(file)).contentType(MediaType.TEXT_PLAIN_TYPE);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        @Post(uri = "/copy-elements", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> copyElements(AsyncRequestBody body) {
            return copyElementsOnly(body).thenCompose(counted -> body.text()
                .thenApply(text -> HttpResponse.ok(counted.body() + " " + text).contentType(MediaType.TEXT_PLAIN_TYPE)));
        }

        @Post(uri = "/copy-elements-only", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> copyElementsOnly(AsyncRequestBody body) {
            AtomicInteger count = new AtomicInteger();
            return body.copy().elements(Integer.class)
                .forEach(element -> {
                    count.incrementAndGet();
                    return CompletableFuture.completedStage(null);
                })
                .thenApply(ignored -> HttpResponse.ok("count=" + count.get()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }

        @Post(uri = "/copy-file", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> copyFile(AsyncRequestBody body) throws IOException {
            return copyFileOnly(body).thenCompose(written -> body.text()
                .thenApply(text -> HttpResponse.ok(written.body() + " " + text).contentType(MediaType.TEXT_PLAIN_TYPE)));
        }

        @Post(uri = "/copy-file-only", consumes = MediaType.APPLICATION_JSON)
        CompletionStage<HttpResponse<String>> copyFileOnly(AsyncRequestBody body) throws IOException {
            return file(body.copy());
        }

        @Post(uri = "/argument", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<String> argument(@Body List<Map<String, String>> body) {
            return HttpResponse.ok("count=" + body.size()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
