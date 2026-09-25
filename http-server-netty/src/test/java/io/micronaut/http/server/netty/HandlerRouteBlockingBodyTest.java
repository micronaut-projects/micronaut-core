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
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.exceptions.RoutingException;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A handler route that reads the body as an {@link InputStream} blocks until the body arrives:
 * the route must run on an executor, which the router checks when it takes the route.
 */
class HandlerRouteBlockingBodyTest {

    @Test
    void anInputStreamBodyOnTheEventLoopFailsAtStartup() {
        Throwable error = assertThrows(Throwable.class, () -> {
            try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteBlockingBodyTest.eventLoop", "micronaut.server.port", -1))) {
                ctx.getBean(EmbeddedServer.class).start();
            }
        });
        RoutingException routing = find(error);
        assertNotNull(routing, () -> "no routing exception in " + error);
        assertTrue(routing.getMessage().contains("/blocking/event-loop"), routing.getMessage());
        assertTrue(routing.getMessage().contains("InputStream"), routing.getMessage());
    }

    @Test
    void anInputStreamBodyOnAnExecutorIsRead() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteBlockingBodyTest.executor", "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                String body = client.toBlocking().retrieve(HttpRequest.POST("/blocking/executor", "streamed").contentType(MediaType.TEXT_PLAIN_TYPE));
                assertEquals("streamed", body);
            }
        }
    }

    private static RoutingException find(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RoutingException routing) {
                return routing;
            }
        }
        return null;
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteBlockingBodyTest.eventLoop")
    static class EventLoopRoutes {
        @Singleton
        HttpRoutes eventLoopRoutes() {
            return routes -> routes.POST("/blocking/event-loop", Argument.of(InputStream.class), (request, pathVariables, in) ->
                HttpResponse.ok(read(in))).consumesAll();
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteBlockingBodyTest.executor")
    static class ExecutorRoutes {
        @Singleton
        HttpRoutes executorRoutes() {
            return routes -> routes.POST("/blocking/executor", Argument.of(InputStream.class), (request, pathVariables, in) ->
                HttpResponse.ok(read(in)).contentType(MediaType.TEXT_PLAIN_TYPE)).consumesAll().executeOn(TaskExecutors.BLOCKING);
        }
    }
}
