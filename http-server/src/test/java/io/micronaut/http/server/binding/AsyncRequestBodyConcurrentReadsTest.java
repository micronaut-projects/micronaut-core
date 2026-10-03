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
package io.micronaut.http.server.binding;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reads of a request body that are in flight together, started from different threads: each
 * read waits for what its own binding waits for. The binder of the test tells when a binding added
 * what it waits for, and holds the binding until the test lets it return, so the bindings
 * overlap in a known order.
 */
class AsyncRequestBodyConcurrentReadsTest {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    /**
     * Two bodies of one request, e.g. the body a filter still reads and the body of the route:
     * their bindings overlap. The second binding starts while the first is bound, and returns
     * after the first one did.
     */
    @Test
    void theReadsOfTwoBodiesOfARequestWhoseBindingsOverlapWaitForTheirOwnBinding() throws Exception {
        Binders binders = new Binders();
        Server server = new Server();
        // what the route waits for is its own
        ExecutionFlow<?> route = CompletableFutureExecutionFlow.just(new CompletableFuture<>());
        BasicHttpAttributes.addRouteWaitsFor(server, route);
        DefaultAsyncRequestBody filterBody = new DefaultAsyncRequestBody(server, server, binders.binder());
        DefaultAsyncRequestBody routeBody = new DefaultAsyncRequestBody(server, server, binders.binder());
        AsyncRequestBody first = filterBody.copy();
        AsyncRequestBody second = routeBody.copy();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CompletionStage<String>> firstStarted = executor.submit(() -> first.body(String.class));
            assertTrue(binders.firstAdded.await(10, TimeUnit.SECONDS));
            Future<CompletionStage<Integer>> secondStarted = executor.submit(() -> second.body(Integer.class));
            // the second binding added what it waits for while the first one is bound
            assertTrue(binders.secondAdded.await(10, TimeUnit.SECONDS));
            binders.letFirstReturn.countDown();
            CompletionStage<String> firstRead = firstStarted.get(10, TimeUnit.SECONDS);
            binders.letSecondReturn.countDown();
            CompletionStage<Integer> secondRead = secondStarted.get(10, TimeUnit.SECONDS);

            // no read is done before its value arrived
            assertFalse(firstRead.toCompletableFuture().isDone());
            assertFalse(secondRead.toCompletableFuture().isDone());
            assertSame(route, BasicHttpAttributes.getRouteWaitsFor(server));

            binders.secondValue.complete(2);
            assertEquals(2, secondRead.toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertFalse(firstRead.toCompletableFuture().isDone());
            binders.firstValue.complete("first");
            assertEquals("first", firstRead.toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertSame(route, BasicHttpAttributes.getRouteWaitsFor(server));
        } finally {
            binders.release();
            filterBody.releaseBody();
            routeBody.releaseBody();
            server.bytes.close();
        }
    }

    /**
     * Two copies of one body: their reads are started one at a time, since they split the same
     * bytes, and each waits for its own binding.
     */
    @Test
    void theReadsOfTwoCopiesStartedFromDifferentThreadsWaitForTheirOwnBinding() throws Exception {
        Binders binders = new Binders();
        Server server = new Server();
        ExecutionFlow<?> route = CompletableFutureExecutionFlow.just(new CompletableFuture<>());
        BasicHttpAttributes.addRouteWaitsFor(server, route);
        DefaultAsyncRequestBody body = new DefaultAsyncRequestBody(server, server, binders.binder());
        AsyncRequestBody first = body.copy();
        AsyncRequestBody second = body.copy();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CompletionStage<String>> firstStarted = executor.submit(() -> first.body(String.class));
            assertTrue(binders.firstAdded.await(10, TimeUnit.SECONDS));
            Future<CompletionStage<Integer>> secondStarted = executor.submit(() -> second.body(Integer.class));
            // the second read is not bound while the first one is
            assertFalse(binders.secondAdded.await(300, TimeUnit.MILLISECONDS));
            binders.letFirstReturn.countDown();
            CompletionStage<String> firstRead = firstStarted.get(10, TimeUnit.SECONDS);
            assertTrue(binders.secondAdded.await(10, TimeUnit.SECONDS));
            binders.letSecondReturn.countDown();
            CompletionStage<Integer> secondRead = secondStarted.get(10, TimeUnit.SECONDS);

            assertFalse(firstRead.toCompletableFuture().isDone());
            assertFalse(secondRead.toCompletableFuture().isDone());
            assertSame(route, BasicHttpAttributes.getRouteWaitsFor(server));

            binders.firstValue.complete("first");
            assertEquals("first", firstRead.toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertFalse(secondRead.toCompletableFuture().isDone());
            binders.secondValue.complete(2);
            assertEquals(2, secondRead.toCompletableFuture().get(10, TimeUnit.SECONDS));
        } finally {
            binders.release();
            body.releaseBody();
            server.bytes.close();
        }
    }

    /**
     * A {@code String} is bound first and an {@code Integer} second: each binding adds the value
     * it waits for, tells the test, and returns when the test lets it.
     */
    private static final class Binders implements RequestBinderRegistry {
        final CountDownLatch firstAdded = new CountDownLatch(1);
        final CountDownLatch secondAdded = new CountDownLatch(1);
        final CountDownLatch letFirstReturn = new CountDownLatch(1);
        final CountDownLatch letSecondReturn = new CountDownLatch(1);
        final CompletableFuture<String> firstValue = new CompletableFuture<>();
        final CompletableFuture<Integer> secondValue = new CompletableFuture<>();

        AsyncRequestBodyArgumentBinder binder() {
            return new AsyncRequestBodyArgumentBinder(
                () -> new RequestArgumentSatisfier(this),
                () -> {
                    throw new AssertionError("No body is read");
                },
                () -> {
                    throw new AssertionError("No form is read");
                },
                ConversionService.SHARED);
        }

        void release() {
            letFirstReturn.countDown();
            letSecondReturn.countDown();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
            boolean first = argument.getType() == String.class;
            CompletableFuture<?> value = first ? firstValue : secondValue;
            return Optional.of((context, request) -> {
                BasicHttpAttributes.addRouteWaitsFor(request, CompletableFutureExecutionFlow.just(value));
                (first ? firstAdded : secondAdded).countDown();
                await(first ? letFirstReturn : letSecondReturn);
                return new PendingRequestBindingResult<T>() {
                    @Override
                    public boolean isPending() {
                        return !value.isDone();
                    }

                    @Override
                    public Optional<T> getValue() {
                        return Optional.ofNullable((T) value.getNow(null));
                    }
                };
            });
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("The binding was not let to return");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static final class Server extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object> {
        final CloseableByteBody bytes = FACTORY.copyOf("body", StandardCharsets.UTF_8);

        Server() {
            super(new SimpleHttpRequest<>(HttpMethod.POST, "/", null));
        }

        @Override
        public ByteBody byteBody() {
            return bytes;
        }

        @Override
        public ByteBodyFactory byteBodyFactory() {
            return FACTORY;
        }
    }
}
