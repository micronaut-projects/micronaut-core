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
package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.body.BodyElements;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of {@link BodyElements} as {@link PulledBodyElements} enforces them: the elements
 * that are available at once, the state, the end and the failure read again, and closing.
 */
class PulledBodyElementsTest {

    @Test
    void anAvailableElementIsReadWithACompletedStage() {
        Source<String> elements = new Source<>();
        elements.held.add("a");
        assertEquals(BodyElements.State.AVAILABLE, elements.state());
        CompletableFuture<Optional<String>> read = elements.next().toCompletableFuture();
        assertTrue(read.isDone());
        assertEquals(Optional.of("a"), read.join());
        assertEquals(0, elements.demands.get());
    }

    @Test
    void pollTakesWhatIsAvailableWithoutAskingTheSource() {
        Source<String> elements = new Source<>();
        assertNull(elements.poll());
        assertEquals(BodyElements.State.PENDING, elements.state());
        elements.held.add("a");
        assertEquals("a", elements.poll());
        assertEquals(0, elements.demands.get());
    }

    @Test
    void aWaitingReadIsAnsweredByTheSource() {
        Source<String> elements = new Source<>();
        CompletableFuture<Optional<String>> read = elements.next().toCompletableFuture();
        assertFalse(read.isDone());
        assertEquals(1, elements.demands.get());
        assertThrows(IllegalStateException.class, elements::poll);
        assertThrows(IllegalStateException.class, elements::next);
        elements.give("a");
        assertEquals(Optional.of("a"), read.join());
    }

    @Test
    void theEndIsReadAgain() {
        Source<String> elements = new Source<>();
        elements.finish();
        assertEquals(BodyElements.State.COMPLETED, elements.state());
        assertEquals(Optional.empty(), elements.next().toCompletableFuture().join());
        assertEquals(Optional.empty(), elements.next().toCompletableFuture().join());
        assertNull(elements.poll());
        assertNull(elements.failure());
    }

    @Test
    void theFailureIsReadAgainAfterTheQueuedElements() {
        Source<String> elements = new Source<>();
        elements.queue("a");
        IllegalStateException error = new IllegalStateException("source");
        elements.failWith(error);
        assertEquals(BodyElements.State.AVAILABLE, elements.state());
        assertNull(elements.failure());
        assertEquals(Optional.of("a"), elements.next().toCompletableFuture().join());
        assertEquals(BodyElements.State.FAILED, elements.state());
        assertEquals(error, elements.failure());
        for (int i = 0; i < 2; i++) {
            CompletionException e = assertThrows(CompletionException.class, () -> elements.next().toCompletableFuture().join());
            assertEquals(error, e.getCause());
        }
    }

    @Test
    void closingFailsTheWaitingReadAndReleasesTheQueuedElements() {
        Source<Counted> elements = new Source<>();
        Counted queued = new Counted();
        elements.queue(queued);
        elements.queue(new Counted());
        assertEquals(Optional.of(queued), elements.next().toCompletableFuture().join());
        Counted second = elements.poll();
        CompletableFuture<Optional<Counted>> read = elements.next().toCompletableFuture();
        Counted unread = new Counted();
        // not possible while a read waits, so close first
        elements.close();
        CompletionException cancelled = assertThrows(CompletionException.class, read::join);
        assertInstanceOf(CancellationException.class, cancelled.getCause());
        assertEquals(1, elements.released.get());
        assertEquals(BodyElements.State.FAILED, elements.state());
        assertInstanceOf(CancellationException.class, elements.failure());
        elements.give(unread);
        assertEquals(1, unread.releases.get());
        assertEquals(0, queued.releases.get());
        assertEquals(0, second.releases.get());
        assertThrows(IllegalStateException.class, elements::next);
        assertThrows(IllegalStateException.class, elements::poll);
    }

    @Test
    void closingReleasesTheElementsThatWereNotRead() {
        Source<Counted> elements = new Source<>();
        Counted queued = new Counted();
        elements.queue(queued);
        elements.close();
        assertEquals(1, queued.releases.get());
    }

    @Test
    void forEachClosesTheElementsWhenAConsumerFails() {
        Source<String> elements = new Source<>();
        elements.held.addAll(List.of("a", "b"));
        CompletableFuture<Void> done = elements.forEach(element -> CompletableFuture.failedFuture(new IllegalStateException("consumer"))).toCompletableFuture();
        CompletionException e = assertThrows(CompletionException.class, done::join);
        assertEquals("consumer", e.getCause().getMessage());
        assertEquals(1, elements.released.get());
        assertEquals(BodyElements.State.FAILED, elements.state());
    }

    @Test
    void forEachClosesTheElementsWhenReadingFails() {
        Source<String> elements = new Source<>();
        CompletableFuture<Void> done = elements.forEach(element -> CompletableFuture.completedFuture(null)).toCompletableFuture();
        elements.failWith(new IllegalStateException("source"));
        assertThrows(CompletionException.class, done::join);
        assertEquals(1, elements.released.get());
    }

    @Test
    void forEachRefusesAnOperationOfItsConsumer() {
        Source<String> elements = new Source<>();
        elements.held.add("a");
        CompletableFuture<Void> done = elements.forEach(element -> {
            elements.next();
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture();
        CompletionException e = assertThrows(CompletionException.class, done::join);
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void forEachConsumesManyAvailableElementsInALoop() {
        Source<Integer> elements = new Source<>();
        for (int i = 0; i < 100_000; i++) {
            elements.held.add(i);
        }
        elements.endWhenEmpty = true;
        AtomicInteger sum = new AtomicInteger();
        elements.forEach(element -> {
            sum.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture().join();
        assertEquals(100_000, sum.get());
    }

    @Test
    void forEachConsumesManySynchronouslyProducedElementsInALoop() {
        Source<Integer> elements = new Source<>();
        elements.produce = 100_000;
        List<Integer> consumed = new ArrayList<>();
        elements.forEach(element -> {
            consumed.add(element);
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture().join();
        assertEquals(100_000, consumed.size());
    }

    @Test
    void aContinuationOfACompletedReadMayReadTheNextElement() {
        Source<String> elements = new Source<>();
        CompletionStage<Optional<String>> both = elements.next().thenCompose(first -> elements.next());
        elements.give("a");
        elements.give("b");
        assertEquals(Optional.of("b"), both.toCompletableFuture().join());
    }

    private static final class Counted implements ReferenceCounted {
        final AtomicInteger releases = new AtomicInteger();

        @Override
        public ReferenceCounted retain() {
            return this;
        }

        @Override
        public boolean release() {
            releases.incrementAndGet();
            return true;
        }
    }

    /**
     * A source that holds elements it hands out at once, and answers the other reads when the
     * test gives an element, or produces them synchronously.
     */
    private static final class Source<T> extends PulledBodyElements<T> {
        final ArrayDeque<T> held = new ArrayDeque<>();
        final AtomicInteger demands = new AtomicInteger();
        final AtomicInteger released = new AtomicInteger();
        boolean endWhenEmpty;
        int produce;
        int produced;

        @Override
        protected T pollSource() {
            T element = held.poll();
            if (element == null && endWhenEmpty) {
                end();
            }
            return element;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void demand() {
            demands.incrementAndGet();
            if (produce > 0) {
                if (produced < produce) {
                    push((T) Integer.valueOf(produced++));
                } else {
                    end();
                }
            }
        }

        @Override
        protected void release() {
            released.incrementAndGet();
        }

        void give(T element) {
            push(element);
        }

        void queue(T element) {
            push(element);
        }

        void finish() {
            end();
        }

        void failWith(Throwable error) {
            fail(error);
        }
    }
}
