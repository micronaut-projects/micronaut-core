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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter that is not given arguments reads a copy of the body of {@link AsyncRequestBody#of},
 * as often as it likes, without blocking, and the route reads the body afterwards: a pre-matching filter method
 * and a functional route filter, with bodies larger than what a connection receives unread.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class AsyncRequestBodyOfTest {
    public static final String SPEC_NAME = "AsyncRequestBodyOfTest";
    private static final String DIGEST = "X-Body-Digest";

    @Test
    void aPreMatchingFilterBuffersALargeBodyAndTheControllerReadsIt() throws IOException {
        String body = largeBody(256 * 1024);
        try (ServerUnderTest server = server(Map.of())) {
            HttpResponse<String> response = server.exchange(post("/fbb/controller", body), String.class);
            assertEquals(200, response.code());
            // the filter read the copy twice, the route read the body
            assertEquals(sha256(body) + "," + sha256(body) + ":" + body.length(), response.body());
        }
    }

    @Test
    void aFunctionalRouteFilterBuffersALargeBodyAndTheHandlerRouteReadsIt() throws IOException {
        String body = largeBody(200 * 1024);
        try (ServerUnderTest server = server(Map.of())) {
            HttpResponse<String> response = server.exchange(post("/fbb/handler", body), String.class);
            assertEquals(200, response.code());
            assertEquals(sha256(body) + ":" + body.length(), response.body());
        }
    }

    @Test
    void copiesOfSeparatelyAcquiredBodiesAreReadAtTheSameTime() throws IOException {
        String body = largeBody(300 * 1024);
        try (ServerUnderTest server = server(Map.of())) {
            HttpResponse<String> response = server.exchange(post("/fbb/concurrent", body), String.class);
            assertEquals(200, response.code());
            assertEquals("same," + sha256(body) + "," + sha256(body) + ":" + body.length(), response.body());
        }
    }

    @Test
    void anEmptyBodyIsBufferedEmpty() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            HttpResponse<String> response = server.exchange(post("/fbb/empty", ""), String.class);
            assertEquals(200, response.code());
            assertEquals(sha256(""), response.body());
        }
    }

    @Test
    void aBodyLargerThanTheBufferLimitIsRejected() {
        String body = largeBody(16 * 1024);
        try (ServerUnderTest server = server(Map.of("micronaut.server.max-request-buffer-size", 1024))) {
            AssertionUtils.assertThrows(server, post("/fbb/handler", body), HttpResponseAssertion.builder()
                .status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                .build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ServerUnderTest server(Map<String, Object> properties) {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, properties);
    }

    private static HttpRequest<?> post(String uri, String body) {
        return HttpRequest.POST(uri, body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static String largeBody(int size) {
        StringBuilder builder = new StringBuilder(size);
        for (int i = 0; builder.length() < size; i++) {
            builder.append(i).append(',');
        }
        builder.setLength(size);
        return builder.toString();
    }

    static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(InputStream stream) {
        try (stream) {
            return sha256(stream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ServerFilter("/fbb/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BufferingFilter {
        @RequestFilter
        @PreMatching
        CompletionStage<@Nullable HttpResponse<?>> filter(HttpRequest<?> request) {
            return AsyncRequestBody.of(request).copy().bytes(Integer.MAX_VALUE).thenApply(bytes -> {
                // the bytes are read twice
                String first = sha256(new ByteArrayInputStream(bytes));
                String second = sha256(new ByteArrayInputStream(bytes));
                request.setAttribute(DIGEST, first + "," + second);
                return null;
            });
        }
    }

    @Controller("/fbb")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    static class BufferedController {
        @Post("/controller")
        String post(HttpRequest<?> request, @Body String body) {
            return request.getAttribute(DIGEST, String.class).orElse("none") + ":" + body.length();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.serverFilter("/fbb/handler", "/fbb/empty").beforeReplacingAsync(request -> AsyncRequestBody.of(request).copy().bytes(Integer.MAX_VALUE)
                .thenApply(bytes -> {
                    request.setAttribute(DIGEST, sha256(bytes));
                    return null;
                }));
            routes.serverFilter("/fbb/concurrent").beforeReplacingAsync(request -> {
                AsyncRequestBody first = AsyncRequestBody.of(request);
                AsyncRequestBody second = AsyncRequestBody.of(request);
                // two readers that acquired the body separately read their copies on other threads
                CompletableFuture<byte[]> a = CompletableFuture.supplyAsync(() -> second.copy().bytes(Integer.MAX_VALUE))
                    .thenCompose(CompletionStage::toCompletableFuture);
                CompletableFuture<byte[]> b = CompletableFuture.supplyAsync(() -> first.copy().bytes(Integer.MAX_VALUE))
                    .thenCompose(CompletionStage::toCompletableFuture);
                return a.thenCombine(b, (x, y) -> {
                    request.setAttribute(DIGEST, (first == second ? "same," : "different,") + sha256(x) + "," + sha256(y));
                    return (HttpResponse<?>) null;
                });
            });
            routes.POST("/fbb/concurrent").consumesAll().body(Argument.STRING).handle((request, pathVariables, body) ->
                HttpResponse.ok(request.getAttribute(DIGEST, String.class).orElse("none") + ":" + (body == null ? 0 : body.length()))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/fbb/empty").consumesAll().handle((request, pathVariables) ->
                HttpResponse.ok(request.getAttribute(DIGEST, String.class).orElse("none")).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/fbb/handler").consumesAll().body(Argument.STRING).handle((request, pathVariables, body) ->
                HttpResponse.ok(request.getAttribute(DIGEST, String.class).orElse("none") + ":" + (body == null ? 0 : body.length()))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
