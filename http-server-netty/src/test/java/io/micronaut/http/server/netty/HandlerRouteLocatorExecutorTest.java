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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A locator declared with {@code locateOn} runs on the named executor, not on the event loop,
 * with the propagated context of the request: it may block. The router then matches the rest of
 * the path with the routes of the located target, answers {@code 404} for a {@code null} target,
 * and answers the error of the locator with the error routes.
 */
class HandlerRouteLocatorExecutorTest {
    private static final String SPEC_NAME = "HandlerRouteLocatorExecutorTest";

    private static ApplicationContext ctx;
    private static HttpClient client;
    private static Calls calls;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
        calls = ctx.getBean(Calls.class);
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
    void theLocatorRunsOnTheExecutorAndTheLocatedRouteIsDispatched() {
        calls.clear();
        assertEquals("order 7, item 3", client.toBlocking().retrieve("/orders/7/items/3"));
        assertEquals(List.of("order 7"), calls.locators());
        assertOffTheEventLoop();
    }

    @Test
    void aNestedLocatorOfALocatedTargetRunsOnTheExecutor() {
        calls.clear();
        assertEquals("order 5, line 2", client.toBlocking().retrieve("/orders/5/lines/2"));
        assertEquals(List.of("order 5", "line 2"), calls.locators());
        assertOffTheEventLoop();
    }

    @Test
    void aNullTargetIsNotFound() {
        calls.clear();
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange("/orders/missing/items/1"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertOffTheEventLoop();
    }

    @Test
    void aRestOfThePathThatNoLocatedRouteMatchesIsNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange("/orders/7/unknown"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void theErrorOfTheLocatorIsAnsweredByTheErrorRoutes() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.GET("/orders/failing/items/1"), String.class));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("failed: no order failing", e.getResponse().getBody(String.class).orElse(null));
    }

    @Test
    void aLocatorOnAnUnknownExecutorFailsTheRequest() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange("/unknown-executor/1/items/1"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    private static void assertOffTheEventLoop() {
        assertFalse(calls.threads().isEmpty());
        for (String thread : calls.threads()) {
            assertFalse(thread.contains("EventLoop"), thread);
        }
        assertTrue(calls.requests().stream().allMatch(Boolean::booleanValue), "the locator runs without the request in its context");
    }

    record Order(String id) {
    }

    record Line(Order order, int number) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class Calls {
        private final List<String> locators = new CopyOnWriteArrayList<>();
        private final List<String> threads = new CopyOnWriteArrayList<>();
        private final List<Boolean> requests = new CopyOnWriteArrayList<>();

        void record(String locator) {
            locators.add(locator);
            threads.add(Thread.currentThread().getName());
            requests.add(ServerRequestContext.currentRequest().isPresent());
        }

        void clear() {
            locators.clear();
            threads.clear();
            requests.clear();
        }

        List<String> locators() {
            return locators;
        }

        List<String> threads() {
            return threads;
        }

        List<Boolean> requests() {
            return requests;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class Routes implements HttpRoutes {
        private final Calls calls;

        Routes(Calls calls) {
            this.calls = calls;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.locateOn(TaskExecutors.BLOCKING, "/orders/{id}", (request, pathVariables) -> {
                String id = pathVariables.getString("id");
                calls.record("order " + id);
                if ("failing".equals(id)) {
                    throw new IllegalStateException("no order " + id);
                }
                return "missing".equals(id) ? null : new Order(id);
            }, new OrderRoutes(calls));
            routes.locateOn("no-such-executor", "/unknown-executor/{id}", (request, pathVariables) -> new Order("unknown"), new OrderRoutes(calls));
            routes.error(IllegalStateException.class, (request, error) ->
                HttpResponse.status(HttpStatus.CONFLICT).body("failed: " + error.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    static final class OrderRoutes implements LocatedRoutes<Order> {
        private final Calls calls;

        OrderRoutes(Calls calls) {
            this.calls = calls;
        }

        @Override
        public Argument<Order> targetType() {
            return Argument.of(Order.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Order> order) {
            order.GET("/items/{item}").handle((request, pathVariables, located) ->
                HttpResponse.ok("order " + located.id() + ", item " + pathVariables.getString("item")).contentType(MediaType.TEXT_PLAIN_TYPE));
            order.locateOn(TaskExecutors.BLOCKING, "/lines/{line}", (request, pathVariables, located) -> {
                calls.record("line " + pathVariables.getString("line"));
                return new Line(located, Integer.parseInt(pathVariables.getString("line")));
            }, new LineRoutes());
        }
    }

    static final class LineRoutes implements LocatedRoutes<Line> {
        @Override
        public Argument<Line> targetType() {
            return Argument.of(Line.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Line> line) {
            line.GET("/").handle((request, pathVariables, located) ->
                HttpResponse.ok("order " + located.order().id() + ", line " + located.number()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
