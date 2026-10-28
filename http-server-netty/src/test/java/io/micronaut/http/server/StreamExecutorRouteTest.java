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
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The streamed body of a route is subscribed to on the executor of the route, the one its
 * invocation runs on, whether the executor is declared on the method, on the route or on its
 * group, with the execution prepared for the route and with the compatibility execution.
 */
class StreamExecutorRouteTest {

    private static final String SPEC = "StreamExecutorRouteTest";
    private static final String LOOP = "default-eventLoopGroup";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theStreamRunsOnTheExecutorOfTheRoute(boolean compatibility) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1))) {
            ctx.getBean(RouteExecutor.class).forceCompatibility = compatibility;
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                for (String path : List.of("/stream/annotated", "/stream/route", "/stream/group/body",
                    "/stream/annotated-delayed", "/stream/route-delayed",
                    "/stream/filtered/annotated", "/stream/filtered/route")) {
                    // the strings of a stream are written unquoted: the body is [invoked on,streamed on]
                    String body = client.toBlocking().retrieve(HttpRequest.GET(path), String.class);
                    String[] threads = body.substring(1, body.length() - 1).split(",");
                    assertFalse(threads[1].contains(LOOP), path + ": " + body);
                    // a response completed later, by a timer, e.g. after an asynchronous
                    // filter, streams on the executor that invoked the route too
                    assertEquals(pool(threads[0]), pool(threads[1]), path + ": " + body);
                }
                String plain = client.toBlocking().retrieve(HttpRequest.GET("/stream/plain"), String.class);
                assertTrue(plain.substring(plain.indexOf(',')).contains(LOOP), "a route without an executor streams on the event loop: " + plain);
            }
        }
    }

    /**
     * A response completed later, on the thread of the timer.
     */
    private static CompletableFuture<HttpResponse<Flux<String>>> delayed(RouteTestTimer timer) {
        return timer.later("tick").<HttpResponse<Flux<String>>>map(tick -> HttpResponse.ok(thread())).toFuture();
    }

    /**
     * @return The thread that invokes the route, then the thread that subscribes to the stream
     */
    private static Flux<String> thread() {
        String invoked = Thread.currentThread().getName();
        return Flux.defer(() -> Flux.just(invoked, Thread.currentThread().getName()));
    }

    /**
     * @param thread The name of a thread, e.g. {@code io-executor-thread-2}
     * @return The name of its pool, e.g. {@code io-executor-thread}
     */
    private static String pool(String thread) {
        return thread.replaceAll("[-#]?\\d+$", "");
    }

    @Controller("/stream")
    @Requires(property = "spec.name", value = SPEC)
    static class StreamController {
        private final RouteTestTimer timer;

        StreamController(RouteTestTimer timer) {
            this.timer = timer;
        }

        @Get(value = "/annotated", produces = MediaType.APPLICATION_JSON)
        @ExecuteOn(TaskExecutors.BLOCKING)
        HttpResponse<Flux<String>> annotated() {
            return HttpResponse.ok(thread());
        }

        @Get(value = "/annotated-delayed", produces = MediaType.APPLICATION_JSON)
        @ExecuteOn(TaskExecutors.BLOCKING)
        CompletionStage<HttpResponse<Flux<String>>> annotatedDelayed() {
            return delayed(timer);
        }

        @Get(value = "/filtered/annotated", produces = MediaType.APPLICATION_JSON)
        @ExecuteOn(TaskExecutors.BLOCKING)
        HttpResponse<Flux<String>> filteredAnnotated() {
            return HttpResponse.ok(thread());
        }

        @Get(value = "/plain", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Flux<String>> plain() {
            return HttpResponse.ok(thread());
        }
    }

    /**
     * Completes the response later, on the thread of the timer, so the writer subscribes to the
     * streamed body on that thread.
     */
    @ServerFilter("/stream/filtered/**")
    @Requires(property = "spec.name", value = SPEC)
    static final class DelayingFilter {
        private final RouteTestTimer timer;

        DelayingFilter(RouteTestTimer timer) {
            this.timer = timer;
        }

        @ResponseFilter
        Publisher<MutableHttpResponse<?>> delay(MutableHttpResponse<?> response) {
            return timer.later(response);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class Routes implements HttpRoutes {
        private final RouteTestTimer timer;

        Routes(RouteTestTimer timer) {
            this.timer = timer;
        }

        @Override
        public void routes(io.micronaut.web.router.builder.HttpRouteBuilder routes) {
            routes.GET("/stream/route").produces(MediaType.APPLICATION_JSON_TYPE).executeOn(TaskExecutors.BLOCKING)
                .handle((request, pathVariables) -> HttpResponse.ok(thread()));
            routes.GET("/stream/route-delayed").produces(MediaType.APPLICATION_JSON_TYPE).executeOn(TaskExecutors.BLOCKING)
                .handleAsync((request, pathVariables) -> delayed(timer));
            // the route declares its streamed body, which the server streams on the route executor
            routes.GET("/stream/filtered/route").produces(MediaType.APPLICATION_JSON_TYPE).executeOn(TaskExecutors.BLOCKING)
                .responseType(Argument.of(Flux.class, Argument.STRING))
                .handle((request, pathVariables) -> HttpResponse.ok(thread()));
            routes.path("/stream/group", group -> {
                group.executeOn(TaskExecutors.BLOCKING);
                group.GET("/body").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok(thread()));
            });
        }
    }
}
