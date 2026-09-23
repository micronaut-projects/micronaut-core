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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.AsyncServerHttpRequest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
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

    @Test
    void anAsynchronousHandlerThatReturnsNoStageFailsWhateverTheRequest() {
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.of((AsyncRequestHandler) (request, pathVariables) -> null);

        // a request that has no body to release
        NullPointerException plain = assertThrows(NullPointerException.class, () -> invoke(method, request(null)));
        assertEquals("The asynchronous handler returned no stage", plain.getMessage());

        AtomicInteger released = new AtomicInteger();
        NullPointerException handlerRequest = assertThrows(NullPointerException.class,
            () -> invoke(method, request(() -> released.incrementAndGet())));
        assertEquals("The asynchronous handler returned no stage", handlerRequest.getMessage());
        assertEquals(1, released.get());
    }

    @Test
    void anErrorOfTheHandlerReleasesTheBody() {
        StackOverflowError error = new StackOverflowError("handler");
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.of((AsyncRequestHandler) (request, pathVariables) -> {
            throw error;
        });
        AtomicInteger released = new AtomicInteger();

        StackOverflowError thrown = assertThrows(StackOverflowError.class, () -> invoke(method, request(() -> released.incrementAndGet())));
        assertSame(error, thrown);
        assertEquals(1, released.get());
    }

    @Test
    void aFailureToReleaseIsSuppressedByTheFailureOfTheHandler() {
        IllegalStateException failure = new IllegalStateException("handler");
        IllegalArgumentException releaseFailure = new IllegalArgumentException("release");
        HandlerMethod<CompletionStage<? extends HttpResponse<?>>> method = HandlerMethod.of((AsyncRequestHandler) (request, pathVariables) -> {
            throw failure;
        });

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> invoke(method, request(() -> {
            throw releaseFailure;
        })));
        assertSame(failure, thrown);
        assertArrayEquals(new Throwable[] {releaseFailure}, thrown.getSuppressed());

        NullPointerException noStage = assertThrows(NullPointerException.class, () -> invoke(HandlerMethod.of((AsyncRequestHandler) (request, pathVariables) -> null), request(() -> {
                throw releaseFailure;
            })));
        assertArrayEquals(new Throwable[] {releaseFailure}, noStage.getSuppressed());
    }

    @Test
    void anAsynchronousFilterThatReturnsNoStageFailsWithAMessage() {
        assertFilterFails("The asynchronous request filter returned no stage",
            routes -> routes.filter("/**").beforeAsync((AsyncRouteRequestFilter) request -> null));
        assertFilterFails("The asynchronous response filter returned no stage",
            routes -> routes.filter("/**").afterReplacingAsync((AsyncReplacingRouteResponseFilter) (request, response) -> null));
        assertFilterFails("The asynchronous response filter returned no stage",
            routes -> routes.filter("/**").afterAsync((AsyncRouteResponseFilter) (request, response) -> null));
        assertFilterFails("The asynchronous response filter returned no stage",
            routes -> routes.filter("/**").afterAsync((AsyncContextRouteResponseFilter) (request, response, context) -> null));
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

    private static Object invoke(HandlerMethod<?> method, AsyncServerHttpRequest<?> request) {
        return method.invoke(new Object[] {request, pathVariables()});
    }

    private static PathVariables pathVariables() {
        return new DefaultPathVariables(Map.of(), ConversionService.SHARED);
    }

    /**
     * @param release Releases the body, or {@code null} for a request without a body to release
     * @return The request of an asynchronous handler
     */
    private static AsyncServerHttpRequest<?> request(Runnable release) {
        Class<?>[] types = release == null
            ? new Class<?>[] {AsyncServerHttpRequest.class}
            : new Class<?>[] {AsyncServerHttpRequest.class, AsyncHandlerRequest.class};
        return (AsyncServerHttpRequest<?>) Proxy.newProxyInstance(NoStageTest.class.getClassLoader(), types, (proxy, method, args) -> {
            if (method.getName().equals("releaseBody") && release != null) {
                release.run();
                return CompletableFuture.completedFuture(null);
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }
}
