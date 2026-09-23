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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Asynchronous error and status handler functions answer like the synchronous ones: the same
 * errors of controller routes, synchronous handler routes and asynchronous handler routes (thrown
 * by the handler, or completing its stage) reach them, and one that fails is answered with the
 * default error response.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesAsyncErrorsTest {
    public static final String SPEC_NAME = "HandlerRoutesAsyncErrorsTest";
    private static final String EXECUTOR = "async-errors";

    /**
     * Each error is raised by a controller method, a handler function, an asynchronous handler
     * function that throws, and the stage of an asynchronous handler function.
     */
    private static final List<String> SOURCES = List.of(
        "/async-errors/controller",
        "/async-errors/handler",
        "/async-errors/async-thrown",
        "/async-errors/async-stage"
    );

    @Test
    void asyncErrorRouteAnswersLikeASyncErrorRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                // the synchronous error route
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/fail/sync-handled"), HttpResponseAssertion.builder()
                    .status(HttpStatus.I_AM_A_TEAPOT)
                    .body("handled sync-handled failure")
                    .build());
                // the asynchronous error route, completed later on another thread
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/fail/async-handled"), HttpResponseAssertion.builder()
                    .status(HttpStatus.I_AM_A_TEAPOT)
                    .body("handled async-handled failure")
                    .build());
            }
        }
    }

    @Test
    void asyncErrorRouteOfASupertypeHandlesTheSubtype() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/fail/async-subtype"), HttpResponseAssertion.builder()
                    .status(HttpStatus.I_AM_A_TEAPOT)
                    .body("handled async-subtype failure")
                    .build());
            }
        }
    }

    @Test
    void failingErrorRouteIsAnsweredWithTheDefaultErrorResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                // a synchronous error route that throws, an asynchronous one that throws, and one whose stage fails
                for (String failure : List.of("/sync-failing", "/async-throwing", "/async-failing")) {
                    AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/fail" + failure), HttpResponseAssertion.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Internal Server Error")
                        .build());
                }
            }
        }
    }

    @Test
    void asyncStatusRouteAnswersAMissingRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/async-errors/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .body("no route for /async-errors/missing")
                .build());
        }
    }

    @Test
    void asyncStatusRouteAnswersTheStatusOfARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String source : SOURCES) {
                // answered by the synchronous status route, then by the asynchronous one
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/status/conflict"), HttpResponseAssertion.builder()
                    .status(HttpStatus.CONFLICT)
                    .body("sync conflict status")
                    .build());
                AssertionUtils.assertThrows(server, HttpRequest.GET(source + "/status/locked"), HttpResponseAssertion.builder()
                    .status(HttpStatus.LOCKED)
                    .body("async locked status")
                    .build());
            }
        }
    }

    @Test
    void failingAsyncStatusRouteIsAnInternalServerError() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/async-errors/handler/gone"), HttpResponseAssertion.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal Server Error")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    /**
     * The failure of each path.
     *
     * @param name The last segment of the path
     * @return The failure
     */
    static RuntimeException failure(String name) {
        return switch (name) {
            case "sync-handled" -> new SyncHandledFailure();
            case "async-handled" -> new AsyncHandledFailure();
            case "async-subtype" -> new AsyncHandledSubtype();
            case "sync-failing" -> new SyncFailingRouteFailure();
            case "async-throwing" -> new AsyncThrowingRouteFailure();
            case "async-failing" -> new AsyncFailingRouteFailure();
            default -> new IllegalArgumentException(name);
        };
    }

    /**
     * The response of a path that answers a status.
     *
     * @param name The last segment of the path
     * @return The response
     */
    static HttpResponse<?> statusResponse(String name) {
        return HttpResponse.status("conflict".equals(name) ? HttpStatus.CONFLICT : HttpStatus.LOCKED);
    }

    /**
     * Completes later on an executor of the application, like a service call would.
     */
    private static <T> CompletableFuture<T> later(ExecutorService executor, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    static class Failure extends RuntimeException {
        Failure(String message) {
            super(message);
        }
    }

    static final class SyncHandledFailure extends Failure {
        SyncHandledFailure() {
            super("sync-handled failure");
        }
    }

    static final class AsyncHandledFailure extends Failure {
        AsyncHandledFailure() {
            super("async-handled failure");
        }
    }

    static class AsyncHandledSupertype extends Failure {
        AsyncHandledSupertype(String message) {
            super(message);
        }
    }

    static final class AsyncHandledSubtype extends AsyncHandledSupertype {
        AsyncHandledSubtype() {
            super("async-subtype failure");
        }
    }

    static final class SyncFailingRouteFailure extends Failure {
        SyncFailingRouteFailure() {
            super("sync-failing failure");
        }
    }

    static final class AsyncThrowingRouteFailure extends Failure {
        AsyncThrowingRouteFailure() {
            super("async-throwing failure");
        }
    }

    static final class AsyncFailingRouteFailure extends Failure {
        AsyncFailingRouteFailure() {
            super("async-failing failure");
        }
    }

    @Controller("/async-errors/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ThrowingController {
        @Get("/fail/{name}")
        String fail(String name) {
            throw failure(name);
        }

        @Get("/status/{name}")
        HttpResponse<?> status(String name) {
            return statusResponse(name);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final ExecutorService executor;

        Routes(@Named(EXECUTOR) ExecutorService executor) {
            this.executor = executor;
        }

        private <T> CompletableFuture<T> later(Supplier<T> supplier) {
            return HandlerRoutesAsyncErrorsTest.later(executor, supplier);
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/async-errors/handler/fail/{name}", (request, pathVariables) -> {
                throw failure(pathVariables.getString("name"));
            });
            routes.GET("/async-errors/handler/status/{name}", (request, pathVariables) -> statusResponse(pathVariables.getString("name")));
            routes.GET("/async-errors/handler/gone", (request, pathVariables) -> HttpResponse.status(HttpStatus.GONE));
            routes.asyncGET("/async-errors/async-thrown/fail/{name}", (request, pathVariables) -> {
                throw failure(pathVariables.getString("name"));
            });
            routes.asyncGET("/async-errors/async-thrown/status/{name}", (request, pathVariables) ->
                CompletableFuture.completedFuture(statusResponse(pathVariables.getString("name"))));
            routes.asyncGET("/async-errors/async-stage/fail/{name}", (request, pathVariables) ->
                later(() -> {
                    throw failure(pathVariables.getString("name"));
                }));
            routes.asyncGET("/async-errors/async-stage/status/{name}", (request, pathVariables) ->
                later(() -> statusResponse(pathVariables.getString("name"))));

            routes.error(SyncHandledFailure.class, (request, error) -> handled(error));
            routes.errorAsync(AsyncHandledFailure.class, (request, error) -> later(() -> handled(error)));
            routes.errorAsync(AsyncHandledSupertype.class, (request, error) -> CompletableFuture.completedFuture(handled(error)));
            routes.error(SyncFailingRouteFailure.class, (request, error) -> {
                throw new IllegalStateException("the error route failed");
            });
            routes.errorAsync(AsyncThrowingRouteFailure.class, (request, error) -> {
                throw new IllegalStateException("the error route failed");
            });
            routes.errorAsync(AsyncFailingRouteFailure.class, (request, error) ->
                later(() -> {
                    throw new IllegalStateException("the error route failed");
                }));

            routes.statusAsync(HttpStatus.NOT_FOUND, request ->
                later(() -> text(HttpStatus.NOT_FOUND, "no route for " + request.getPath())));
            routes.status(HttpStatus.CONFLICT, request -> text(HttpStatus.CONFLICT, "sync conflict status"));
            routes.statusAsync(HttpStatus.LOCKED, request -> later(() -> text(HttpStatus.LOCKED, "async locked status")));
            routes.statusAsync(HttpStatus.GONE, request -> later(() -> {
                throw new IllegalStateException("the status route failed");
            }));
        }

        private static HttpResponse<?> handled(Throwable error) {
            return text(HttpStatus.I_AM_A_TEAPOT, "handled " + error.getMessage());
        }

        private static HttpResponse<?> text(HttpStatus status, String body) {
            return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Executors {
        @Singleton
        @Named(EXECUTOR)
        @Bean(preDestroy = "shutdown")
        ExecutorService asyncErrorsExecutor() {
            return java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "async-errors-thread"));
        }
    }
}
