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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locators that read the body: the server provides the body of the request, the locator reads a
 * copy of it, and the request is matched again once the target is located.
 */
class LocatorBodyTest {

    private final List<TestBody> bound = new ArrayList<>();
    private final CompletableFuture<Object> content = new CompletableFuture<>();
    private CompletableFuture<Void> releaseStage = CompletableFuture.completedFuture(null);
    private @Nullable RuntimeException releaseThrows;
    private @Nullable ApplicationContext context;

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void theLocatorReceivesTheDecodedBodyOnceItIsRead() {
        AtomicInteger calls = new AtomicInteger();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/items", (request, pathVariables) ->
            HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables))));
        Router router = router(routes -> routes.locator("/orders/{id}")
            .body(String.class)
            .locate((request, pathVariables, body) -> {
                calls.incrementAndGet();
                return pathVariables.getLong("id") + ":" + body;
            }, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders/5/items", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        CompletionStage<?> pending = pending(() -> router.findClosest(request));
        assertEquals(0, calls.get(), "the locator waits for the body");
        // not located yet: the body is not bound again
        assertSame(pending, pending(() -> router.findClosest(request)));
        assertEquals(1, bound.size());
        assertFalse(bound.getFirst().released.get() > 0);

        content.complete("gold");
        assertTrue(pending.toCompletableFuture().isDone());
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        assertEquals("5:gold", ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target());
        assertEquals(1, calls.get());
        assertEquals(1, bound.size());
        // the locator read a copy, and released what its read left open
        assertEquals(1, bound.getFirst().copies.get());
        assertEquals(1, bound.getFirst().released.get());
    }

    @Test
    void anAsyncLocatorReadsTheCopyOfTheBodyItself() {
        AtomicReference<AsyncRequestBody> received = new AtomicReference<>();
        CompletableFuture<Object> order = new CompletableFuture<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body()
            .locateAsync((request, pathVariables, body) -> {
                received.set(body);
                return body.body(String.class).thenCompose(text -> order.thenApply(t -> t + text));
            }, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        CompletionStage<?> pending = pending(() -> router.findClosest(request));
        // called at once, with the copy, not the body of the request
        assertNotNull(received.get());
        assertNotSame(bound.getFirst(), received.get());
        content.complete("-x");
        assertFalse(pending.toCompletableFuture().isDone());
        assertEquals(0, bound.getFirst().released.get(), "released when the stage of the locator completes");
        order.complete("order");
        assertTrue(pending.toCompletableFuture().isDone());
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        assertEquals("order-x", ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target());
        assertEquals(1, bound.getFirst().released.get());
    }

    @Test
    void aBodyThatFailsToReadFailsTheLocationWithoutCallingTheLocator() {
        AtomicInteger calls = new AtomicInteger();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body(String.class)
            .locate((request, pathVariables, body) -> {
                calls.incrementAndGet();
                return body;
            }, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        CompletionStage<?> pending = pending(() -> router.findClosest(request));
        IllegalArgumentException failure = new IllegalArgumentException("bad body");
        content.completeExceptionally(failure);
        assertTrue(pending.toCompletableFuture().isDone());
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> router.findClosest(request)));
        assertEquals(0, calls.get());
        assertEquals(1, bound.getFirst().released.get());
    }

    @Test
    void aLocatorThatFindsNoTargetIsNotFound() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body(String.class)
            .locate((request, pathVariables, body) -> null, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        pending(() -> router.findClosest(request));
        content.complete("none");
        assertNull(router.findClosest(request));
    }

    @Test
    void aRequestThatIsNotAFormIsLocatedWithTheEmptyFormWithoutReadingTheBody() {
        AtomicReference<FormData> received = new AtomicReference<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            located.GET("/", (request, pathVariables) -> HttpResponse.ok());
            located.POST("/", (request, pathVariables) -> HttpResponse.ok());
        });
        Router router = router(routes -> routes.locator("/orders")
            .form()
            .locate((request, pathVariables, form) -> {
                received.set(form);
                return "order";
            }, target -> items));

        // located at once: no body is waited for
        assertNotNull(router.findClosest(HttpRequest.GET("/orders")));
        assertSame(FormData.empty(), received.get());
        assertNotNull(router.findClosest(HttpRequest.POST("/orders", "{}").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE)));
        assertTrue(bound.isEmpty(), "the body is not bound");
    }

    @Test
    void aFormRequestReadsTheForm() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .form()
            .locate((request, pathVariables, form) -> form.getString("id"), target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        pending(() -> router.findClosest(request));
        assertEquals(1, bound.size());
    }

    @Test
    void anAbandonedRequestIsNotHeldByASharedStageOfTheLocator() {
        // shared by every request, e.g. of a cache, and never completes
        CompletableFuture<Object> shared = new CompletableFuture<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body(String.class)
            .locateAsync((request, pathVariables, body) -> shared, target -> items));

        WeakReference<HttpRequest<?>> abandoned = abandonAfterTheBodyWasRead(router);
        // the count only: the body references the request
        AtomicInteger released = bound.getFirst().released;
        // released when the request was abandoned, not when the shared stage completes
        assertEquals(1, released.get());
        bound.clear();
        for (int i = 0; i < 50 && abandoned.get() != null; i++) {
            System.gc();
        }
        assertNull(abandoned.get(), "the shared stage does not hold the abandoned request");
        shared.complete("late");
        assertEquals(1, released.get(), "released once");
    }

    private WeakReference<HttpRequest<?>> abandonAfterTheBodyWasRead(Router router) {
        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        pending(() -> router.findClosest(request));
        content.complete("body");
        // the body was read: the locator waits for its own stage
        pending(() -> router.findClosest(request));
        assertEquals(0, bound.getFirst().released.get());
        RouteLocator.abandonPendingLocations(request);
        return new WeakReference<>(request);
    }

    @Test
    void aReleaseThatThrowsWhenTheRequestIsAbandonedStillAbandonsTheRequest() {
        releaseThrows = new IllegalStateException("release failed");
        CompletableFuture<Object> shared = new CompletableFuture<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> {
            routes.locator("/orders")
                .body(String.class)
                .locateAsync((request, pathVariables, body) -> shared, target -> items);
            // a second locator of the request, abandoned after the first
            routes.locateAsync("/others/{id}", (request, pathVariables) -> shared, target -> items);
        });

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        pending(() -> router.findClosest(request));
        content.complete("order");
        CompletionStage<?> waiting = pending(() -> router.findClosest(request));
        HttpRequest<?> other = HttpRequest.GET("/others/1");
        CompletionStage<?> otherWaiting = pending(() -> router.findClosest(other));

        RouteLocator.abandonPendingLocations(request);
        assertTrue(waiting.toCompletableFuture().isDone(), "the request stops waiting despite the failed release");
        assertEquals(1, bound.getFirst().released.get());
        assertTrue(RouteLocator.isAbandonment(assertThrows(RuntimeException.class, () -> router.findClosest(request))));
        // another request is not affected
        assertFalse(otherWaiting.toCompletableFuture().isDone());
    }

    @Test
    void theLocationCompletesOnceTheBodyWasReleased() {
        releaseStage = new CompletableFuture<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body(String.class)
            .locate((request, pathVariables, body) -> body, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        CompletionStage<?> pending = pending(() -> router.findClosest(request));
        content.complete("order");
        assertFalse(pending.toCompletableFuture().isDone(), "the release of the body is waited for");
        releaseStage.complete(null);
        assertTrue(pending.toCompletableFuture().isDone());
        assertNotNull(router.findClosest(request));
    }

    @Test
    void aFailureToReleaseTheBodyFailsTheLocation() {
        IllegalStateException releaseFailure = new IllegalStateException("release failed");
        releaseStage = CompletableFuture.failedFuture(releaseFailure);
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body(String.class)
            .locate((request, pathVariables, body) -> body, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        pending(() -> router.findClosest(request));
        content.complete("order");
        assertSame(releaseFailure, assertThrows(IllegalStateException.class, () -> router.findClosest(request)));
    }

    @Test
    void aFailureToReleaseIsSuppressedByTheFailureOfTheLocator() {
        IllegalStateException releaseFailure = new IllegalStateException("release failed");
        releaseStage = CompletableFuture.failedFuture(releaseFailure);
        IllegalArgumentException locatorFailure = new IllegalArgumentException("locator failed");
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body(String.class)
            .locate((request, pathVariables, body) -> {
                throw locatorFailure;
            }, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        pending(() -> router.findClosest(request));
        content.complete("order");
        assertSame(locatorFailure, assertThrows(IllegalArgumentException.class, () -> router.findClosest(request)));
        assertSame(releaseFailure, locatorFailure.getSuppressed()[0]);
    }

    @Test
    void theLocatorRunsInTheContextOfTheMatchingWhenTheBodyArrivesLater() {
        AtomicReference<@Nullable Trace> seen = new AtomicReference<>();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locator("/orders")
            .body(String.class)
            .locate((request, pathVariables, body) -> {
                seen.set(PropagatedContext.getOrEmpty().find(Trace.class).orElse(null));
                return body;
            }, target -> items));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        PropagatedContext.getOrEmpty().plus(new Trace("t-1")).propagate(() -> pending(() -> router.findClosest(request)));
        // completed outside of the context, e.g. on the thread that received the last chunk
        content.complete("order");
        assertEquals(new Trace("t-1"), seen.get());
        assertTrue(PropagatedContext.getOrEmpty().find(Trace.class).isEmpty(), "the context is not leaked to the completing thread");
    }

    record Trace(String id) implements PropagatedContextElement {
    }

    @Test
    void withoutAServerTheBodyIsNotProvided() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/", (request, pathVariables) -> HttpResponse.ok()));
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        new DefaultHttpRouteBuilder(assembly).locator("/orders").body(String.class).locate((request, pathVariables, body) -> body, target -> items);
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));

        HttpRequest<?> request = HttpRequest.POST("/orders", "").contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> router.findClosest(request));
        assertTrue(error.getMessage().contains("No HTTP server provides the body"), error.getMessage());
    }

    private static CompletionStage<?> pending(org.junit.jupiter.api.function.Executable match) {
        RuntimeException error = assertThrows(RuntimeException.class, match);
        CompletionStage<?> stage = RouteLocator.pendingLocation(error);
        assertNotNull(stage, () -> "the locator waits for the body: " + error);
        return stage;
    }

    private Router router(Consumer<HttpRouteBuilder> routes) {
        ApplicationContext ctx = ApplicationContext.builder().start();
        context = ctx;
        ctx.registerSingleton(RouteConditionContext.class, new RouteConditionContext() {
            @Override
            public @Nullable String host(HttpRequest<?> request) {
                return null;
            }

            @Override
            public @Nullable String clientAddress(HttpRequest<?> request) {
                return null;
            }

            @Override
            public Clock clock() {
                return Clock.systemUTC();
            }

            @Override
            public AsyncRequestBody locatorBody(HttpRequest<?> request) {
                TestBody body = new TestBody(content, null, releaseStage, request);
                body.releaseThrows = releaseThrows;
                bound.add(body);
                return body;
            }
        });
        RouteAssembly assembly = new RouteAssembly(ctx, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    /**
     * A body whose content completes later.
     */
    private static final class TestBody implements AsyncRequestBody, ReleasableRequestBody {
        final AtomicInteger copies = new AtomicInteger();
        final AtomicInteger released = new AtomicInteger();
        private final CompletableFuture<Object> content;
        private final @Nullable TestBody original;
        private final CompletableFuture<Void> releaseStage;
        /**
         * Like the body of a server, it references its request.
         */
        private final @Nullable HttpRequest<?> request;

        TestBody(CompletableFuture<Object> content, @Nullable TestBody original, CompletableFuture<Void> releaseStage, @Nullable HttpRequest<?> request) {
            this.content = content;
            this.original = original;
            this.releaseStage = releaseStage;
            this.request = request;
        }

        private @Nullable RuntimeException releaseThrows;

        @Override
        public CompletionStage<Void> releaseBody() {
            released.incrementAndGet();
            RuntimeException failure = releaseThrows;
            if (failure != null) {
                throw failure;
            }
            return releaseStage;
        }

        @Override
        public AsyncRequestBody copy() {
            copies.incrementAndGet();
            return new TestBody(content, this, CompletableFuture.completedFuture(null), request);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> CompletionStage<@Nullable T> body(Class<T> type) {
            return content.thenApply(value -> (T) value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends @Nullable Object> CompletionStage<@Nullable T> body(Argument<T> type) {
            return content.thenApply(value -> (T) value);
        }

        @Override
        public CompletionStage<FormData> form() {
            return content.thenApply(FormData.class::cast);
        }

        @Override
        public boolean hasBody() {
            return true;
        }

        @Override
        public OptionalLong expectedBodySize() {
            return OptionalLong.empty();
        }

        @Override
        public CompletionStage<String> text() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<String> text(int maximumBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<String> text(int maximumBytes, Charset charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<byte[]> bytes(int maximumBytes) {
            return content.thenApply(value -> value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public <T> BodyElements<T> elements(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> BodyElements<T> elements(Argument<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> transferTo(Path destination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FormParts parts() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CloseableByteBody takeBody() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Void> discardBody() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return original == null ? "bound body" : "copy";
        }
    }
}
