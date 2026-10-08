package io.micronaut.http.server.multipart;

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormFieldFlowsTest {

    @Test
    void firstMapsTheFirstItemAndCancels() throws Exception {
        Source<String> source = new Source<>();
        CompletableFuture<Integer> result = FormFieldFlows.first(source, String::length);
        assertEquals(Long.MAX_VALUE, source.requested);
        assertFalse(result.isDone());

        source.next("abc");
        assertTrue(source.cancelled);
        assertEquals(3, result.get());

        // items after the first are ignored
        source.next("abcdef");
        source.complete();
        assertEquals(3, result.get());
    }

    @Test
    void firstOfAnEmptySourceIsNull() throws Exception {
        Source<String> source = new Source<>();
        CompletableFuture<Integer> result = FormFieldFlows.first(source, String::length);
        source.complete();
        assertNull(result.get());
    }

    @Test
    void firstFailsWithTheErrorOfTheSource() {
        Source<String> source = new Source<>();
        CompletableFuture<Integer> result = FormFieldFlows.first(source, String::length);
        RuntimeException error = new RuntimeException("test");
        source.error(error);
        assertSame(error, assertThrows(ExecutionException.class, result::get).getCause());
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    void firstFailsWhenTheMapperFails() {
        Source<String> source = new Source<>();
        RuntimeException error = new RuntimeException("test");
        CompletableFuture<Integer> result = FormFieldFlows.first(source, s -> {
            throw error;
        });
        source.next("abc");
        assertTrue(source.cancelled);
        assertSame(error, assertThrows(ExecutionException.class, result::get).getCause());
    }

    @Test
    void firstClosesTheItemWhenTheMapperFails() {
        Source<Item> source = new Source<>();
        CompletableFuture<Integer> result = FormFieldFlows.first(source, s -> {
            throw new RuntimeException("test");
        });
        Item item = new Item();
        source.next(item);
        assertTrue(result.isCompletedExceptionally());
        assertTrue(item.closed, "nobody took the item");
    }

    @Test
    void firstFlatMapClosesTheItemWhenTheMapperFails() {
        Source<Item> source = new Source<>();
        CompletableFuture<Integer> result = FormFieldFlows.firstFlatMap(source, s -> {
            throw new RuntimeException("test");
        });
        Item item = new Item();
        source.next(item);
        assertTrue(result.isCompletedExceptionally());
        assertTrue(item.closed, "nobody took the item");
    }

    @Test
    void cancellingTheResultOfFirstCancelsTheSource() {
        Source<Item> source = new Source<>();
        CompletableFuture<Integer> result = FormFieldFlows.first(source, item -> 1);
        assertFalse(source.cancelled);
        assertTrue(result.cancel(false));
        assertTrue(source.cancelled, "like the future of a Mono");
        // an item that arrives anyway is closed: nobody takes it
        Item late = new Item();
        source.next(late);
        assertTrue(late.closed);
        assertTrue(result.isCancelled());
    }

    @Test
    void firstCancelledBeforeTheSubscriptionCancelsIt() {
        AtomicReference<Subscriber<? super String>> subscriber = new AtomicReference<>();
        Publisher<String> lazy = subscriber::set;
        CompletableFuture<Integer> result = FormFieldFlows.first(lazy, String::length);
        result.cancel(false);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicLong requested = new AtomicLong();
        subscriber.get().onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                requested.addAndGet(n);
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
        assertTrue(cancelled.get());
        assertEquals(0, requested.get());
    }

    @Test
    void cancellingTheResultOfFirstFlatMapCancelsTheFlow() {
        Source<String> source = new Source<>();
        DelayedExecutionFlow<Integer> flow = DelayedExecutionFlow.create();
        AtomicBoolean flowCancelled = new AtomicBoolean();
        flow.onCancel(() -> flowCancelled.set(true));
        CompletableFuture<Integer> result = FormFieldFlows.firstFlatMap(source, s -> flow);
        source.next("abc");
        assertFalse(flowCancelled.get());
        result.cancel(false);
        assertTrue(flowCancelled.get(), "the flow of the item is cancelled");
    }

    @Test
    void firstFlatMapCancelledWhileTheFlowStartsCancelsTheFlow() {
        Source<String> source = new Source<>();
        DelayedExecutionFlow<Integer> flow = DelayedExecutionFlow.create();
        AtomicBoolean flowCancelled = new AtomicBoolean();
        flow.onCancel(() -> flowCancelled.set(true));
        AtomicReference<CompletableFuture<Integer>> result = new AtomicReference<>();
        result.set(FormFieldFlows.firstFlatMap(source, s -> {
            // cancelled after the item was taken, before its flow is known
            result.get().cancel(false);
            return flow;
        }));
        source.next("abc");
        assertTrue(flowCancelled.get());
    }

    @Test
    void concatCancelledWhileAFlowStartsCancelsTheFlow() {
        Source<String> source = new Source<>();
        DelayedExecutionFlow<Integer> flow = DelayedExecutionFlow.create();
        AtomicBoolean flowCancelled = new AtomicBoolean();
        flow.onCancel(() -> flowCancelled.set(true));
        AtomicReference<FormFieldFlows.Concat<String, Integer>> reading = new AtomicReference<>();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> {
            // the reading is cancelled after the item was taken, before its flow is known
            reading.get().cancel();
            return flow;
        }, s -> {
        }, v -> {
        }, e -> {
        });
        reading.set(concat);
        source.subscribe(concat);
        source.next("a");
        assertTrue(source.cancelled);
        assertTrue(flowCancelled.get(), "the flow that started is cancelled");
    }

    private static final class Item implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void firstFlatMapCompletesWithTheFlow() throws Exception {
        Source<String> source = new Source<>();
        DelayedExecutionFlow<Integer> flow = DelayedExecutionFlow.create();
        CompletableFuture<String> result = FormFieldFlows.firstFlatMap(source, s -> flow, i -> "length " + i);
        source.next("abc");
        assertTrue(source.cancelled);
        assertFalse(result.isDone());
        flow.complete(3);
        assertEquals("length 3", result.get());
    }

    @Test
    void firstFlatMapOfAnEmptyFlowIsNullAndNotMapped() throws Exception {
        Source<String> source = new Source<>();
        AtomicBoolean mapped = new AtomicBoolean();
        CompletableFuture<String> result = FormFieldFlows.firstFlatMap(source, s -> ExecutionFlow.<Integer>empty(), i -> {
            mapped.set(true);
            return "length " + i;
        });
        source.next("abc");
        assertNull(result.get());
        assertFalse(mapped.get());
    }

    @Test
    void firstFlatMapFailsWithTheErrorOfTheFlow() {
        Source<String> source = new Source<>();
        RuntimeException error = new RuntimeException("test");
        CompletableFuture<Integer> result = FormFieldFlows.firstFlatMap(source, s -> ExecutionFlow.error(error));
        source.next("abc");
        assertSame(error, assertThrows(ExecutionException.class, result::get).getCause());
    }

    @Test
    void concatCompletesTheItemsInOrderOneAtATime() {
        Source<String> source = new Source<>();
        List<DelayedExecutionFlow<Integer>> flows = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        AtomicReference<Throwable> done = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> {
            DelayedExecutionFlow<Integer> flow = DelayedExecutionFlow.create();
            flows.add(flow);
            return flow;
        }, s -> {
        }, values::add, e -> {
            done.set(e);
            finished.set(true);
        });
        source.subscribe(concat);
        assertEquals(1, source.requested);

        source.next("a");
        assertEquals(1, flows.size());
        // the next item is requested once the item completed
        assertEquals(1, source.requested);
        flows.get(0).complete(1);
        assertEquals(2, source.requested);

        source.next("bb");
        // the source completes while the last item is completed
        source.complete();
        assertFalse(finished.get());
        flows.get(1).complete(2);

        assertEquals(List.of(1, 2), values);
        assertTrue(finished.get());
        assertNull(done.get());
    }

    @Test
    void concatOfAnImmediateFlowRequestsTheNextItem() {
        Source<String> source = new Source<>();
        List<Integer> values = new ArrayList<>();
        AtomicBoolean finished = new AtomicBoolean();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> ExecutionFlow.just(s.length()), s -> {
        }, values::add, e -> finished.set(true));
        source.subscribe(concat);
        source.next("a");
        source.next("bb");
        assertEquals(3, source.requested);
        source.complete();
        assertEquals(List.of(1, 2), values);
        assertTrue(finished.get());
    }

    @Test
    void concatErrorOfAFlowCancelsTheSource() {
        Source<String> source = new Source<>();
        RuntimeException error = new RuntimeException("test");
        AtomicReference<Throwable> done = new AtomicReference<>();
        List<String> discarded = new ArrayList<>();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> ExecutionFlow.error(error),
            discarded::add, i -> {
        }, done::set);
        source.subscribe(concat);
        source.next("a");
        assertTrue(source.cancelled);
        assertSame(error, done.get());

        // an item that arrives afterwards is discarded
        source.next("b");
        assertEquals(List.of("b"), discarded);
    }

    @Test
    void concatErrorOfTheSourceCancelsTheRunningFlow() {
        Source<String> source = new Source<>();
        DelayedExecutionFlow<Integer> flow = DelayedExecutionFlow.create();
        AtomicReference<Throwable> done = new AtomicReference<>();
        List<Integer> values = new ArrayList<>();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> flow, s -> {
        }, values::add, done::set);
        source.subscribe(concat);
        source.next("a");
        RuntimeException error = new RuntimeException("test");
        source.error(error);
        assertSame(error, done.get());
        assertTrue(flow.isCancelled());
        assertTrue(values.isEmpty());
    }

    @Test
    void concatMapperErrorDiscardsTheItem() {
        Source<String> source = new Source<>();
        RuntimeException error = new RuntimeException("test");
        AtomicReference<Throwable> done = new AtomicReference<>();
        List<String> discarded = new ArrayList<>();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> {
            throw error;
        }, discarded::add, i -> {
        }, done::set);
        source.subscribe(concat);
        source.next("a");
        assertEquals(List.of("a"), discarded);
        assertTrue(source.cancelled);
        assertSame(error, done.get());
    }

    @Test
    void concatCancelStopsTheReading() {
        Source<String> source = new Source<>();
        DelayedExecutionFlow<Integer> flow = DelayedExecutionFlow.create();
        AtomicBoolean finished = new AtomicBoolean();
        List<Integer> values = new ArrayList<>();
        List<String> discarded = new ArrayList<>();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> flow,
            discarded::add, values::add, e -> finished.set(true));
        source.subscribe(concat);
        source.next("a");
        concat.cancel();
        assertTrue(source.cancelled);
        assertTrue(flow.isCancelled());

        // nothing is delivered afterwards
        source.next("b");
        source.complete();
        assertEquals(List.of("b"), discarded);
        assertTrue(values.isEmpty());
        assertFalse(finished.get());
    }

    @Test
    void concatCancelledBeforeTheSubscriptionCancelsIt() {
        Source<String> source = new Source<>();
        AtomicBoolean finished = new AtomicBoolean();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> ExecutionFlow.just(s.length()), s -> {
        }, v -> {
        }, e -> finished.set(true));
        concat.cancel();
        source.subscribe(concat);
        assertTrue(source.cancelled);
        assertEquals(0, source.requested);
        assertFalse(finished.get());
    }

    @Test
    void concatValueMayCancel() {
        Source<String> source = new Source<>();
        AtomicReference<FormFieldFlows.Concat<String, Integer>> self = new AtomicReference<>();
        List<Integer> values = new ArrayList<>();
        AtomicBoolean finished = new AtomicBoolean();
        FormFieldFlows.Concat<String, Integer> concat = new FormFieldFlows.Concat<>(s -> ExecutionFlow.just(s.length()), s -> {
        }, v -> {
            values.add(v);
            self.get().cancel();
        }, e -> finished.set(true));
        self.set(concat);
        source.subscribe(concat);
        source.next("a");
        assertTrue(source.cancelled);
        // not requested again after the cancellation
        assertEquals(1, source.requested);
        assertEquals(List.of(1), values);
        assertFalse(finished.get());
    }

    /**
     * A source driven by the test: it records the demand and the cancellation, and does not
     * check them.
     *
     * @param <T> The type of the items
     */
    private static final class Source<T> implements Publisher<T> {
        Subscriber<? super T> subscriber;
        long requested;
        boolean cancelled;

        @Override
        public void subscribe(Subscriber<? super T> s) {
            subscriber = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested = requested + n < 0 ? Long.MAX_VALUE : requested + n;
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        void next(T item) {
            subscriber.onNext(item);
        }

        void complete() {
            subscriber.onComplete();
        }

        void error(Throwable e) {
            subscriber.onError(e);
        }
    }
}
