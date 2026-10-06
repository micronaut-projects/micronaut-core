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
package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.direct.DirectRouteBuilder;
import io.micronaut.web.router.direct.HttpDirectRoutes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Direct routes over h2c: an HTTP/2 stream is converted to a Netty request before the direct
 * routes are looked up, so they answer it like an HTTP/1.1 request, and a host condition reads
 * the authority of the request.
 */
@MicronautTest
@Property(name = "spec.name", value = "DirectRouteHttp2Test")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
@Property(name = "micronaut.http.client.http-version", value = "2.0")
class DirectRouteHttp2Test {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    Filtered filtered;

    @Test
    void aDirectRouteAnswersAnHttp2Stream() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/h2-direct/health"), String.class);
        // the client talks HTTP/2 to the server, as an ordinary route sees
        assertEquals(HttpVersion.HTTP_2_0.name(), client.toBlocking().retrieve(HttpRequest.GET("/h2-direct/version")));
        assertEquals("UP", response.body());
        assertEquals("2", response.getHeaders().get("Content-Length"));
        // only the ordinary route was filtered
        assertEquals(1, filtered.count.get());
    }

    @Test
    void aHeadStreamHasTheHeadersOnly() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.HEAD("/h2-direct/health"), String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("2", response.getHeaders().get("Content-Length"));
        assertEquals("", response.getBody(String.class).orElse(""));
    }

    @Test
    void anAsynchronousRouteAnswersAnHttp2Stream() {
        assertEquals("later", client.toBlocking().retrieve(HttpRequest.GET("/h2-async/later")));
        assertEquals("executor", client.toBlocking().retrieve(HttpRequest.GET("/h2-async/executor")));
        HttpClientResponseException failed = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/h2-async/failed")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatus());
        // declined later: the ordinary route reads the body of the stream
        assertEquals("stored 50000 chars", client.toBlocking().retrieve(
            HttpRequest.POST("/h2-async/upload", "x".repeat(50_000)).contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void aHostConditionReadsTheAuthority() {
        // the client sends the authority of the server, localhost
        assertEquals("local", client.toBlocking().retrieve(HttpRequest.GET("/h2-direct/host")));
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/h2-direct/other-host")));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
    }

    @Singleton
    @Requires(property = "spec.name", value = "DirectRouteHttp2Test")
    static class Routes implements HttpRoutes, HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/h2-direct/health", HttpResponse.ok("UP").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/h2-direct/host").where(RouteCondition.host("localhost", "127.0.0.1")).respond(HttpResponse.ok("local"));
            routes.GET("/h2-direct/other-host").where(RouteCondition.host("example.com")).respond(HttpResponse.ok("other"));
            routes.GET("/h2-async/later").respondAsync(direct -> CompletableFuture.supplyAsync(() -> direct.responses().ok("later"),
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)));
            routes.GET("/h2-async/executor").executeOn(TaskExecutors.BLOCKING).respond(direct -> direct.responses().ok("executor"));
            routes.GET("/h2-async/failed").respondAsync(direct -> CompletableFuture.failedFuture(new IllegalStateException("failed")));
            routes.POST("/h2-async/upload").respondAsync(direct -> CompletableFuture.supplyAsync(() -> null,
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)));
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/h2-async/upload").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class).handle((request, pathVariables, body) ->
                HttpResponse.ok("stored " + body.length() + " chars").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/h2-direct/version", (request, pathVariables) ->
                HttpResponse.ok(request.getHttpVersion().name()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @ServerFilter("/h2-direct/**")
    @Requires(property = "spec.name", value = "DirectRouteHttp2Test")
    static class Filtered {
        final AtomicInteger count = new AtomicInteger();

        @RequestFilter
        void count() {
            count.incrementAndGet();
        }
    }
}
