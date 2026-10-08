package io.micronaut.http.body.stream;

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publishers of the pieces of bodies, without Reactor operators.
 */
class BodyPublishersTest {

    @Test
    void mapMapsUnderTheDemandOfTheSubscriber() {
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.map(Flux.range(1, 3), i -> "v" + i).subscribe(recorder);

        recorder.subscription.request(2);
        assertEquals(List.of("v1", "v2"), recorder.items);
        assertFalse(recorder.completed);

        recorder.subscription.request(1);
        assertEquals(List.of("v1", "v2", "v3"), recorder.items);
        assertTrue(recorder.completed);
    }

    @Test
    void aMapperThatThrowsCancelsTheSourceAndFails() {
        AtomicBoolean cancelled = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("boom");
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.map(Flux.range(1, 3).doOnCancel(() -> cancelled.set(true)), i -> {
            if (i == 2) {
                throw failure;
            }
            return "v" + i;
        }).subscribe(recorder);

        recorder.subscription.request(10);

        assertEquals(List.of("v1"), recorder.items);
        assertSame(failure, recorder.error);
        assertTrue(cancelled.get());
    }

    @Test
    void mapPassesTheContextOfTheSubscriberToAReactorSource() {
        List<Object> discarded = new ArrayList<>();
        Recorder<String> recorder = new Recorder<>(Context.of("reactor.onDiscard.local", (Consumer<Object>) discarded::add));
        Sinks.Many<Integer> sink = Sinks.many().unicast().onBackpressureBuffer();
        BodyPublishers.map(sink.asFlux(), i -> "v" + i).subscribe(recorder);
        sink.tryEmitNext(1);
        sink.tryEmitNext(2);

        recorder.subscription.cancel();

        assertEquals(List.of(1, 2), discarded);
    }

    @Test
    void mapWithADiscardReleasesWhatAReactorSourceDrops() {
        List<Object> discarded = new ArrayList<>();
        Recorder<String> recorder = new Recorder<>();
        Sinks.Many<Integer> sink = Sinks.many().unicast().onBackpressureBuffer();
        BodyPublishers.map(sink.asFlux(), i -> "v" + i, discarded::add).subscribe(recorder);
        sink.tryEmitNext(1);
        sink.tryEmitNext(2);

        recorder.subscription.cancel();

        assertEquals(List.of(1, 2), discarded);
    }

    @Test
    void justDeliversItsItemOnceRequested() {
        Recorder<String> recorder = new Recorder<>();
        Publisher<String> just = BodyPublishers.just("a", item -> { });
        just.subscribe(recorder);

        assertEquals(List.of(), recorder.items);
        recorder.subscription.request(1);
        assertEquals(List.of("a"), recorder.items);
        assertTrue(recorder.completed);

        Recorder<String> second = new Recorder<>();
        just.subscribe(second);
        assertInstanceOf(IllegalStateException.class, second.error);
    }

    @Test
    void justReleasesItsItemWhenCancelledBeforeItWasTaken() {
        List<String> discarded = new ArrayList<>();
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.just("a", discarded::add).subscribe(recorder);

        recorder.subscription.cancel();
        recorder.subscription.request(1);

        assertEquals(List.of("a"), discarded);
        assertEquals(List.of(), recorder.items);
    }

    @Test
    void appendDeliversTheLastItemOnceTheSourceCompletedAndItIsRequested() {
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.append(Flux.just("a", "b"), "end", item -> { }).subscribe(recorder);

        recorder.subscription.request(2);
        assertEquals(List.of("a", "b"), recorder.items);
        assertFalse(recorder.completed);

        recorder.subscription.request(1);
        assertEquals(List.of("a", "b", "end"), recorder.items);
        assertTrue(recorder.completed);
    }

    @Test
    void appendReleasesTheLastItemWhenItIsNotDelivered() {
        List<String> discarded = new ArrayList<>();
        Recorder<String> cancelled = new Recorder<>();
        BodyPublishers.append(Flux.just("a"), "end", discarded::add).subscribe(cancelled);
        cancelled.subscription.request(1);
        cancelled.subscription.cancel();

        IllegalStateException failure = new IllegalStateException("boom");
        Recorder<String> failed = new Recorder<>();
        BodyPublishers.append(Flux.<String>error(failure), "end2", discarded::add).subscribe(failed);

        assertEquals(List.of("end", "end2"), discarded);
        assertSame(failure, failed.error);
    }

    @Test
    void awaitFirstCompletesWithTheFirstItemAndPublishesAllItems() {
        AtomicInteger requests = new AtomicInteger();
        ExecutionFlow<Publisher<String>> flow = BodyPublishers.awaitFirst(Flux.just("a", "b", "c").doOnRequest(n -> requests.incrementAndGet()), item -> { });
        assertEquals(1, requests.get());

        Recorder<String> recorder = new Recorder<>();
        flow.tryCompleteValue().subscribe(recorder);
        recorder.subscription.request(2);
        assertEquals(List.of("a", "b"), recorder.items);

        recorder.subscription.request(5);
        assertEquals(List.of("a", "b", "c"), recorder.items);
        assertTrue(recorder.completed);
    }

    @Test
    void awaitFirstFailsWithAFailureBeforeTheFirstItem() {
        IllegalStateException failure = new IllegalStateException("boom");
        assertSame(failure, BodyPublishers.awaitFirst(Flux.error(failure), item -> { }).tryCompleteError());
    }

    @Test
    void awaitFirstOfAnEmptySourceCompletesAndPublishesTheEnd() {
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.awaitFirst(Flux.<String>empty(), item -> { }).tryCompleteValue().subscribe(recorder);
        assertTrue(recorder.completed);
    }

    @Test
    void awaitFirstDeliversAFailureAfterTheFirstItem() {
        IllegalStateException failure = new IllegalStateException("boom");
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.awaitFirst(Flux.just("a").concatWith(Flux.error(failure)), item -> { }).tryCompleteValue().subscribe(recorder);

        assertNull(recorder.error);
        recorder.subscription.request(1);
        assertEquals(List.of("a"), recorder.items);
        assertSame(failure, recorder.error);
    }

    @Test
    void awaitFirstReleasesTheFirstItemWhenCancelledBeforeItWasTaken() {
        List<String> discarded = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.awaitFirst(Flux.just("a", "b").doOnCancel(() -> cancelled.set(true)), discarded::add).tryCompleteValue().subscribe(recorder);

        recorder.subscription.cancel();

        assertEquals(List.of("a"), discarded);
        assertTrue(cancelled.get());
    }

    @Test
    void unicastQueuesItemsAndDeliversAFailureAfterThem() {
        AtomicBoolean subscribed = new AtomicBoolean();
        BodyPublishers.Unicast<String> unicast = new BodyPublishers.Unicast<>(item -> { }, () -> subscribed.set(true), () -> { });
        assertTrue(unicast.tryNext("a"));
        IllegalStateException failure = new IllegalStateException("boom");
        assertTrue(unicast.tryError(failure));
        assertFalse(unicast.tryComplete());

        Recorder<String> recorder = new Recorder<>();
        unicast.subscribe(recorder);
        assertTrue(subscribed.get());
        assertNull(recorder.error);

        recorder.subscription.request(1);
        assertEquals(List.of("a"), recorder.items);
        assertSame(failure, recorder.error);

        Recorder<String> second = new Recorder<>();
        unicast.subscribe(second);
        assertInstanceOf(IllegalStateException.class, second.error);
    }

    @Test
    void cancellingAUnicastReleasesTheQueuedItems() {
        List<String> discarded = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        BodyPublishers.Unicast<String> unicast = new BodyPublishers.Unicast<>(discarded::add, () -> { }, () -> cancelled.set(true));
        Recorder<String> recorder = new Recorder<>();
        unicast.subscribe(recorder);
        unicast.tryNext("a");
        unicast.tryNext("b");

        recorder.subscription.cancel();

        assertEquals(List.of("a", "b"), discarded);
        assertTrue(cancelled.get());
        assertFalse(unicast.tryNext("c"));
    }

    private static final class Recorder<T> implements CoreSubscriber<T> {
        private final Context context;
        Subscription subscription;
        final List<T> items = new ArrayList<>();
        Throwable error;
        boolean completed;

        Recorder() {
            this(Context.empty());
        }

        Recorder(Context context) {
            this.context = context;
        }

        @Override
        public Context currentContext() {
            return context;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
        }

        @Override
        public void onNext(T t) {
            items.add(t);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}
