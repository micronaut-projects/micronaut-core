package io.micronaut.http.server.netty.multipart;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnicastFieldPublisherTest {

    @Test
    void buffersUntilRequested() {
        List<String> discarded = new ArrayList<>();
        UnicastFieldPublisher<String> publisher = new UnicastFieldPublisher<>(discarded::add, () -> {
        }, () -> {
        });
        assertTrue(publisher.offer("a"));
        assertTrue(publisher.offer("b"));
        publisher.complete();
        assertFalse(publisher.offer("c"));

        Recorder<String> recorder = new Recorder<>();
        publisher.subscribe(recorder);
        assertTrue(recorder.items.isEmpty());
        recorder.subscription.request(1);
        assertEquals(List.of("a"), recorder.items);
        assertFalse(recorder.complete);
        // the completion follows the buffered items
        recorder.subscription.request(1);
        assertEquals(List.of("a", "b"), recorder.items);
        assertTrue(recorder.complete);
        assertTrue(discarded.isEmpty());
    }

    @Test
    void startsOnSubscribeBeforeTheSubscription() {
        List<String> events = new ArrayList<>();
        AtomicReference<UnicastFieldPublisher<String>> holder = new AtomicReference<>();
        UnicastFieldPublisher<String> publisher = new UnicastFieldPublisher<>(s -> {
        }, () -> {
            events.add("start");
            // a producer that emits as it starts
            holder.get().offer("a");
        }, () -> {
        });
        holder.set(publisher);
        Recorder<String> recorder = new Recorder<>() {
            @Override
            public void onSubscribe(Subscription s) {
                events.add("subscribe");
                super.onSubscribe(s);
                s.request(Long.MAX_VALUE);
                // not delivered during onSubscribe
                assertTrue(items.isEmpty());
            }
        };
        publisher.subscribe(recorder);
        assertEquals(List.of("start", "subscribe"), events);
        assertEquals(List.of("a"), recorder.items);
        publisher.offer("b");
        assertEquals(List.of("a", "b"), recorder.items);
    }

    @Test
    void errorFollowsTheBufferedItems() {
        UnicastFieldPublisher<String> publisher = new UnicastFieldPublisher<>(s -> {
        }, () -> {
        }, () -> {
        });
        Recorder<String> recorder = new Recorder<>();
        publisher.subscribe(recorder);
        publisher.offer("a");
        RuntimeException error = new RuntimeException("test");
        assertTrue(publisher.error(error));
        assertFalse(publisher.error(new RuntimeException("second")));
        assertNull(recorder.error);
        recorder.subscription.request(1);
        assertEquals(List.of("a"), recorder.items);
        assertSame(error, recorder.error);
    }

    @Test
    void cancelDiscardsTheBufferedItems() {
        List<String> discarded = new ArrayList<>();
        AtomicInteger cancels = new AtomicInteger();
        UnicastFieldPublisher<String> publisher = new UnicastFieldPublisher<>(discarded::add, () -> {
        }, cancels::incrementAndGet);
        Recorder<String> recorder = new Recorder<>();
        publisher.subscribe(recorder);
        publisher.offer("a");
        publisher.offer("b");
        recorder.subscription.cancel();
        assertEquals(1, cancels.get());
        assertEquals(List.of("a", "b"), discarded);

        // refused afterwards: the caller releases the item
        assertFalse(publisher.offer("c"));
        assertFalse(publisher.error(new RuntimeException()));
        assertEquals(List.of("a", "b"), discarded);

        // the hook runs for every cancellation
        recorder.subscription.cancel();
        assertEquals(2, cancels.get());
        assertTrue(recorder.items.isEmpty());
        assertFalse(recorder.complete);
    }

    @Test
    void cancelDuringOnNextDiscardsTheRest() {
        List<String> discarded = new ArrayList<>();
        UnicastFieldPublisher<String> publisher = new UnicastFieldPublisher<>(discarded::add, () -> {
        }, () -> {
        });
        publisher.offer("a");
        publisher.offer("b");
        Recorder<String> recorder = new Recorder<>() {
            @Override
            public void onNext(String s) {
                super.onNext(s);
                subscription.cancel();
            }
        };
        publisher.subscribe(recorder);
        recorder.subscription.request(Long.MAX_VALUE);
        assertEquals(List.of("a"), recorder.items);
        assertEquals(List.of("b"), discarded);
    }

    @Test
    void refusesASecondSubscriber() {
        AtomicInteger starts = new AtomicInteger();
        UnicastFieldPublisher<String> publisher = new UnicastFieldPublisher<>(s -> {
        }, starts::incrementAndGet, () -> {
        });
        publisher.subscribe(new Recorder<>());
        Recorder<String> second = new Recorder<>();
        publisher.subscribe(second);
        assertInstanceOf(IllegalStateException.class, second.error);
        assertEquals(1, starts.get());
    }

    @Test
    void concurrentCancelReleasesEveryItem() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (int round = 0; round < 500; round++) {
                AtomicInteger offered = new AtomicInteger();
                AtomicInteger released = new AtomicInteger();
                UnicastFieldPublisher<Integer> publisher = new UnicastFieldPublisher<>(i -> released.incrementAndGet(), () -> {
                }, () -> {
                });
                Recorder<Integer> recorder = new Recorder<>() {
                    @Override
                    public void onNext(Integer i) {
                        released.incrementAndGet();
                    }
                };
                publisher.subscribe(recorder);
                CountDownLatch started = new CountDownLatch(1);
                executor.execute(() -> {
                    started.countDown();
                    recorder.subscription.request(5);
                    recorder.subscription.cancel();
                });
                started.await();
                for (int i = 0; i < 100; i++) {
                    if (publisher.offer(i)) {
                        offered.incrementAndGet();
                    }
                }
                executor.submit(() -> {
                }).get(10, TimeUnit.SECONDS);
                assertEquals(offered.get(), released.get(), "round " + round);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static class Recorder<T> implements Subscriber<T> {
        final List<T> items = new ArrayList<>();
        Subscription subscription;
        boolean complete;
        Throwable error;

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
            complete = true;
        }
    }
}
