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
package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asynchronous error and status handler functions are error and status routes like the
 * synchronous ones: selected the same way, returning a {@link CompletionStage} like an
 * {@code @Error} method that returns one, and run without an executor, like every error route.
 */
class AsyncErrorRoutesTest {

    private static final HttpRequest<?> REQUEST = HttpRequest.GET("/missing");

    @Test
    void asyncErrorRouteIsAnAsyncErrorRoute() throws Exception {
        Router router = router(routes -> routes.errorAsync(NoSuchFileException.class, (request, error) ->
            CompletableFuture.completedFuture(HttpResponse.notFound(request.getPath() + " " + error.getMessage()))));

        NoSuchFileException error = new NoSuchFileException("a.txt");
        RouteMatch<Object> match = router.<Object>findErrorRoute(error, REQUEST).orElseThrow();
        RouteInfo<Object> routeInfo = match.getRouteInfo();
        assertTrue(routeInfo.isErrorRoute());
        assertTrue(routeInfo.isAsync());
        assertFalse(routeInfo.isImperative());
        assertNull(routeInfo.getExecutor(ThreadSelection.AUTO), "an error route runs on the thread that handles the error");

        HttpResponse<?> response = execute(match, error);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertEquals("/missing a.txt", response.body());
    }

    @Test
    void asyncErrorRouteHandlesSubtypesAndTheClosestTypeWins() throws Exception {
        Router router = router(routes -> {
            routes.errorAsync(IOException.class, (request, error) -> CompletableFuture.completedFuture(HttpResponse.ok("io")));
            routes.error(NoSuchFileException.class, (request, error) -> HttpResponse.ok("sync no such file"));
            routes.errorAsync(IllegalStateException.class, (request, error) -> CompletableFuture.completedFuture(HttpResponse.ok("async state")));
            routes.error(RuntimeException.class, (request, error) -> HttpResponse.ok("sync runtime"));
        });

        assertEquals("io", handle(router, new EOFException()).body());
        assertEquals("sync no such file", handle(router, new NoSuchFileException("x")).body());
        assertEquals("async state", handle(router, new IllegalStateException()).body());
        assertEquals("sync runtime", handle(router, new UncheckedIOException(new IOException())).body());
    }

    @Test
    void asyncErrorRouteProduces() {
        Router router = router(routes -> routes.errorAsync(IOException.class, (request, error) ->
            CompletableFuture.completedFuture(HttpResponse.ok("io"))).produces(MediaType.TEXT_PLAIN_TYPE));

        RouteMatch<Object> match = router.<Object>findErrorRoute(new IOException(), REQUEST).orElseThrow();
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), match.getRouteInfo().getProduces());
    }

    @Test
    void asyncStatusRouteIsAnAsyncStatusRoute() throws Exception {
        Router router = router(routes -> {
            routes.statusAsync(HttpStatus.NOT_FOUND, request ->
                CompletableFuture.completedFuture(HttpResponse.notFound("no route for " + request.getPath())))
                .produces(MediaType.TEXT_PLAIN_TYPE);
            routes.statusAsync(HttpStatus.I_AM_A_TEAPOT, request ->
                CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.I_AM_A_TEAPOT).body("teapot")));
        });

        RouteMatch<Object> match = router.<Object>findStatusRoute(HttpStatus.NOT_FOUND, REQUEST).orElseThrow();
        RouteInfo<Object> routeInfo = match.getRouteInfo();
        assertTrue(routeInfo.isErrorRoute());
        assertTrue(routeInfo.isAsync());
        assertNull(routeInfo.getExecutor(ThreadSelection.AUTO));
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), routeInfo.getProduces());
        assertEquals("no route for /missing", execute(match, null).body());

        assertEquals("teapot", execute(router.<Object>findStatusRoute(HttpStatus.I_AM_A_TEAPOT, REQUEST).orElseThrow(), null).body());
        assertTrue(router.findStatusRoute(HttpStatus.CONFLICT, REQUEST).isEmpty());
    }

    @Test
    void asyncErrorRouteThatThrowsOrAnswersNoStageFails() {
        Router router = router(routes -> {
            routes.errorAsync(IOException.class, (request, error) -> {
                throw new IllegalStateException("handler failed");
            });
            routes.errorAsync(IllegalArgumentException.class, (request, error) -> null);
            routes.statusAsync(HttpStatus.NOT_FOUND, request -> null);
        });

        assertEquals("handler failed", assertThrows(IllegalStateException.class, () -> handle(router, new IOException())).getMessage());
        assertThrows(NullPointerException.class, () -> handle(router, new IllegalArgumentException()));
        RouteMatch<Object> noStatusStage = router.<Object>findStatusRoute(HttpStatus.NOT_FOUND, REQUEST).orElseThrow();
        assertThrows(NullPointerException.class, () -> execute(noStatusStage, null));
    }

    @Test
    void asyncErrorRouteReturnsTheStageOfTheHandler() {
        CompletableFuture<HttpResponse<?>> later = new CompletableFuture<>();
        Router router = router(routes -> routes.errorAsync(IOException.class, (request, error) -> later));

        IOException error = new IOException();
        RouteMatch<Object> match = router.<Object>findErrorRoute(error, REQUEST).orElseThrow();
        Object result = fulfilled(match, error).execute();
        assertSame(later, result, "the stage is completed by the handler, not by the route");
    }

    private static HttpResponse<?> handle(Router router, Throwable error) throws Exception {
        return execute(router.<Object>findErrorRoute(error, REQUEST).orElseThrow(), error);
    }

    private static HttpResponse<?> execute(RouteMatch<Object> match, @Nullable Throwable error) throws Exception {
        Object result = fulfilled(match, error).execute();
        if (!match.getRouteInfo().isAsync()) {
            // a synchronous error route
            return assertInstanceOf(HttpResponse.class, result);
        }
        CompletionStage<?> stage = assertInstanceOf(CompletionStage.class, result);
        return assertInstanceOf(HttpResponse.class, stage.toCompletableFuture().get());
    }

    /**
     * Bind the arguments like the server does: the request, and the exception of an error route.
     */
    private static RouteMatch<Object> fulfilled(RouteMatch<Object> match, @Nullable Throwable error) {
        match.fulfill(error == null ? Map.of("request", REQUEST) : Map.of("request", REQUEST, "error", error));
        return match;
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
