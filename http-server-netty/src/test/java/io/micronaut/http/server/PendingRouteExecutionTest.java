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
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Body conditions and route results that complete after the routing returned, with the
 * execution prepared for the route and with the compatibility execution: each route is invoked
 * once, a rejected or failed condition falls through or fails like before, and a shared stage a
 * route returns is not cancelled when the client abandons the request.
 */
class PendingRouteExecutionTest {

    private static final String SPEC = "PendingRouteExecutionTest";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingWork(boolean compatibility) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1))) {
            ctx.getBean(RouteExecutor.class).forceCompatibility = compatibility;
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            Routes routes = ctx.getBean(Routes.class);
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                // a condition accepted later selects its route, invoked once
                assertEquals("accepted", client.toBlocking().retrieve(HttpRequest.POST("/conditions", "accept").contentType(MediaType.TEXT_PLAIN_TYPE)));
                assertEquals(1, routes.accepted.get());
                // a condition rejected later falls through to the route without conditions
                assertEquals("fallback", client.toBlocking().retrieve(HttpRequest.POST("/conditions", "reject").contentType(MediaType.TEXT_PLAIN_TYPE)));
                assertEquals(1, routes.accepted.get());
                assertEquals(1, routes.fallback.get());

                // a stage completed later answers once
                assertEquals("later", client.toBlocking().retrieve(HttpRequest.GET("/later")));
                assertEquals(1, routes.later.get());
                // a stage that fails later is answered by the error route
                HttpClientResponseException failed = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET("/failing"), String.class));
                assertEquals(HttpStatus.CONFLICT, failed.getStatus());

                // an abandoned request does not cancel the shared stage its route returned
                Mono<?> abandoned = Mono.from(client.exchange(HttpRequest.GET("/shared"), String.class))
                    .timeout(Duration.ofMillis(200))
                    .onErrorResume(e -> Mono.empty());
                abandoned.block(Duration.ofSeconds(10));
                assertTrue(routes.sharedInvoked.await(), "the route was invoked");
                assertFalse(routes.shared.isCancelled());
                assertFalse(routes.shared.isDone());
                routes.shared.complete(HttpResponse.ok("shared"));
                assertEquals("shared", client.toBlocking().retrieve(HttpRequest.GET("/shared")));
            }
        }
    }

    static final class Invoked {
        private final CompletableFuture<Boolean> invoked = new CompletableFuture<>();

        void signal() {
            invoked.complete(true);
        }

        boolean await() throws Exception {
            return invoked.get(10, TimeUnit.SECONDS);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class Routes implements HttpRoutes {
        final AtomicInteger accepted = new AtomicInteger();
        final AtomicInteger fallback = new AtomicInteger();
        final AtomicInteger later = new AtomicInteger();
        final CompletableFuture<HttpResponse<?>> shared = new CompletableFuture<>();
        final Invoked sharedInvoked = new Invoked();
        private final RouteTestTimer timer;

        Routes(RouteTestTimer timer) {
            this.timer = timer;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/conditions")
                .where(RequestCondition.bodyAsync(String.class, text -> timer.later(text.equals("accept")).toFuture()))
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
                later.incrementAndGet();
                return timer.<HttpResponse<?>>later(HttpResponse.ok("later")).toFuture();
            });
            routes.GET("/failing").handleAsync((request, pathVariables) -> timer.later("x")
                .<HttpResponse<?>>flatMap(x -> Mono.error(new IllegalStateException("later")))
                .toFuture());
            routes.error(IllegalStateException.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT));
            routes.GET("/shared").produces(MediaType.TEXT_PLAIN_TYPE).handleAsync((request, pathVariables) -> {
                sharedInvoked.signal();
                return shared(shared);
            });
        }

        /**
         * The stage the routes share, not a copy: cancelling it would cancel it for every route.
         */
        private static CompletionStage<HttpResponse<?>> shared(CompletableFuture<HttpResponse<?>> shared) {
            return shared;
        }
    }
}
