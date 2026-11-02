package io.micronaut.http.client.jdk;

import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The read timeout of the JDK raw client must not signal the body consumer while it handles a
 * piece of the body, nor count the time it takes against the upstream.
 */
class ByteBodySubscriberTimeoutTest {

    @Test
    void theTimeoutDoesNotSignalWhileTheConsumerHandlesData() throws Exception {
        ByteBodySubscriber subscriber = new ByteBodySubscriber(BodySizeLimits.UNLIMITED, Duration.ofMillis(100));
        AtomicLong requested = new AtomicLong();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                requested.addAndGet(n);
            }

            @Override
            public void cancel() {
            }
        });
        CloseableByteBody body = subscriber.getBody().toCompletableFuture().join();
        AtomicBoolean inNext = new AtomicBoolean();
        AtomicBoolean errored = new AtomicBoolean();
        CompletableFuture<Boolean> errorDuringNext = new CompletableFuture<>();
        CompletableFuture<Void> nextDone = new CompletableFuture<>();
        body.toByteArrayPublisher().subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(2);
            }

            @Override
            public void onNext(byte[] bytes) {
                inNext.set(true);
                try {
                    // slower than the read timeout
                    TimeUnit.MILLISECONDS.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                inNext.set(false);
                nextDone.complete(null);
            }

            @Override
            public void onError(Throwable t) {
                errored.set(true);
                errorDuringNext.complete(inNext.get());
            }

            @Override
            public void onComplete() {
                errorDuringNext.complete(false);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (requested.get() == 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        Assertions.assertTrue(requested.get() > 0, "no demand");
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        nextDone.get(5, TimeUnit.SECONDS);
        // the upstream delivers the rest as soon as the consumer returns: it never stalled
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {4, 5, 6})));
        subscriber.onComplete();
        Boolean result = errorDuringNext.get(5, TimeUnit.SECONDS);
        Assertions.assertFalse(result, "onError while onNext ran");
        Assertions.assertFalse(errored.get(), "the time the consumer took counted as a read timeout");
    }
}
