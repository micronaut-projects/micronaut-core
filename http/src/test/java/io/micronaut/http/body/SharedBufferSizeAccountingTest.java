package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedBufferSizeAccountingTest {
    private static ReadBuffer bytes(ByteBodyFactory factory, int n, byte value) {
        byte[] array = new byte[n];
        Arrays.fill(array, value);
        return factory.readBufferFactory().adapt(array);
    }

    /**
     * Data that arrives before the streaming subscriber attaches is handed on to that subscriber,
     * which does its own accounting. It must not stay counted against the buffer limit, otherwise
     * the remaining budget for the rest of the body is permanently reduced.
     */
    @Test
    @Timeout(10)
    public void initialBufferIsNotCountedTwiceAgainstBufferLimit() {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        BufferConsumer.Upstream upstream = bytesConsumed -> {
        };
        ByteBodyFactory.StreamingBody streamingBody = factory.createStreamingBody(
            new BodySizeLimits(Long.MAX_VALUE, 100), upstream);

        // 60 bytes arrive before anyone subscribes, so they are buffered by the shared buffer
        streamingBody.sharedBuffer().add(bytes(factory, 60, (byte) 'a'));

        List<byte[]> received = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean complete = new AtomicBoolean();
        try (CloseableByteBody root = streamingBody.rootBody()) {
            Flux.from(root.toReadBufferPublisher()).subscribe(
                rb -> {
                    try (rb) {
                        received.add(rb.toArray());
                    }
                },
                error::set,
                () -> complete.set(true));

            // the first 60 bytes have been consumed by now, so there is room for 60 more
            streamingBody.sharedBuffer().add(bytes(factory, 60, (byte) 'b'));
            streamingBody.sharedBuffer().complete();
        }

        assertNull(error.get());
        assertTrue(complete.get());
        assertEquals(120, received.stream().mapToInt(a -> a.length).sum());
    }
}
