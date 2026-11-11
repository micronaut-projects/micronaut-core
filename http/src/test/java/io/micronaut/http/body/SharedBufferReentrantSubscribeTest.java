package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reader of a split of a streaming body can subscribe another split while it receives the
 * bytes, e.g. a filter that decodes a copy of the form, and whose completion runs the route that
 * reads the body: the other split receives those bytes too, once.
 */
class SharedBufferReentrantSubscribeTest {
    private final ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private ReadBuffer bytes(String text) {
        return factory.readBufferFactory().copyOf(text, StandardCharsets.UTF_8);
    }

    @Test
    void aSplitSubscribedWhileTheBytesAreDeliveredReceivesThem() {
        for (ByteBody.SplitBackpressureMode mode : ByteBody.SplitBackpressureMode.values()) {
            ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 1024), bytesConsumed -> {
            });
            CloseableByteBody root = body.rootBody();
            CloseableByteBody copy = root.split(mode);
            StringBuilder read = new StringBuilder();
            AtomicBoolean completed = new AtomicBoolean();
            AtomicBoolean subscribed = new AtomicBoolean();
            Flux.from(copy.toReadBufferPublisher()).subscribe(rb -> {
                rb.close();
                if (subscribed.compareAndSet(false, true)) {
                    // the reader of the copy is done with the first bytes: the other reader starts
                    Flux.from(root.toReadBufferPublisher()).subscribe(
                        other -> {
                            try (other) {
                                read.append(other.toString(StandardCharsets.UTF_8));
                            }
                        },
                        error -> read.append("error: ").append(error),
                        () -> completed.set(true));
                }
            });

            body.sharedBuffer().add(bytes("first"));
            body.sharedBuffer().add(bytes(" second"));
            body.sharedBuffer().complete();

            assertEquals("first second", read.toString(), mode.name());
            assertTrue(completed.get(), mode.name());
        }
    }

    @Test
    void aSplitSubscribedWhileTheLastBytesAreDeliveredReceivesThem() {
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 1024), bytesConsumed -> {
        });
        CloseableByteBody root = body.rootBody();
        CloseableByteBody copy = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        StringBuilder read = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean();
        Flux.from(copy.toReadBufferPublisher()).subscribe(ReadBuffer::close, error -> { }, () ->
            // the copy completed: the other reader starts
            Flux.from(root.toReadBufferPublisher()).subscribe(
                other -> {
                    try (other) {
                        read.append(other.toString(StandardCharsets.UTF_8));
                    }
                },
                error -> read.append("error: ").append(error),
                () -> completed.set(true)));

        body.sharedBuffer().addAndComplete(bytes("all"));

        assertEquals("all", read.toString());
        assertTrue(completed.get());
    }
}
