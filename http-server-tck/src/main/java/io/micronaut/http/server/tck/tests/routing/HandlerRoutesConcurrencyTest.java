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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handler routes under concurrent requests: the route, group and server filters, the handlers and the asynchronous locators are shared by
 * every request, and no request may see the values of another.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesConcurrencyTest {
    public static final String SPEC_NAME = "HandlerRoutesConcurrencyTest";

    private static final int THREADS = 16;
    private static final int REQUESTS_PER_THREAD = 40;
    private static final String ID = "X-Id";

    @Test
    void concurrentRequestsSeeOnlyTheirOwnValues() throws Exception {
        try (ServerUnderTest server = server()) {
            List<String> failures = runConcurrently(THREADS, REQUESTS_PER_THREAD, (thread, n) -> {
                String id = thread + "-" + n;
                int kind = ThreadLocalRandom.current().nextInt(5);
                HttpRequest<?> request = switch (kind) {
                    case 0 -> HttpRequest.GET("/conc/sync/" + id);
                    case 1 -> HttpRequest.GET("/conc/async/" + id);
                    case 2 -> HttpRequest.POST("/conc/echo/" + id, "body of " + id).contentType(MediaType.TEXT_PLAIN_TYPE);
                    case 3 -> HttpRequest.GET("/conc/located/" + id + "/show");
                    default -> HttpRequest.GET("/conc/context/" + id);
                };
                String expectedBody = switch (kind) {
                    case 0 -> "sync " + id;
                    case 1 -> "async " + id;
                    case 2 -> "echo " + id + ": body of " + id;
                    case 3 -> "located " + id;
                    default -> "context " + id;
                };
                HttpResponse<String> response = server.exchange(((MutableHttpRequest<?>) request).header(ID, id), String.class);
                List<String> problems = new ArrayList<>();
                if (response.getStatus() != HttpStatus.OK) {
                    problems.add("status " + response.getStatus());
                }
                if (!expectedBody.equals(response.body())) {
                    problems.add("body '" + response.body() + "' expected '" + expectedBody + "'");
                }
                for (String header : List.of("X-Server-Filter", "X-Group-Filter", "X-Route-Filter")) {
                    if (!id.equals(response.getHeaders().get(header))) {
                        problems.add(header + "=" + response.getHeaders().get(header));
                    }
                }
                return problems.isEmpty() ? null : request.getPath() + ": " + String.join(", ", problems);
            });
            assertTrue(failures.isEmpty(), () -> failures.size() + " failures, first: " + failures.subList(0, Math.min(5, failures.size())));
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static List<String> runConcurrently(int threads, int requestsPerThread, Call call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int n = 0; n < requestsPerThread; n++) {
                        try {
                            String failure = call.call(thread, n);
                            if (failure != null) {
                                failures.add(failure);
                            }
                        } catch (Exception e) {
                            failures.add(e.toString());
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return new ArrayList<>(failures);
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static String id(HttpRequest<?> request) {
        return Objects.requireNonNullElse(request.getHeaders().get(ID), "none");
    }

    @FunctionalInterface
    interface Call {
        String call(int thread, int n) throws Exception;
    }

    record RequestId(String id) implements PropagatedContextElement {
    }

    record Located(String id) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ConcurrentRoutes implements HttpRoutes {
        private final ExecutorService executor;

        ConcurrentRoutes(@Named(TaskExecutors.IO) ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.filter("/conc/**").before((request, propagatedContext) -> {
                propagatedContext.add(new RequestId(id(request)));
            }).and().after((request, response) -> response.header("X-Server-Filter", PropagatedContext.getOrEmpty().find(RequestId.class).map(RequestId::id).orElse("none")));
            LocatedRoutes<Located> located = TckLocatedRoutes.of(Located.class, table -> table.GET("/show", (request, pathVariables) ->
                    text("located " + LocatedRoutes.locatedTarget(pathVariables, Located.class).id()))
                .after((request, response) -> response.header("X-Route-Filter", pathVariables(request))));
            routes.path("/conc", group -> {
                group.beforeAsync(request -> CompletableFuture.supplyAsync(() -> {
                    request.setAttribute("group-id", id(request));
                    return null;
                }, executor));
                group.after((request, response) -> response.header("X-Group-Filter", request.getAttribute("group-id", String.class).orElse("none")));
                group.GET("/sync/{id}", (request, pathVariables) -> text("sync " + pathVariables.getString("id")))
                    .beforeReplacing(request -> id(request).equals(pathVariables(request)) ? null : HttpResponse.badRequest())
                    .and()
                    .after((request, response) -> response.header("X-Route-Filter", id(request)));
                group.asyncGET("/async/{id}", (request, pathVariables) -> CompletableFuture.supplyAsync(() -> text("async " + pathVariables.getString("id")), executor))
                    .after((request, response) -> response.header("X-Route-Filter", id(request))).executeOn(TaskExecutors.BLOCKING);
                group.asyncPOST("/echo/{id}", (request, pathVariables, body) -> body.text()
                        .thenApply(value -> text("echo " + pathVariables.getString("id") + ": " + value)))
                    .consumes(MediaType.TEXT_PLAIN_TYPE)
                    .afterAsync((request, response) -> CompletableFuture.runAsync(() -> response.header("X-Route-Filter", id(request)), executor));
                group.locateAsync("/located/{id}", (request, pathVariables) -> {
                    String id = pathVariables.getString("id");
                    return CompletableFuture.supplyAsync(() -> new Located(id), executor);
                }, located);
                group.GET("/context/{id}", (request, pathVariables) ->
                        text("context " + PropagatedContext.getOrEmpty().find(RequestId.class).map(RequestId::id).orElse("none")))
                    .after((request, response) -> response.header("X-Route-Filter", id(request)));
            });
        }

        /**
         * The third segment of the path: the id of {@code /conc/sync/{id}} and {@code /conc/located/{id}/show}.
         */
        private static String pathVariables(HttpRequest<?> request) {
            String path = request.getPath();
            int start = path.indexOf('/', "/conc/".length()) + 1;
            int end = path.indexOf('/', start);
            return end < 0 ? path.substring(start) : path.substring(start, end);
        }
    }
}
