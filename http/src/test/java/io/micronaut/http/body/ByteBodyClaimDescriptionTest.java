package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A read that claimed a body describes itself on it: a later access fails with that description,
 * e.g. the route after a filter consumed the body. And a reader of a split that fails lets the
 * body be discarded once the other side is closed, and the completion of a split can read the
 * body it was split from.
 */
class ByteBodyClaimDescriptionTest {
    private static final int LIMIT = 100;
    private final ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private ReadBuffer bytes(int n) {
        byte[] array = new byte[n];
        Arrays.fill(array, (byte) 'a');
        return factory.readBufferFactory().adapt(array);
    }

    @Test
    void aLaterAccessFailsWithTheDescriptionOfTheRead() {
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, LIMIT), bytesConsumed -> {
        });
        ByteBody root = body.rootBody();
        assertNull(InternalByteBody.claimDescription(root));
        CloseableByteBody moved = root.move();
        InternalByteBody.describeClaim(root, "read with text() by a filter");
        // the first description is kept
        InternalByteBody.describeClaim(root, "read with bytes()");

        assertEquals("read with text() by a filter", InternalByteBody.claimDescription(root));
        assertEquals("read with text() by a filter", assertThrows(IllegalStateException.class, () -> root.split(ByteBody.SplitBackpressureMode.FASTEST)).getMessage());
        assertEquals("read with text() by a filter", assertThrows(IllegalStateException.class, root::move).getMessage());
        moved.close();
    }

    @Test
    void aBodyWithoutADescriptionFailsWithTheGenericMessage() {
        CloseableAvailableByteBody body = factory.adapt("abc".getBytes());
        body.move().close();
        IllegalStateException e = assertThrows(IllegalStateException.class, body::move);
        assertTrue(e.getMessage().startsWith("Request body has already been claimed"), e.getMessage());
    }

    @Test
    void aSplitReaderThatFailsLetsTheBodyBeDiscarded() {
        AtomicBoolean discarded = new AtomicBoolean();
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, LIMIT), new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            public void allowDiscard() {
                discarded.set(true);
            }
        });
        CloseableByteBody root = body.rootBody();
        CloseableByteBody copy = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        Flux<ReadBuffer> read = Flux.from(copy.toReadBufferPublisher());
        body.sharedBuffer().add(bytes(2 * LIMIT));
        // the reader of the copy fails with the buffer limit
        assertThrows(RuntimeException.class, () -> read.doOnNext(ReadBuffer::close).blockLast(Duration.ofSeconds(10)));
        assertFalse(discarded.get());
        // the other side ends: nothing reads the body anymore
        root.close();
        assertTrue(discarded.get());
    }

    @Test
    void theCompletionOfASplitCanReadTheBodyItWasSplitFrom() {
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, LIMIT), bytesConsumed -> {
        });
        CloseableByteBody root = body.rootBody();
        CloseableByteBody copy = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        AtomicInteger rootLength = new AtomicInteger(-1);
        // e.g. a filter reads a copy, and the route reads the body once the copy completed
        Flux.from(copy.toReadBufferPublisher())
            .doOnNext(ReadBuffer::close)
            .doOnComplete(() -> Flux.from(root.toReadBufferPublisher())
                .map(rb -> {
                    try (rb) {
                        return rb.readable();
                    }
                })
                .reduce(0, Integer::sum)
                .subscribe(rootLength::set))
            .subscribe();
        body.sharedBuffer().add(bytes(10));
        body.sharedBuffer().complete();
        assertEquals(10, rootLength.get());
    }
}
