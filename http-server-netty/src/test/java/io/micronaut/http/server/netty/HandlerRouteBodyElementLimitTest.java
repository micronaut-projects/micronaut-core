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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The elements of a body are decoded in memory one at a time: each one is limited by
 * {@code micronaut.server.max-request-buffer-size}, like a buffered body, and a larger one fails
 * with {@code 413}. The body as a whole is not.
 */
class HandlerRouteBodyElementLimitTest {

    private static final int LIMIT = 64 * 1024;

    @Test
    void anElementLargerThanTheBufferLimitIsRejected() {
        String json = "[{\"name\":\"" + "x".repeat(4 * LIMIT) + "\"}]";
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, rejected(json, MediaType.APPLICATION_JSON_TYPE));
    }

    @Test
    void anElementOfAJsonStreamLargerThanTheBufferLimitIsRejected() {
        String json = "{\"name\":\"a\"}\n{\"name\":\"" + "x".repeat(4 * LIMIT) + "\"}\n";
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, rejected(json, MediaType.APPLICATION_JSON_STREAM_TYPE));
    }

    @Test
    void elementsWithinTheLimitAreReadWhenTheBodyIsLargerThanTheLimit() {
        StringJoiner json = new StringJoiner(",", "[", "]");
        int count = 4000;
        for (int i = 0; i < count; i++) {
            json.add("{\"name\":\"" + "x".repeat(64) + i + "\"}");
        }
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                String result = client.toBlocking().retrieve(HttpRequest.POST("/elements", json.toString()).contentType(MediaType.APPLICATION_JSON_TYPE));
                assertEquals("count=" + count, result);
            }
        }
    }

    private static HttpStatus rejected(String json, MediaType contentType) {
        try (ApplicationContext ctx = run()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                HttpClientResponseException error = assertThrows(HttpClientResponseException.class, () ->
                    client.toBlocking().retrieve(HttpRequest.POST("/elements", json).contentType(contentType)));
                return error.getStatus();
            }
        }
    }

    private static ApplicationContext run() {
        return ApplicationContext.run(Map.of("spec.name", "HandlerRouteBodyElementLimitTest", "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", LIMIT));
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteBodyElementLimitTest")
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return routes -> routes.asyncPOST("/elements", (request, variables, body) -> {
                AtomicInteger count = new AtomicInteger();
                return body.elements(Argument.mapOf(String.class, String.class))
                    .forEach(element -> {
                        count.incrementAndGet();
                        return CompletableFuture.completedStage(null);
                    })
                    .thenApply(ignored -> HttpResponse.ok("count=" + count.get()).contentType(MediaType.TEXT_PLAIN_TYPE));
            }).consumes(MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_STREAM_TYPE);
        }
    }
}
