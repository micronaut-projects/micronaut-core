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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Body conditions and route results that complete after the routing returned, with the
 * execution prepared for the route and with the compatibility execution. Each pending step is a
 * future the test completes once the server entered it, after checking that the response is
 * still pending: each route is invoked once, a condition rejected later falls through, a result
 * that fails later reaches the error route, and the shared stage of a request the client
 * abandoned is not cancelled once the server saw its connection closed.
 */
class PendingRouteExecutionTest {

    private static final String SPEC = "PendingRouteExecutionTest";
    private static final long WAIT = 10;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingWork(boolean compatibility) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1))) {
            ctx.getBean(RouteExecutor.class).forceCompatibility = compatibility;
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            Routes routes = ctx.getBean(Routes.class);
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                // a condition accepted later selects its route, invoked once
                CompletableFuture<String> accepted = text(client, HttpRequest.POST("/conditions", "accept").contentType(MediaType.TEXT_PLAIN_TYPE));
                pending(routes.condition.entered(), accepted).complete(true);
                assertEquals("accepted", accepted.get(WAIT, TimeUnit.SECONDS));
                assertEquals(1, routes.accepted.get());

                // a condition rejected later falls through to the route without conditions
                CompletableFuture<String> rejected = text(client, HttpRequest.POST("/conditions", "reject").contentType(MediaType.TEXT_PLAIN_TYPE));
                pending(routes.condition.entered(), rejected).complete(false);
                assertEquals("fallback", rejected.get(WAIT, TimeUnit.SECONDS));
                assertEquals(1, routes.accepted.get());
                assertEquals(1, routes.fallback.get());

                // a result completed later answers once
                CompletableFuture<String> later = text(client, HttpRequest.GET("/later"));
                pending(routes.later.entered(), later).complete(HttpResponse.ok("later"));
                assertEquals("later", later.get(WAIT, TimeUnit.SECONDS));
                assertEquals(1, routes.laterInvocations.get());

                // a result that fails later is answered by the error route
                CompletableFuture<String> failing = text(client, HttpRequest.GET("/failing"));
                pending(routes.failing.entered(), failing).completeExceptionally(new IllegalStateException("later"));
                ExecutionException failed = assertThrows(ExecutionException.class, () -> failing.get(WAIT, TimeUnit.SECONDS));
                assertEquals(HttpStatus.CONFLICT, assertInstanceOf(HttpClientResponseException.class, failed.getCause()).getStatus());

                // the server sees the abandonment of a request, which does not cancel the shared stage of its route
                // the client closes the connection: a cancelled exchange of the client drains it instead
                try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                    socket.getOutputStream().write(("GET /shared HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    routes.sharedInvoked.get(WAIT, TimeUnit.SECONDS);
                }
                routes.sharedClosed.get(WAIT, TimeUnit.SECONDS);
                assertFalse(routes.shared.isCancelled());
                assertFalse(routes.shared.isDone());
                routes.shared.complete(HttpResponse.ok("shared"));
                assertEquals("shared", client.toBlocking().retrieve(HttpRequest.GET("/shared")));
            }
        }
    }

    private static CompletableFuture<String> text(HttpClient client, HttpRequest<?> request) {
        return Mono.from(client.retrieve(request)).toFuture();
    }

    /**
     * Wait until the server entered the pending step, and check that the response still waits
     * for it.
     *
     * @param entered  Completes with the pending step when the server entered it
     * @param response The response
     * @param <T>      The type of the step
     * @return The pending step, to complete
     */
    private static <T> CompletableFuture<T> pending(CompletableFuture<CompletableFuture<T>> entered, CompletableFuture<String> response) throws Exception {
        CompletableFuture<T> step = entered.get(WAIT, TimeUnit.SECONDS);
        assertFalse(response.isDone(), "the response waits for the pending step");
        return step;
    }

    /**
     * A step of a route that the test completes: each time the server enters it, it is a new
     * future, handed to the test.
     *
     * @param <T> The type of the step
     */
    static final class Pending<T> {
        private final AtomicReference<CompletableFuture<CompletableFuture<T>>> entered = new AtomicReference<>(new CompletableFuture<>());

        CompletableFuture<T> enter() {
            CompletableFuture<T> step = new CompletableFuture<>();
            entered.get().complete(step);
            return step;
        }

        CompletableFuture<CompletableFuture<T>> entered() {
            CompletableFuture<CompletableFuture<T>> current = entered.get();
            try {
                current.get(WAIT, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("the server did not enter the pending step", e);
            }
            // the next request enters a fresh step
            entered.set(new CompletableFuture<>());
            return current;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class Routes implements HttpRoutes {
        final AtomicInteger accepted = new AtomicInteger();
        final AtomicInteger fallback = new AtomicInteger();
        final AtomicInteger laterInvocations = new AtomicInteger();
        final Pending<Boolean> condition = new Pending<>();
        final Pending<HttpResponse<?>> later = new Pending<>();
        final Pending<HttpResponse<?>> failing = new Pending<>();
        final CompletableFuture<HttpResponse<?>> shared = new CompletableFuture<>();
        final CompletableFuture<Boolean> sharedInvoked = new CompletableFuture<>();
        final CompletableFuture<Boolean> sharedClosed = new CompletableFuture<>();

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/conditions")
                .where(RequestCondition.bodyAsync(String.class, text -> condition.enter()))
                .order(-1).consumes(MediaType.TEXT_PLAIN_TYPE)
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) -> {
                    accepted.incrementAndGet();
                    return HttpResponse.ok("accepted");
                });
            routes.POST("/conditions").consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pathVariables) -> {
                fallback.incrementAndGet();
                return HttpResponse.ok("fallback");
            });
            routes.GET("/later").produces(MediaType.TEXT_PLAIN_TYPE).handleAsync((request, pathVariables) -> {
                laterInvocations.incrementAndGet();
                return later.enter();
            });
            routes.GET("/failing").handleAsync((request, pathVariables) -> failing.enter());
            routes.error(IllegalStateException.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT));
            routes.GET("/shared").produces(MediaType.TEXT_PLAIN_TYPE).handleAsync((request, pathVariables) -> {
                // the server sees the connection of the request closed
                ((NettyHttpRequest<?>) request).getChannelHandlerContext().channel().closeFuture()
                    .addListener(future -> sharedClosed.complete(true));
                sharedInvoked.complete(true);
                return shared(shared);
            });
        }

        /**
         * The stage the requests share, not a copy: cancelling it would cancel it for every request.
         */
        private static CompletionStage<HttpResponse<?>> shared(CompletableFuture<HttpResponse<?>> shared) {
            return shared;
        }
    }
}
