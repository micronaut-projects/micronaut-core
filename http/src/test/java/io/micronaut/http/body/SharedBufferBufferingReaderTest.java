package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The readers of the splits of a streaming body that buffer the whole body, e.g. the decoded body
 * of a copy of the request body, are given all of it, whatever the other readers of the body do
 * while it arrives.
 */
class SharedBufferBufferingReaderTest {
    private final ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private ReadBuffer bytes(String text) {
        return factory.readBufferFactory().copyOf(text, StandardCharsets.UTF_8);
    }

    @Test
    void aBufferingReaderThatClosesTheBodyWhenItCompletesLeavesTheBytesToTheOtherBufferingReaders() {
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 1024), bytesConsumed -> {
        });
        CloseableByteBody root = body.rootBody();
        CloseableByteBody first = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        CloseableByteBody second = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        StringBuilder read = new StringBuilder();
        InternalByteBody.bufferFlow(first).onComplete((buffered, error) -> {
            try (buffered) {
                read.append("first: ").append(error == null ? buffered.toString(StandardCharsets.UTF_8) : error);
            }
            // e.g. the read of a body that consumes it once it is decoded, while a copy is read too:
            // the last reservation is closed before the other reader is given the bytes
            root.close();
        });
        InternalByteBody.bufferFlow(second).onComplete((buffered, error) -> {
            try (buffered) {
                read.append(", second: ").append(error == null ? buffered.toString(StandardCharsets.UTF_8) : error);
            }
        });

        body.sharedBuffer().add(bytes("one"));
        body.sharedBuffer().add(bytes(" two"));
        body.sharedBuffer().complete();

        assertEquals("first: one two, second: one two", read.toString());
    }

    @Test
    void aStreamingReaderThatTakesTheLastReservationLeavesTheBytesToTheBufferingReaders() {
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 1024), bytesConsumed -> {
        });
        CloseableByteBody root = body.rootBody();
        CloseableByteBody copy = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        StringBuilder read = new StringBuilder();
        StringBuilder streamed = new StringBuilder();
        body.sharedBuffer().add(bytes("one"));
        InternalByteBody.bufferFlow(copy).onComplete((buffered, error) -> {
            try (buffered) {
                read.append(error == null ? buffered.toString(StandardCharsets.UTF_8) : error);
            }
        });
        // e.g. the text or the form of a body, read after a copy started to decode the body: no
        // reservation is left, and the reader that buffers the body is still to be given the bytes
        Flux.from(root.toReadBufferPublisher()).subscribe(rb -> {
            try (rb) {
                streamed.append(rb.toString(StandardCharsets.UTF_8));
            }
        });

        body.sharedBuffer().add(bytes(" two"));
        body.sharedBuffer().complete();

        assertEquals("one two", streamed.toString());
        assertEquals("one two", read.toString());
    }

    @Test
    void aBodyClosedWhileAReaderBuffersItLeavesTheBytesToTheReader() {
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 1024), bytesConsumed -> {
        });
        CloseableByteBody root = body.rootBody();
        CloseableByteBody copy = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        StringBuilder read = new StringBuilder();
        body.sharedBuffer().add(bytes("one"));
        InternalByteBody.bufferFlow(copy).onComplete((buffered, error) -> {
            try (buffered) {
                read.append(error == null ? buffered.toString(StandardCharsets.UTF_8) : error);
            }
        });
        root.close();

        body.sharedBuffer().add(bytes(" two"));
        body.sharedBuffer().complete();

        assertEquals("one two", read.toString());
    }
}
