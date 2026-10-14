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
package io.micronaut.web.router.builder;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.PathVariables;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An asynchronous handler or filter that returns no stage fails with a message that says so,
 * whatever the request, and the body the handler read is released when the handler fails.
 */
class NoStageTest {

    private static final Argument<AsyncRequestBody> ASYNC_BODY = Argument.of(AsyncRequestBody.class);

    @Test
    void anAsynchronousHandlerThatReturnsNoStageFailsWhateverTheRequest() {
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.ofAsync(ASYNC_BODY, (AsyncBodyRequestHandler<AsyncRequestBody>) (request, pathVariables, body) -> null);

        // a body that has nothing to release
        AsyncRequestBody nothingToRelease = body(null);
        NullPointerException plain = assertThrows(NullPointerException.class, () -> invoke(method, nothingToRelease));
        assertEquals("The asynchronous handler returned no stage", plain.getMessage());

        AtomicInteger released = new AtomicInteger();
        AsyncRequestBody releasable = body(released::incrementAndGet);
        NullPointerException handlerRequest = assertThrows(NullPointerException.class,
            () -> invoke(method, releasable));
        assertEquals("The asynchronous handler returned no stage", handlerRequest.getMessage());
        assertEquals(1, released.get());
    }

    @Test
    void anAsynchronousHandlerOfADecodedBodyThatReturnsNoStageFails() {
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.ofAsync(Argument.of(String.class),
            (AsyncBodyRequestHandler<String>) (request, pathVariables, body) -> null);

        Object[] arguments = {HttpRequest.POST("/x", "text"), pathVariables(), "text"};
        NullPointerException noStage = assertThrows(NullPointerException.class, () -> method.invoke(arguments));
        assertEquals("The asynchronous handler returned no stage", noStage.getMessage());
    }

    @Test
    void anAsynchronousHandlerWithoutTheBodyThatReturnsNoStageFails() {
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.of((AsyncRequestHandler) (request, pathVariables) -> null);

        Object[] arguments = {HttpRequest.GET("/x"), pathVariables()};
        NullPointerException noStage = assertThrows(NullPointerException.class, () -> method.invoke(arguments));
        assertEquals("The asynchronous handler returned no stage", noStage.getMessage());
    }

    @Test
    void anErrorOfTheHandlerReleasesTheBody() {
        StackOverflowError error = new StackOverflowError("handler");
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.ofAsync(ASYNC_BODY, (AsyncBodyRequestHandler<AsyncRequestBody>) (request, pathVariables, body) -> {
            throw error;
        });
        AtomicInteger released = new AtomicInteger();
        AsyncRequestBody releasable = body(released::incrementAndGet);

        StackOverflowError thrown = assertThrows(StackOverflowError.class, () -> invoke(method, releasable));
        assertSame(error, thrown);
        assertEquals(1, released.get());
    }

    @Test
    void aFailureToReleaseIsSuppressedByTheFailureOfTheHandler() {
        IllegalStateException failure = new IllegalStateException("handler");
        IllegalArgumentException releaseFailure = new IllegalArgumentException("release");
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.ofAsync(ASYNC_BODY, (AsyncBodyRequestHandler<AsyncRequestBody>) (request, pathVariables, body) -> {
            throw failure;
        });

        AsyncRequestBody failingRelease = body(() -> {
            throw releaseFailure;
        });
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> invoke(method, failingRelease));
        assertSame(failure, thrown);
        assertArrayEquals(new Throwable[] {releaseFailure}, thrown.getSuppressed());

        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> noStageMethod = HandlerMethod.ofAsync(ASYNC_BODY,
            (AsyncBodyRequestHandler<AsyncRequestBody>) (request, pathVariables, body) -> null);
        AsyncRequestBody alsoFailingRelease = body(() -> {
            throw releaseFailure;
        });
        NullPointerException noStage = assertThrows(NullPointerException.class, () -> invoke(noStageMethod, alsoFailingRelease));
        assertArrayEquals(new Throwable[] {releaseFailure}, noStage.getSuppressed());
    }

    @Test
    void anAsynchronousFilterThatReturnsNoStageFailsWithAMessage() {
        assertFilterFails("The asynchronous request filter returned no stage",
            routes -> routes.serverFilter("/**").beforeReplacingAsync((AsyncReplacingRouteRequestFilter) request -> null));
        assertFilterFails("The asynchronous request filter returned no stage",
            routes -> routes.serverFilter("/**").beforeAsync((AsyncRouteRequestFilter) request -> null));
        assertFilterFails("The asynchronous request filter returned no stage",
            routes -> routes.serverFilter("/**").beforeAsync((AsyncContextRouteRequestFilter) (request, context) -> null));
        assertFilterFails("The asynchronous response filter returned no stage",
            routes -> routes.serverFilter("/**").afterReplacingAsync((AsyncReplacingRouteResponseFilter) (request, response) -> null));
        assertFilterFails("The asynchronous response filter returned no stage",
            routes -> routes.serverFilter("/**").afterAsync((AsyncRouteResponseFilter) (request, response) -> null));
        assertFilterFails("The asynchronous response filter returned no stage",
            routes -> routes.serverFilter("/**").afterAsync((AsyncContextRouteResponseFilter) (request, response, context) -> null));
    }

    private static void assertFilterFails(String message, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        HttpRequest<?> request = HttpRequest.GET("/x");
        CompletableFuture<HttpResponse<?>> response = new FilterRunner(router.findFilters(request),
            (r, propagatedContext) -> ExecutionFlow.just(HttpResponse.status(HttpStatus.NOT_FOUND)))
            .run(request).toCompletableFuture();
        CompletionException e = assertThrows(CompletionException.class, response::join);
        Throwable cause = e.getCause();
        assertEquals(NullPointerException.class, cause.getClass());
        assertEquals(message, cause.getMessage());
    }

    private static Object invoke(HandlerMethod<?> method, AsyncRequestBody body) {
        return method.invoke(new Object[] {HttpRequest.GET("/x"), pathVariables(), body});
    }

    private static PathVariables pathVariables() {
        return new DefaultPathVariables(Map.of(), ConversionService.SHARED);
    }

    /**
     * @param release Releases the body, or {@code null} for a body without anything to release
     * @return The body of an asynchronous handler
     */
    private static AsyncRequestBody body(Runnable release) {
        Class<?>[] types = release == null
            ? new Class<?>[] {AsyncRequestBody.class}
            : new Class<?>[] {AsyncRequestBody.class, ReleasableRequestBody.class};
        return (AsyncRequestBody) Proxy.newProxyInstance(NoStageTest.class.getClassLoader(), types, (proxy, method, args) -> {
            if (method.getName().equals("releaseBody") && release != null) {
                release.run();
                return CompletableFuture.completedFuture(null);
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }
}
