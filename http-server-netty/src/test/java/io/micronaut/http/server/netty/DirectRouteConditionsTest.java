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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The query and time conditions of direct routes on the Netty server, and a direct route that
 * declines a request by returning {@code null}: the request continues to the ordinary routes
 * with its body.
 */
class DirectRouteConditionsTest {
    private static final String SPEC_NAME = "DirectRouteConditionsTest";
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;
    private static Routes routes;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.idle-timeout", "5s"));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
        routes = ctx.getBean(Routes.class);
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aQueryConditionReadsTheDecodedParameters() {
        assertEquals("debug", retrieve(HttpRequest.GET("/dc/search?mode=debug")));
        // an encoded value is decoded like the parameters of a request
        assertEquals("spaced", retrieve(HttpRequest.GET("/dc/search?q=hello%20world%21")));
        assertEquals("spaced", retrieve(HttpRequest.GET("/dc/search?q=hello+world!")));
        assertEquals("ordinary", retrieve(HttpRequest.GET("/dc/search?mode=other")));
    }

    @Test
    void aTimeConditionReadsTheClockBean() {
        assertEquals("open", retrieve(HttpRequest.GET("/dc/sale")));
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> retrieve(HttpRequest.GET("/dc/future")));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
    }

    @Test
    void aDeclinedRequestReachesTheOrdinaryRouteWithItsBody() {
        int ordinary = routes.ordinary.get();
        // the direct route answers the known ids
        assertEquals("cached 1", retrieve(HttpRequest.POST("/dc/items/1", "ignored").contentType(MediaType.TEXT_PLAIN_TYPE)));
        assertEquals(ordinary, routes.ordinary.get());
        // it declines the others: the ordinary route reads the body
        String body = "x".repeat(50_000);
        assertEquals("stored 7: " + body.length() + " chars",
            retrieve(HttpRequest.POST("/dc/items/7", body).contentType(MediaType.TEXT_PLAIN_TYPE)));
        assertEquals(ordinary + 1, routes.ordinary.get());
        // a declined request without an ordinary route is not found
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> retrieve(HttpRequest.GET("/dc/declined")));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
    }

    private static String retrieve(HttpRequest<?> request) {
        return client.toBlocking().retrieve(request);
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ClockFactory {
        @Singleton
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes, HttpDirectRoutes {
        final AtomicInteger ordinary = new AtomicInteger();

        @Override
        public void routes(DirectRouteBuilder builder) {
            builder.GET("/dc/search").where(RouteCondition.query("mode", "debug")).respond(HttpResponse.ok("debug"));
            builder.GET("/dc/search").where(RouteCondition.query("q", "hello world!")).respond(HttpResponse.ok("spaced"));

            // the fixed clock is inside the window, the system clock is not
            builder.GET("/dc/sale")
                .where(RouteCondition.between(NOW.minusSeconds(60), NOW.plusSeconds(60)))
                .respond(HttpResponse.ok("open"));
            builder.GET("/dc/future").where(RouteCondition.after(NOW.plusSeconds(60))).respond(HttpResponse.ok("future"));

            builder.POST("/dc/items/{id}").respond(direct ->
                direct.pathVariables().getLong("id") < 5 ? direct.responses().ok("cached " + direct.pathVariables().getLong("id")) : null);

            builder.GET("/dc/declined").respond(direct -> null);
        }

        @Override
        public void routes(HttpRouteBuilder builder) {
            builder.GET("/dc/search", (request, pathVariables) -> text("ordinary"));
            builder.POST("/dc/items/{id}").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class).handle((request, pathVariables, body) -> {
                ordinary.incrementAndGet();
                return text("stored " + pathVariables.getLong("id") + ": " + body.length() + " chars");
            });
        }

        private static HttpResponse<?> text(String body) {
            return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
