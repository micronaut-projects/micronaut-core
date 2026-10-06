package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publisher of the buffers of a streaming body.
 */
class AsPublisherTest {

    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void buffersAreDeliveredOnDemandAndTheCompletionWithoutDemand() {
        RecordingUpstream upstream = new RecordingUpstream();
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        Recorder reader = new Recorder();
        body.rootBody().toReadBufferPublisher().subscribe(reader);
        body.sharedBuffer().add(buffer("a"));
        body.sharedBuffer().add(buffer("bc"));
        body.sharedBuffer().complete();

        assertTrue(upstream.started);
        assertEquals(List.of(), reader.items);

        reader.subscription.request(1);
        assertEquals(List.of("a"), reader.items);
        assertFalse(reader.completed);

        reader.subscription.request(1);
        assertEquals(List.of("a", "bc"), reader.items);
        assertTrue(reader.completed);
        assertEquals(3, upstream.consumed);
    }

    @Test
    void aFailureIsDeliveredAfterTheQueuedBuffers() {
        RecordingUpstream upstream = new RecordingUpstream();
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        Recorder reader = new Recorder();
        InternalByteBody.toUnbufferedReadBufferPublisher(body.rootBody()).subscribe(reader);
        IllegalStateException failure = new IllegalStateException("boom");
        body.sharedBuffer().add(buffer("a"));
        body.sharedBuffer().error(failure);

        assertNull(reader.error);

        reader.subscription.request(1);
        assertEquals(List.of("a"), reader.items);
        assertSame(failure, reader.error);
        // a reader that failed reads nothing more
        assertTrue(upstream.discardAllowed);
        assertTrue(upstream.backpressureDisregarded);
    }

    @Test
    void cancellingClosesTheQueuedBuffersAndAllowsDiscard() {
        RecordingUpstream upstream = new RecordingUpstream();
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        Recorder reader = new Recorder();
        body.rootBody().toReadBufferPublisher().subscribe(reader);
        List<ReadBuffer> added = List.of(buffer("a"), buffer("b"));
        added.forEach(body.sharedBuffer()::add);

        reader.subscription.cancel();

        assertTrue(upstream.discardAllowed);
        assertTrue(upstream.backpressureDisregarded);
        assertEquals(List.of(), reader.items);

        // a buffer that arrives after the cancellation is not delivered
        body.sharedBuffer().add(buffer("c"));
        reader.subscription.request(10);
        assertEquals(List.of(), reader.items);
    }

    @Test
    void aSecondSubscriberIsRejected() {
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, new RecordingUpstream());
        BaseSharedBuffer.AsPublisher publisher = new BaseSharedBuffer.AsPublisher(body.sharedBuffer());
        publisher.publisher(body.rootBody().primary(publisher));
        publisher.subscribe(new Recorder());
        Recorder second = new Recorder();

        publisher.subscribe(second);

        assertInstanceOf(IllegalStateException.class, second.error);
    }

    @Test
    void aReaderOverTheBufferLimitFails() {
        RecordingUpstream upstream = new RecordingUpstream();
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 2), upstream);
        Recorder reader = new Recorder();
        body.rootBody().toReadBufferPublisher().subscribe(reader);

        body.sharedBuffer().add(buffer("abc"));

        assertInstanceOf(BufferLengthExceededException.class, reader.error);
    }

    @Test
    void aReaderMayRequestFromOnNextWithoutReentrantDelivery() {
        RecordingUpstream upstream = new RecordingUpstream();
        ByteBodyFactory.StreamingBody body = FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
        int[] depth = new int[1];
        int[] maxDepth = new int[1];
        Recorder reader = new Recorder();
        reader.onItem = r -> {
            depth[0]++;
            maxDepth[0] = Math.max(maxDepth[0], depth[0]);
            r.subscription.request(1);
            depth[0]--;
        };
        body.rootBody().toReadBufferPublisher().subscribe(reader);
        for (int i = 0; i < 1000; i++) {
            body.sharedBuffer().add(buffer("x"));
        }
        body.sharedBuffer().complete();

        reader.subscription.request(1);

        assertEquals(1000, reader.items.size());
        assertTrue(reader.completed);
        assertEquals(1, maxDepth[0]);
    }

    private static ReadBuffer buffer(String text) {
        return ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8);
    }

    private static final class RecordingUpstream implements BufferConsumer.Upstream {
        boolean started;
        long consumed;
        boolean discardAllowed;
        boolean backpressureDisregarded;

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            consumed += bytesConsumed;
        }

        @Override
        public void allowDiscard() {
            discardAllowed = true;
        }

        @Override
        public void disregardBackpressure() {
            backpressureDisregarded = true;
        }
    }

    private static final class Recorder implements Subscriber<ReadBuffer> {
        Subscription subscription;
        final List<String> items = new ArrayList<>();
        Throwable error;
        boolean completed;
        Consumer<Recorder> onItem = r -> { };

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            try (buffer) {
                items.add(buffer.toString(StandardCharsets.UTF_8));
            }
            onItem.accept(this);
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
