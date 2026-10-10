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
package io.micronaut.http.body;

import io.micronaut.http.body.stream.ReleasingBodyElements;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BodyElements} made by the application: the rules of {@link BodyElements#of}, the default
 * {@link BodyElements#forEach} loop over a lambda, and the elements that release a body when they
 * are closed.
 */
class BodyElementsTest {

    @Test
    void ofRetainsTheEndAcrossReadsAndIteration() {
        AtomicInteger reads = new AtomicInteger();
        BodyElements<String> elements = BodyElements.of(() -> CompletableFuture.completedFuture(
            reads.getAndIncrement() == 0 ? Optional.empty() : Optional.of("unexpected")));
        assertEquals(Optional.empty(), elements.next().toCompletableFuture().join());
        assertEquals(BodyElements.State.COMPLETED, elements.state());
        assertEquals(null, elements.failure());
        assertEquals(Optional.empty(), elements.next().toCompletableFuture().join());
        elements.forEach(value -> {
            throw new AssertionError("An element was read after EOF");
        }).toCompletableFuture().join();
        assertEquals(1, reads.get());
    }

    @Test
    void ofIterationRetainsTheEndForTheNextRead() {
        AtomicInteger reads = new AtomicInteger();
        BodyElements<String> elements = BodyElements.of(() -> {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.empty());
        });
        elements.forEach(value -> CompletableFuture.completedFuture(null)).toCompletableFuture().join();
        assertEquals(BodyElements.State.COMPLETED, elements.state());
        assertEquals(Optional.empty(), elements.next().toCompletableFuture().join());
        assertEquals(1, reads.get());
    }

    @Test
    void ofRetainsReadFailuresAcrossReadsAndIteration() {
        AtomicInteger reads = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("read");
        BodyElements<String> elements = BodyElements.of(() -> reads.getAndIncrement() == 0 ?
            CompletableFuture.failedFuture(failure) : CompletableFuture.completedFuture(Optional.of("unexpected")));
        for (int i = 0; i < 2; i++) {
            var read = elements.next().toCompletableFuture();
            assertSame(failure, assertThrows(CompletionException.class, read::join).getCause());
            assertEquals(BodyElements.State.FAILED, elements.state());
            assertSame(failure, elements.failure());
        }
        var iteration = elements.forEach(value -> CompletableFuture.completedFuture(null)).toCompletableFuture();
        assertSame(failure, assertThrows(CompletionException.class, iteration::join).getCause());
        assertSame(failure, elements.failure());
        assertEquals(1, reads.get());
    }

    @Test
    void ofRetainsSynchronousSupplierFailures() {
        AtomicInteger reads = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("supplier");
        BodyElements<String> elements = BodyElements.of(() -> {
            reads.incrementAndGet();
            throw failure;
        });
        var firstRead = elements.next().toCompletableFuture();
        assertSame(failure, assertThrows(CompletionException.class, firstRead::join).getCause());
        var repeatedRead = elements.next().toCompletableFuture();
        assertSame(failure, assertThrows(CompletionException.class, repeatedRead::join).getCause());
        assertSame(failure, elements.failure());
        assertEquals(1, reads.get());
    }

    @Test
    void ofAllowsOneOperationAtATime() {
        CompletableFuture<Optional<String>> pending = new CompletableFuture<>();
        BodyElements<String> elements = BodyElements.of(() -> pending);
        CompletionStage<Optional<String>> first = elements.next();
        assertThrows(IllegalStateException.class, elements::next);
        assertThrows(IllegalStateException.class, () -> elements.forEach(element -> CompletableFuture.completedFuture(null)));
        pending.complete(Optional.of("a"));
        assertEquals(Optional.of("a"), first.toCompletableFuture().join());
    }

    @Test
    void ofLetsAContinuationReadTheNextElement() {
        AtomicInteger count = new AtomicInteger();
        BodyElements<Integer> elements = BodyElements.of(() -> CompletableFuture.completedFuture(Optional.of(count.incrementAndGet())));
        // the operation ends before its stage completes
        assertEquals(Optional.of(2), elements.next().thenCompose(first -> elements.next()).toCompletableFuture().join());
    }

    @Test
    void ofClosingFailsAWaitingReadAndRunsTheCallbackOnce() {
        AtomicInteger closed = new AtomicInteger();
        BodyElements<String> elements = BodyElements.of(CompletableFuture::new, closed::incrementAndGet);
        CompletableFuture<Optional<String>> waiting = elements.next().toCompletableFuture();
        CompletionStage<Void> first = elements.closeAsync();
        // join() throws a cancellation as it is
        assertThrows(CancellationException.class, waiting::join);
        assertSame(first, elements.closeAsync());
        elements.close();
        assertEquals(1, closed.get());
        assertThrows(IllegalStateException.class, elements::next);
        assertThrows(IllegalStateException.class, () -> elements.forEach(element -> CompletableFuture.completedFuture(null)));
    }

    @Test
    void asynchronousCleanupStartsOnceAndCannotBeCancelledByAnObserver() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        BodyElements<String> elements = BodyElements.ofAsync(CompletableFuture::new, () -> {
            calls.incrementAndGet();
            return opaque(cleanup);
        });
        CompletableFuture<Optional<String>> read = elements.next().toCompletableFuture();
        elements.close();
        assertThrows(CancellationException.class, read::join);
        CompletionStage<Void> closed = elements.closeAsync();
        assertSame(closed, elements.closeAsync());
        assertTrue(!closed.toCompletableFuture().isDone());
        closed.toCompletableFuture().cancel(false);
        assertTrue(!cleanup.isDone());
        assertTrue(!elements.closeAsync().toCompletableFuture().isDone());
        cleanup.complete(null);
        elements.closeAsync().toCompletableFuture().join();
        assertEquals(1, calls.get());
    }

    @Test
    void asynchronousCleanupReportsDelayedFailure() {
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        BodyElements<String> elements = BodyElements.ofAsync(CompletableFuture::new, () -> cleanup);
        CompletionStage<Void> closed = elements.closeAsync();
        IllegalStateException failure = new IllegalStateException("cleanup failed");
        cleanup.completeExceptionally(failure);
        CompletableFuture<Void> closedFuture = closed.toCompletableFuture();
        assertSame(failure, assertThrows(CompletionException.class, closedFuture::join).getCause());
        assertSame(failure, assertThrows(IllegalStateException.class, elements::close));
    }

    @Test
    void asynchronousCleanupReportsSupplierFailureAndMissingStage() {
        IllegalStateException failure = new IllegalStateException("cleanup failed");
        BodyElements<String> elements = BodyElements.ofAsync(CompletableFuture::new, () -> {
            throw failure;
        });
        CompletableFuture<Void> closed = elements.closeAsync().toCompletableFuture();
        assertSame(failure, assertThrows(CompletionException.class, closed::join).getCause());
        BodyElements<String> invalid = BodyElements.ofAsync(CompletableFuture::new, () -> null);
        CompletableFuture<Void> invalidClosed = invalid.closeAsync().toCompletableFuture();
        assertTrue(assertThrows(CompletionException.class, invalidClosed::join).getCause() instanceof NullPointerException);
    }

    @Test
    void ofClosesAnElementProducedAfterClosing() {
        CompletableFuture<Optional<AutoCloseable>> pending = new CompletableFuture<>();
        BodyElements<AutoCloseable> elements = BodyElements.of(() -> pending);
        CompletableFuture<Optional<AutoCloseable>> waiting = elements.next().toCompletableFuture();
        elements.closeAsync();
        assertThrows(CancellationException.class, waiting::join);
        // nobody takes the element anymore
        AtomicInteger closed = new AtomicInteger();
        pending.complete(Optional.of(closed::incrementAndGet));
        assertEquals(1, closed.get());
    }

    @Test
    void ofForEachClosesAnElementProducedAfterClosing() {
        CompletableFuture<Optional<AutoCloseable>> pending = new CompletableFuture<>();
        BodyElements<AutoCloseable> elements = BodyElements.of(() -> pending);
        AtomicInteger consumed = new AtomicInteger();
        CompletableFuture<Void> loop = elements.forEach(element -> {
            consumed.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture();
        elements.closeAsync();
        assertThrows(CancellationException.class, loop::join);
        AtomicInteger closed = new AtomicInteger();
        pending.complete(Optional.of(closed::incrementAndGet));
        assertEquals(1, closed.get());
        assertEquals(0, consumed.get());
    }

    @Test
    void ofClosingCancelsForEach() {
        BodyElements<String> elements = BodyElements.of(CompletableFuture::new);
        CompletableFuture<Void> each = elements.forEach(element -> CompletableFuture.completedFuture(null)).toCompletableFuture();
        elements.close();
        assertThrows(CancellationException.class, each::join);
    }

    @Test
    void ofForEachIsOneOperation() {
        List<Integer> consumed = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        BodyElements<Integer> elements = BodyElements.of(() -> CompletableFuture.completedFuture(
            count.get() < 3 ? Optional.of(count.incrementAndGet()) : Optional.empty()));
        elements.forEach(element -> {
            consumed.add(element);
            // another operation while forEach runs
            assertThrows(IllegalStateException.class, elements::next);
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture().join();
        assertEquals(List.of(1, 2, 3), consumed);
    }

    @Test
    void defaultForEachLoopsOverElementsThatAreAvailableAtOnce() {
        int total = 100_000;
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();
        BodyElements<Integer> elements = () -> CompletableFuture.completedFuture(
            produced.get() < total ? Optional.of(produced.incrementAndGet()) : Optional.empty());
        // many elements available at once do not deepen the stack
        elements.forEach(element -> {
            consumed.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture().join();
        assertEquals(total, consumed.get());
    }

    @Test
    void defaultForEachContinuesOnTheThreadThatCompletesAStage() {
        List<CompletableFuture<Optional<Integer>>> reads = new ArrayList<>();
        BodyElements<Integer> elements = () -> {
            CompletableFuture<Optional<Integer>> read = new CompletableFuture<>();
            reads.add(read);
            return read;
        };
        List<Integer> consumed = new ArrayList<>();
        CompletableFuture<Void> each = elements.forEach(element -> {
            consumed.add(element);
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture();
        reads.get(0).complete(Optional.of(1));
        reads.get(1).complete(Optional.of(2));
        reads.get(2).complete(Optional.empty());
        each.join();
        assertEquals(List.of(1, 2), consumed);
    }

    @Test
    void defaultForEachNeverConvertsAStage() {
        AtomicInteger produced = new AtomicInteger();
        BodyElements<Integer> elements = () -> opaque(CompletableFuture.completedFuture(
            produced.get() < 3 ? Optional.of(produced.incrementAndGet()) : Optional.empty()));
        AtomicInteger consumed = new AtomicInteger();
        elements.forEach(element -> {
            consumed.incrementAndGet();
            return opaque(CompletableFuture.completedFuture(null));
        }).toCompletableFuture().join();
        assertEquals(3, consumed.get());
    }

    @Test
    void defaultForEachFailsWithTheConsumerAndClosesTheElements() {
        AtomicInteger closed = new AtomicInteger();
        BodyElements<Integer> elements = new BodyElements<>() {
            @Override
            public CompletionStage<Optional<Integer>> next() {
                return CompletableFuture.completedFuture(Optional.of(1));
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
        CompletableFuture<Void> each = elements.forEach(element -> CompletableFuture.failedFuture(new IllegalStateException("consumer"))).toCompletableFuture();
        CompletionException failure = assertThrows(CompletionException.class, each::join);
        assertEquals("consumer", failure.getCause().getMessage());
        assertEquals(1, closed.get());
    }

    @Test
    void defaultCloseAsync() {
        BodyElements<String> lambda = () -> CompletableFuture.completedFuture(Optional.empty());
        assertTrue(lambda.closeAsync().toCompletableFuture().isDone());
        BodyElements<String> failing = new BodyElements<>() {
            @Override
            public CompletionStage<Optional<String>> next() {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public void close() {
                throw new IllegalStateException("cannot close");
            }
        };
        CompletableFuture<Void> closed = failing.closeAsync().toCompletableFuture();
        CompletionException failure = assertThrows(CompletionException.class, closed::join);
        assertEquals("cannot close", failure.getCause().getMessage());
    }

    @Test
    void releasingElementsReleaseOnceOnEitherClose() {
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        BodyElements<String> elements = ReleasingBodyElements.onClose(
            BodyElements.of(() -> CompletableFuture.completedFuture(Optional.of("a")), closed::incrementAndGet), released::incrementAndGet);
        // the elements keep their own operations
        assertEquals(Optional.of("a"), elements.next().toCompletableFuture().join());
        elements.close();
        elements.closeAsync().toCompletableFuture().join();
        assertEquals(1, closed.get());
        assertEquals(1, released.get());
        assertThrows(IllegalStateException.class, elements::next);
    }

    @Test
    void ofForEachClosesTheElementsWhenReadingFails() {
        AtomicInteger closed = new AtomicInteger();
        BodyElements<String> elements = BodyElements.of(() -> CompletableFuture.failedFuture(new IllegalStateException("read")), closed::incrementAndGet);
        CompletableFuture<Void> done = elements.forEach(element -> CompletableFuture.completedFuture(null)).toCompletableFuture();
        assertThrows(CompletionException.class, done::join);
        assertEquals(1, closed.get());
    }

    @Test
    void defaultForEachTakesTheElementsThatArePolledWithoutAStage() {
        AtomicInteger reads = new AtomicInteger();
        List<Integer> available = new ArrayList<>(List.of(1, 2, 3));
        BodyElements<Integer> elements = new BodyElements<>() {
            @Override
            public Integer poll() {
                return available.isEmpty() ? null : available.remove(0);
            }

            @Override
            public CompletionStage<Optional<Integer>> next() {
                reads.incrementAndGet();
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
        List<Integer> consumed = new ArrayList<>();
        elements.forEach(element -> {
            consumed.add(element);
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture().join();
        assertEquals(List.of(1, 2, 3), consumed);
        // only the end was read with a stage
        assertEquals(1, reads.get());
    }

    @Test
    void theDefaultsKnowNothingAhead() {
        BodyElements<String> elements = () -> CompletableFuture.completedFuture(Optional.empty());
        assertEquals(null, elements.poll());
        assertEquals(BodyElements.State.PENDING, elements.state());
        assertEquals(null, elements.failure());
    }

    /**
     * A stage that refuses {@link CompletionStage#toCompletableFuture()}, which the contract of
     * {@link CompletionStage} allows.
     */
    @SuppressWarnings("unchecked")
    private static <T> CompletionStage<T> opaque(CompletableFuture<T> future) {
        return (CompletionStage<T>) Proxy.newProxyInstance(BodyElementsTest.class.getClassLoader(), new Class<?>[]{CompletionStage.class}, (proxy, method, args) -> {
            if (method.getName().equals("toCompletableFuture")) {
                throw new UnsupportedOperationException("An opaque stage");
            }
            try {
                return method.invoke(future, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
