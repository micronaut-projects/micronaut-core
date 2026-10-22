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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.DefaultRouteInfo;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shape of a route result answers the same response with the execution prepared for its
 * route and with the compatibility execution, which reads the route on every request: each case
 * is checked against its own expected response, so that a defect both share does not pass by
 * agreement.
 */
class PreparedRouteExecutionTest {

    private static final String SPEC = "PreparedRouteExecutionTest";
    private static final String LOOP = "default-eventLoopGroup";

    private ApplicationContext ctx;
    private HttpClient httpClient;
    private BlockingHttpClient client;

    private void start(boolean compatibility) {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1));
        ctx.getBean(RouteExecutor.class).forceCompatibility = compatibility;
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
        httpClient = ctx.createBean(HttpClient.class, server.getURL());
        client = httpClient.toBlocking();
    }

    @AfterEach
    void stop() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void values(boolean compatibility) {
        start(compatibility);
        assertBody("/shapes/string", "ok");
        assertBody("/shapes/bean", "{\"name\":\"ok\"}");
        assertStatus("/shapes/null-string", HttpStatus.NOT_FOUND);
        assertEmpty("/shapes/void", HttpStatus.OK);
        assertEmpty("/shapes/status", HttpStatus.ACCEPTED);
        assertEquals(HttpStatus.CREATED, exchange("/shapes/created").status());
        assertBody("/shapes/created", "ok");
        assertBody("/shapes/optional-present", "ok");
        assertStatus("/shapes/optional-empty", HttpStatus.NOT_FOUND);
        assertBody("/shapes/object-string", "ok");
        assertBody("/shapes/object-mono", "ok");
        assertBody("/shapes/object-response", "ok");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responses(boolean compatibility) {
        start(compatibility);
        HttpResponse<String> response = exchange("/shapes/response");
        assertEquals("ok", response.body());
        assertEquals("yes", response.header("X-Explicit"));
        assertEmpty("/shapes/response-no-body", HttpStatus.OK);
        assertBody("/shapes/response-mono", "ok");
        assertStatus("/shapes/response-mono-empty", HttpStatus.NOT_FOUND);
        assertBody("/shapes/response-flux", "[a,b]");
        assertBody("/shapes/response-stage", "ok");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stages(boolean compatibility) {
        start(compatibility);
        assertBody("/shapes/stage", "ok");
        assertBody("/shapes/stage-delayed", "ok");
        assertStatus("/shapes/stage-null", HttpStatus.NOT_FOUND);
        assertEmpty("/shapes/stage-void", HttpStatus.OK);
        assertStatus("/shapes/stage-failed", HttpStatus.I_AM_A_TEAPOT);
        assertBody("/shapes/stage-optional", "ok");
        assertStatus("/shapes/stage-optional-empty", HttpStatus.NOT_FOUND);
        HttpResponse<String> response = exchange("/shapes/stage-response");
        assertEquals("ok", response.body());
        assertEquals("yes", response.header("X-Explicit"));
        assertBody("/shapes/stage-response-stage", "ok");
        assertEmpty("/shapes/stage-status", HttpStatus.ACCEPTED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publishers(boolean compatibility) {
        start(compatibility);
        assertBody("/shapes/mono", "ok");
        assertBody("/shapes/mono-operator", "ok");
        assertBody("/shapes/mono-delayed", "ok");
        assertStatus("/shapes/mono-empty", HttpStatus.NOT_FOUND);
        assertStatus("/shapes/mono-error", HttpStatus.I_AM_A_TEAPOT);
        assertEmpty("/shapes/mono-void", HttpStatus.OK);
        assertBody("/shapes/mono-response", "ok");
        assertBody("/shapes/mono-response-flux", "[a,b]");
        assertBody("/shapes/mono-optional", "ok");
        assertEmpty("/shapes/mono-status", HttpStatus.ACCEPTED);
        // the baseline writes the strings of a stream as they are, unquoted
        assertBody("/shapes/flux", "[a,b]");
        assertBody("/shapes/flux-empty", "[]");
        assertBody("/shapes/flux-single", "[a]");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theRouteIsInvokedAndSubscribedToOnce(boolean compatibility) {
        start(compatibility);
        Counters counters = ctx.getBean(Counters.class);
        for (String path : new String[]{"/shapes/counted-mono", "/shapes/counted-flux", "/shapes/counted-stage", "/shapes/counted-response-mono"}) {
            client.exchange(HttpRequest.GET(path), String.class);
            assertEquals(1, counters.invocations.get(path).get(), path);
            assertEquals(path.endsWith("stage") ? 0 : 1, counters.subscriptions.getOrDefault(path, new AtomicInteger()).get(), path);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void threads(boolean compatibility) {
        start(compatibility);
        assertTrue(client.retrieve("/shapes/thread").startsWith(LOOP));
        assertFalse(client.retrieve("/shapes/blocking-thread").startsWith(LOOP));
        assertFalse(client.retrieve("/shapes/blocking-mono-thread").startsWith(LOOP));
        assertFalse(client.retrieve("/shapes/blocking-flux-thread").contains(LOOP));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void handlers(boolean compatibility) {
        start(compatibility);
        assertBody("/handler", "ok");
        assertBody("/handler-async", "ok");
        assertStatus("/handler-null", HttpStatus.NOT_FOUND);
        assertBody("/located/1/items", "1");
        assertBody("/located/2/items", "2");
        assertFalse(client.retrieve("/located-blocking/1/items").startsWith(LOOP), "the executor of the locator group");
        assertTrue(client.retrieve("/located/1/thread").startsWith(LOOP));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void errorRoutes(boolean compatibility) {
        start(compatibility);
        // a local error route that returns a stage, and a global one of a handler route
        assertStatus("/shapes/throws", HttpStatus.I_AM_A_TEAPOT);
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.GET("/handler-throws"), String.class));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("handled", e.getResponse().getBody(String.class).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theRouteKeepsTheExecutionPreparedForIt(boolean compatibility) {
        start(compatibility);
        RouteExecutor routeExecutor = ctx.getBean(RouteExecutor.class);
        RecordingFilter recorded = ctx.getBean(RecordingFilter.class);
        for (String path : new String[]{"/shapes/string", "/handler", "/located/1/items", "/shapes/throws"}) {
            send(path);
            RouteInfo<?> route = recorded.routes.get(path);
            assertNotNull(route, path);
            Object prepared = ((DefaultRouteInfo<?>) route).findPreparedExecution(routeExecutor);
            if (compatibility) {
                assertNull(prepared, path);
            } else {
                assertNotNull(prepared, path);
                // prepared once
                send(path);
                assertSame(prepared, ((DefaultRouteInfo<?>) recorded.routes.get(path)).findPreparedExecution(routeExecutor), path);
            }
        }
    }

    private void send(String path) {
        try {
            client.exchange(HttpRequest.GET(path), String.class);
        } catch (HttpClientResponseException e) {
            // an error route answered
        }
    }

    private void assertBody(String path, String body) {
        HttpResponse<String> response = exchange(path);
        assertTrue(response.code() < 300, path);
        assertEquals(body, response.body(), path);
    }

    private void assertEmpty(String path, HttpStatus status) {
        HttpResponse<String> response = exchange(path);
        assertEquals(status, response.status(), path);
        assertTrue(response.getBody(String.class).map(String::isEmpty).orElse(true), path);
        assertEquals("0", response.header("Content-Length"), path);
    }

    private void assertStatus(String path, HttpStatus status) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> exchange(path), path);
        assertEquals(status, e.getStatus(), path);
    }

    private HttpResponse<String> exchange(String path) {
        return client.exchange(HttpRequest.GET(path), Argument.STRING, Argument.STRING);
    }

    static final class TeapotException extends RuntimeException {
    }

    record Bean(String name) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class Counters {
        final Map<String, AtomicInteger> invocations = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> subscriptions = new ConcurrentHashMap<>();

        void invoked(String path) {
            invocations.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
        }

        void subscribed(String path) {
            subscriptions.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
        }
    }

    @Controller(value = "/shapes", produces = MediaType.TEXT_PLAIN)
    @Requires(property = "spec.name", value = SPEC)
    static class ShapesController {
        private final Counters counters;
        private final RouteTestTimer timer;

        ShapesController(Counters counters, RouteTestTimer timer) {
            this.counters = counters;
            this.timer = timer;
        }

        @Get("/string")
        String string() {
            return "ok";
        }

        @Get(value = "/bean", produces = MediaType.APPLICATION_JSON)
        Bean bean() {
            return new Bean("ok");
        }

        @Get("/null-string")
        String nullString() {
            return null;
        }

        @Get("/void")
        void none() {
        }

        @Get("/status")
        HttpStatus status() {
            return HttpStatus.ACCEPTED;
        }

        @Get("/created")
        @Status(HttpStatus.CREATED)
        String created() {
            return "ok";
        }

        @Get("/optional-present")
        Optional<String> optionalPresent() {
            return Optional.of("ok");
        }

        @Get("/optional-empty")
        Optional<String> optionalEmpty() {
            return Optional.empty();
        }

        @Get("/object-string")
        Object objectString() {
            return "ok";
        }

        @Get("/object-mono")
        Object objectMono() {
            return Mono.just("ok");
        }

        @Get("/object-response")
        Object objectResponse() {
            return HttpResponse.ok("ok");
        }

        @Get("/response")
        HttpResponse<String> response() {
            return HttpResponse.ok("ok").header("X-Explicit", "yes");
        }

        @Get("/response-no-body")
        HttpResponse<String> responseNoBody() {
            return HttpResponse.ok();
        }

        @Get("/response-mono")
        HttpResponse<Mono<String>> responseMono() {
            return HttpResponse.ok(Mono.just("ok"));
        }

        @Get("/response-mono-empty")
        HttpResponse<Mono<String>> responseMonoEmpty() {
            return HttpResponse.ok(Mono.empty());
        }

        @Get(value = "/response-flux", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Flux<String>> responseFlux() {
            return HttpResponse.ok(Flux.just("a", "b"));
        }

        @Get("/response-stage")
        HttpResponse<CompletionStage<String>> responseStage() {
            return HttpResponse.ok(CompletableFuture.completedFuture("ok"));
        }

        @Get("/stage")
        CompletionStage<String> stage() {
            return CompletableFuture.completedFuture("ok");
        }

        @Get("/stage-delayed")
        CompletionStage<String> stageDelayed() {
            return timer.later("ok").toFuture();
        }

        @Get("/stage-null")
        CompletionStage<String> stageNull() {
            return CompletableFuture.completedFuture(null);
        }

        @Get("/stage-void")
        CompletionStage<Void> stageVoid() {
            return CompletableFuture.completedFuture(null);
        }

        @Get("/stage-failed")
        CompletionStage<String> stageFailed() {
            return CompletableFuture.failedFuture(new TeapotException());
        }

        @Get("/stage-optional")
        CompletionStage<Optional<String>> stageOptional() {
            return CompletableFuture.completedFuture(Optional.of("ok"));
        }

        @Get("/stage-optional-empty")
        CompletionStage<Optional<String>> stageOptionalEmpty() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Get("/stage-response")
        CompletionStage<HttpResponse<String>> stageResponse() {
            return CompletableFuture.completedFuture(HttpResponse.ok("ok").header("X-Explicit", "yes"));
        }

        @Get("/stage-response-stage")
        CompletionStage<HttpResponse<CompletionStage<String>>> stageResponseStage() {
            return CompletableFuture.completedFuture(HttpResponse.ok(CompletableFuture.completedFuture("ok")));
        }

        @Get("/stage-status")
        CompletionStage<HttpStatus> stageStatus() {
            return CompletableFuture.completedFuture(HttpStatus.ACCEPTED);
        }

        @Get("/mono")
        Mono<String> mono() {
            return Mono.just("ok");
        }

        @Get("/mono-operator")
        Mono<String> monoOperator() {
            return Mono.just("o").map(o -> o + "k");
        }

        @Get("/mono-delayed")
        Mono<String> monoDelayed() {
            return timer.later("ok");
        }

        @Get("/mono-empty")
        Mono<String> monoEmpty() {
            return Mono.empty();
        }

        @Get("/mono-error")
        Mono<String> monoError() {
            return Mono.error(new TeapotException());
        }

        @Get("/mono-void")
        Mono<Void> monoVoid() {
            return Mono.empty();
        }

        @Get("/mono-response")
        Mono<HttpResponse<String>> monoResponse() {
            return Mono.just(HttpResponse.ok("ok"));
        }

        @Get(value = "/mono-response-flux", produces = MediaType.APPLICATION_JSON)
        Mono<HttpResponse<Flux<String>>> monoResponseFlux() {
            return Mono.just(HttpResponse.ok(Flux.just("a", "b")));
        }

        @Get("/mono-optional")
        Mono<Optional<String>> monoOptional() {
            return Mono.just(Optional.of("ok"));
        }

        @Get("/mono-status")
        Mono<HttpStatus> monoStatus() {
            return Mono.just(HttpStatus.ACCEPTED);
        }

        @Get(value = "/flux", produces = MediaType.APPLICATION_JSON)
        Flux<String> flux() {
            return Flux.just("a", "b");
        }

        @Get(value = "/flux-empty", produces = MediaType.APPLICATION_JSON)
        Flux<String> fluxEmpty() {
            return Flux.empty();
        }

        @Get(value = "/flux-single", produces = MediaType.APPLICATION_JSON)
        Flux<String> fluxSingle() {
            return Flux.just("a");
        }

        @Get("/counted-mono")
        Mono<String> countedMono() {
            counters.invoked("/shapes/counted-mono");
            return Mono.just("o").map(o -> o + "k").doOnSubscribe(s -> counters.subscribed("/shapes/counted-mono"));
        }

        @Get(value = "/counted-flux", produces = MediaType.APPLICATION_JSON)
        Flux<String> countedFlux() {
            counters.invoked("/shapes/counted-flux");
            return Flux.just("a", "b").doOnSubscribe(s -> counters.subscribed("/shapes/counted-flux"));
        }

        @Get("/counted-stage")
        CompletionStage<String> countedStage() {
            counters.invoked("/shapes/counted-stage");
            return CompletableFuture.completedFuture("ok");
        }

        @Get("/counted-response-mono")
        HttpResponse<Mono<String>> countedResponseMono() {
            counters.invoked("/shapes/counted-response-mono");
            return HttpResponse.ok(Mono.just("o").map(o -> o + "k").doOnSubscribe(s -> counters.subscribed("/shapes/counted-response-mono")));
        }

        @Get("/thread")
        String thread() {
            return Thread.currentThread().getName();
        }

        @Get("/blocking-thread")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String blockingThread() {
            return Thread.currentThread().getName();
        }

        @Get("/blocking-mono-thread")
        @ExecuteOn(TaskExecutors.BLOCKING)
        Mono<String> blockingMonoThread() {
            return Mono.fromCallable(() -> Thread.currentThread().getName());
        }

        @Get(value = "/blocking-flux-thread", produces = MediaType.APPLICATION_JSON)
        @ExecuteOn(TaskExecutors.BLOCKING)
        Flux<String> blockingFluxThread() {
            return Flux.defer(() -> Flux.just(Thread.currentThread().getName()));
        }

        @Get("/throws")
        String throwsTeapot() {
            throw new IllegalStateException("local");
        }

        @Error(IllegalStateException.class)
        CompletionStage<HttpResponse<String>> local(IllegalStateException e) {
            return CompletableFuture.completedFuture(HttpResponse.<String>status(HttpStatus.I_AM_A_TEAPOT).body("local"));
        }

        @Error(exception = TeapotException.class, global = true)
        HttpResponse<String> teapot() {
            return HttpResponse.status(HttpStatus.I_AM_A_TEAPOT);
        }
    }

    /**
     * Records the routes the responses were answered by.
     */
    @Requires(property = "spec.name", value = SPEC)
    @ServerFilter("/**")
    static final class RecordingFilter {
        final Map<String, RouteInfo<?>> routes = new ConcurrentHashMap<>();

        @ResponseFilter
        void record(HttpRequest<?> request, MutableHttpResponse<?> response) {
            RouteAttributes.getRouteInfo(request).ifPresent(route -> routes.put(request.getPath(), route));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/handler").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pathVariables) -> HttpResponse.ok("ok"));
            routes.GET("/handler-async").produces(MediaType.TEXT_PLAIN_TYPE)
                .handleAsync((request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok("ok")));
            routes.GET("/handler-null", (request, pathVariables) -> null);
            routes.GET("/handler-throws", (request, pathVariables) -> {
                throw new UnsupportedOperationException("handler");
            });
            routes.error(UnsupportedOperationException.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT).body("handled"));
            LocatedRoutes<String> items = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> located) {
                    located.GET("/items").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pathVariables, target) -> HttpResponse.ok(target));
                    located.GET("/thread").produces(MediaType.TEXT_PLAIN_TYPE)
                        .handle((request, pathVariables, target) -> HttpResponse.ok(Thread.currentThread().getName()));
                }
            };
            LocatedRoutes<String> threads = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> located) {
                    located.GET("/items").produces(MediaType.TEXT_PLAIN_TYPE)
                        .handle((request, pathVariables, target) -> HttpResponse.ok(Thread.currentThread().getName()));
                }
            };
            routes.locate("/located/{id}", (request, pathVariables) -> pathVariables.getString("id"), target -> items);
            routes.path("/located-blocking", group -> {
                group.executeOn(TaskExecutors.BLOCKING);
                group.locate("/{id}", (request, pathVariables) -> pathVariables.getString("id"), target -> threads);
            });
        }
    }
}
