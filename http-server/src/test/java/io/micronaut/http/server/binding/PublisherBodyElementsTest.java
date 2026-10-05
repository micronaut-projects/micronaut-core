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

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The elements of a body read one at a time from a publisher: consumers that complete later or
 * fail, reads that overlap, closing while a read waits, and the end of the upstream.
 */
class PublisherBodyElementsTest {

    private static Throwable failure(CompletionStage<?> stage) {
        CompletionException e = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        return e.getCause();
    }

    @Test
    void aConsumerThatCompletesLaterGetsTheNextElementThen() {
        ManualPublisher publisher = new ManualPublisher();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        List<String> seen = new ArrayList<>();
        List<CompletableFuture<Void>> stages = new ArrayList<>();
        CompletionStage<Void> done = elements.forEach(element -> {
            seen.add(element);
            CompletableFuture<Void> stage = new CompletableFuture<>();
            stages.add(stage);
            return stage;
        });
        publisher.emit("a");
        assertEquals(List.of("a"), seen);
        stages.get(0).complete(null);
        publisher.emit("b");
        assertEquals(List.of("a", "b"), seen);
        stages.get(1).complete(null);
        publisher.complete();
        assertTrue(done.toCompletableFuture().isDone());
        done.toCompletableFuture().join();
    }

    @Test
    void aConsumerThatFailsLaterFailsTheWalk() {
        ManualPublisher publisher = new ManualPublisher();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        CompletableFuture<Void> stage = new CompletableFuture<>();
        CompletionStage<Void> done = elements.forEach(element -> stage);
        publisher.emit("a");
        IllegalStateException error = new IllegalStateException("consumer failed");
        stage.completeExceptionally(error);
        assertSame(error, failure(done));
    }

    @Test
    void aConsumerThatFailsAtOnceFailsTheWalk() {
        ManualPublisher publisher = new ManualPublisher();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        IllegalStateException error = new IllegalStateException("consumer failed");
        CompletionStage<Void> done = elements.forEach(element -> CompletableFuture.failedFuture(error));
        publisher.emit("a");
        assertSame(error, failure(done));
    }

    @Test
    void aConsumerThatThrowsFailsTheWalk() {
        ManualPublisher publisher = new ManualPublisher();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        IllegalStateException error = new IllegalStateException("consumer threw");
        CompletionStage<Void> done = elements.forEach(element -> {
            throw error;
        });
        publisher.emit("a");
        assertSame(error, failure(done));
    }

    @Test
    void overlappingReadsAreRefused() {
        ManualPublisher publisher = new ManualPublisher();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        CompletionStage<Optional<String>> first = elements.next();
        assertThrows(IllegalStateException.class, elements::next);
        publisher.emit("a");
        assertEquals(Optional.of("a"), first.toCompletableFuture().join());
        publisher.error(new IllegalStateException("upstream failed"));
        assertEquals("upstream failed", failure(elements.next()).getMessage());
        // signals after the end are ignored
        publisher.emit("b");
        publisher.complete();
    }

    @Test
    void closingFailsTheReadThatWaitsAndCancelsTheUpstream() {
        ManualPublisher publisher = new ManualPublisher();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        CompletionStage<Optional<String>> next = elements.next();
        CompletionStage<Void> closed = elements.closeAsync();
        assertSame(closed, elements.closeAsync());
        assertInstanceOf(CancellationException.class, failure(next));
        assertTrue(publisher.cancelled);
        assertThrows(IllegalStateException.class, elements::next);
        assertThrows(IllegalStateException.class, () -> elements.forEach(element -> CompletableFuture.completedFuture(null)));
    }

    @Test
    void closingFailsTheWalkThatWaits() {
        ManualPublisher publisher = new ManualPublisher();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> publisher, () -> { });
        CompletionStage<Void> done = elements.forEach(element -> CompletableFuture.completedFuture(null));
        elements.close();
        assertInstanceOf(CancellationException.class, failure(done));
        assertTrue(publisher.cancelled);
    }

    @Test
    void closingBeforeTheFirstReadDiscardsTheBody() {
        AtomicBoolean discarded = new AtomicBoolean();
        AtomicBoolean created = new AtomicBoolean();
        PublisherBodyElements<String> elements = new PublisherBodyElements<>(() -> {
            created.set(true);
            return new ManualPublisher();
        }, () -> discarded.set(true));
        elements.closeAsync().toCompletableFuture().join();
        assertTrue(discarded.get());
        assertFalse(created.get());
    }

    /**
     * Emits the elements the test gives it, and records the cancellation.
     */
    private static final class ManualPublisher implements Publisher<String> {
        private Subscriber<? super String> subscriber;
        private boolean cancelled;

        @Override
        public void subscribe(Subscriber<? super String> s) {
            subscriber = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    // the test emits the elements itself
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        void emit(String element) {
            subscriber.onNext(element);
        }

        void complete() {
            subscriber.onComplete();
        }

        void error(Throwable t) {
            subscriber.onError(t);
        }
    }
}
