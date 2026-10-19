package io.micronaut.http.body.stream;

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
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    void invalidDemandReleasesThePendingLastItemAfterSourceCompletion() {
        List<String> discarded = new ArrayList<>();
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.append(Flux.<String>empty(), "last", discarded::add).subscribe(recorder);
        recorder.subscription.request(0);
        recorder.subscription.request(-1);
        assertInstanceOf(IllegalArgumentException.class, recorder.error);
        assertEquals(List.of("last"), discarded);
        assertTrue(recorder.items.isEmpty());
        assertFalse(recorder.completed);
    }

    @Test
    void invalidDemandReleasesThePendingFirstItemAfterSourceCompletion() {
        List<String> discarded = new ArrayList<>();
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.awaitFirst(Flux.just("first"), discarded::add).tryCompleteValue().subscribe(recorder);
        recorder.subscription.request(0);
        recorder.subscription.request(-1);
        assertInstanceOf(IllegalArgumentException.class, recorder.error);
        assertEquals(List.of("first"), discarded);
        assertTrue(recorder.items.isEmpty());
        assertFalse(recorder.completed);
    }

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
    void awaitFirstCancelsASubscriptionArrivingAfterAbandonment() {
        var subscriber = new AtomicReference<Subscriber<? super String>>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        Publisher<String> source = subscriber::set;
        ExecutionFlow<Publisher<String>> flow = BodyPublishers.awaitFirst(source, item -> { });
        flow.cancel();
        subscriber.get().onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                requests.incrementAndGet();
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
        assertTrue(cancelled.get());
        assertEquals(0, requests.get());
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
        BodyPublishers.Unicast<String> unicast = unicast(item -> { }, () -> subscribed.set(true), () -> { });
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
        BodyPublishers.Unicast<String> unicast = unicast(discarded::add, () -> { }, () -> cancelled.set(true));
        Recorder<String> recorder = new Recorder<>();
        unicast.subscribe(recorder);
        unicast.tryNext("a");
        unicast.tryNext("b");

        recorder.subscription.cancel();

        assertEquals(List.of("a", "b"), discarded);
        assertTrue(cancelled.get());
        assertFalse(unicast.tryNext("c"));
    }

    @Test
    void cancellingTheFlowOfAwaitFirstBeforeAnyoneSubscribedCancelsTheSource() {
        List<String> discarded = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        ExecutionFlow<Publisher<String>> flow = BodyPublishers.awaitFirst(Flux.just("a", "b").doOnCancel(() -> cancelled.set(true)), discarded::add);
        Publisher<String> items = flow.tryCompleteValue();

        flow.cancel();

        assertTrue(cancelled.get());
        assertEquals(List.of("a"), discarded);
        // a late subscriber is failed, not left waiting
        Recorder<String> late = new Recorder<>();
        items.subscribe(late);
        assertInstanceOf(CancellationException.class, late.error);
    }

    @Test
    void cancellingTheFlowOfAwaitFirstAfterTheSubscriptionLeavesTheItemsToTheSubscriber() {
        ExecutionFlow<Publisher<String>> flow = BodyPublishers.awaitFirst(Flux.just("a", "b"), item -> { });
        Recorder<String> recorder = new Recorder<>();
        flow.tryCompleteValue().subscribe(recorder);

        flow.cancel();

        recorder.subscription.request(5);
        assertEquals(List.of("a", "b"), recorder.items);
        assertTrue(recorder.completed);
    }

    @Test
    void anItemThatArrivesAfterAwaitFirstWasCancelledIsReleased() {
        List<String> discarded = new ArrayList<>();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        ExecutionFlow<Publisher<String>> flow = BodyPublishers.awaitFirst(new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> s) {
                // not a Reactor publisher: it delivers what it has even after the cancellation
                sink.asFlux().subscribe(new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        s.onSubscribe(new Subscription() {
                            @Override
                            public void request(long n) {
                                subscription.request(n);
                            }

                            @Override
                            public void cancel() {
                                // ignored, as a slow source may
                            }
                        });
                    }

                    @Override
                    public void onNext(String item) {
                        s.onNext(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        s.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        s.onComplete();
                    }
                });
            }
        }, discarded::add);

        flow.cancel();
        sink.tryEmitNext("late");

        assertEquals(List.of("late"), discarded);
    }

    @Test
    void justFailsARequestForNoItems() {
        List<String> discarded = new ArrayList<>();
        Recorder<String> recorder = new Recorder<>();
        BodyPublishers.just("a", discarded::add).subscribe(recorder);

        recorder.subscription.request(0);

        assertInstanceOf(IllegalArgumentException.class, recorder.error);
        assertEquals(List.of("a"), discarded);
        assertEquals(List.of(), recorder.items);
    }

    @Test
    void aUnicastFailsARequestForNoItemsAndReleasesTheQueuedItems() {
        List<String> discarded = new ArrayList<>();
        AtomicInteger cancelled = new AtomicInteger();
        BodyPublishers.Unicast<String> unicast = unicast(discarded::add, () -> { }, cancelled::incrementAndGet);
        Recorder<String> recorder = new Recorder<>();
        unicast.subscribe(recorder);
        unicast.tryNext("a");

        recorder.subscription.request(-1);

        assertInstanceOf(IllegalArgumentException.class, recorder.error);
        assertEquals(List.of("a"), discarded);
        assertEquals(1, cancelled.get());
        assertFalse(unicast.tryNext("b"));
        recorder.subscription.cancel();
        assertEquals(1, cancelled.get());
    }

    @Test
    void aUnicastDoesNotSignalWhileTheSubscriberRunsOnSubscribe() throws Exception {
        BodyPublishers.Unicast<String> unicast = unicast(item -> { }, () -> { }, () -> { });
        AtomicBoolean inOnSubscribe = new AtomicBoolean();
        AtomicBoolean violation = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        unicast.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                inOnSubscribe.set(true);
                // the items end on another thread, e.g. an event loop, while onSubscribe runs
                Thread thread = new Thread(unicast::tryComplete);
                thread.start();
                try {
                    thread.join(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                inOnSubscribe.set(false);
            }

            @Override
            public void onNext(String s) {
                // This completion-ordering fixture never emits an item.
            }

            @Override
            public void onError(Throwable t) {
                throw new AssertionError("The completion-ordering fixture must not fail", t);
            }

            @Override
            public void onComplete() {
                violation.compareAndSet(false, inOnSubscribe.get());
                completed.set(true);
            }
        });

        assertTrue(completed.get());
        assertFalse(violation.get());
    }

    private static <T> BodyPublishers.Unicast<T> unicast(Consumer<T> discard, Runnable onSubscribe, Runnable onCancel) {
        return new BodyPublishers.Unicast<>() {
            @Override
            protected void onSubscribing() {
                onSubscribe.run();
            }

            @Override
            protected void discard(T item) {
                discard.accept(item);
            }

            @Override
            protected void onCancelled() {
                onCancel.run();
            }
        };
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
