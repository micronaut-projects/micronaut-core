package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bytes that arrive before the first reader of a streaming body subscribes are kept past the
 * buffer limit, with {@code setKeepInitialBytes}, only for a reader that streams them without
 * holding them: any other reader fails with the limit, as it does without.
 */
class SharedBufferKeepInitialBytesTest {
    private static final int LIMIT = 100;
    private final ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private ReadBuffer bytes(int n) {
        byte[] array = new byte[n];
        Arrays.fill(array, (byte) 'a');
        return factory.readBufferFactory().adapt(array);
    }

    private ByteBodyFactory.StreamingBody body(boolean keep) {
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, LIMIT), bytesConsumed -> {
        });
        if (keep) {
            body.sharedBuffer().setKeepInitialBytes();
        }
        return body;
    }

    private static int length(Publisher<ReadBuffer> publisher) {
        Integer length = Flux.from(publisher)
            .map(rb -> {
                try (rb) {
                    return rb.readable();
                }
            })
            .reduce(0, Integer::sum)
            .block(Duration.ofSeconds(10));
        return length == null ? -1 : length;
    }

    @Test
    void theKeptBytesReachAReaderThatStreamsThem() {
        ByteBodyFactory.StreamingBody body = body(true);
        body.sharedBuffer().add(bytes(80));
        body.sharedBuffer().add(bytes(80));
        Publisher<ReadBuffer> publisher = InternalByteBody.toUnbufferedReadBufferPublisher(body.rootBody());
        // a buffer larger than the limit after the reader subscribed
        body.sharedBuffer().add(bytes(3 * LIMIT));
        body.sharedBuffer().complete();
        assertEquals(160 + 3 * LIMIT, length(publisher));
    }

    @Test
    void theKeptBytesReachAConsumerThatStreamsThem() {
        ByteBodyFactory.StreamingBody body = body(true);
        body.sharedBuffer().add(bytes(80));
        body.sharedBuffer().add(bytes(80));
        CountingConsumer consumer = new CountingConsumer(true);
        body.rootBody().primary(consumer);
        body.sharedBuffer().add(bytes(3 * LIMIT));
        body.sharedBuffer().complete();
        assertEquals(160 + 3 * LIMIT, consumer.length);
        assertTrue(consumer.completed);
        assertNull(consumer.error);
    }

    @Test
    void theKeptBytesFailAConsumerThatIsHeldToTheLimit() {
        ByteBodyFactory.StreamingBody body = body(true);
        body.sharedBuffer().add(bytes(2 * LIMIT));
        CountingConsumer consumer = new CountingConsumer(false);
        body.rootBody().primary(consumer);
        assertEquals(0, consumer.length);
        assertInstanceOf(BufferLengthExceededException.class, consumer.error);
    }

    @Test
    void theKeptBytesFailAReaderThatBuffersThem() {
        ByteBodyFactory.StreamingBody body = body(true);
        body.sharedBuffer().add(bytes(2 * LIMIT));
        body.sharedBuffer().complete();
        CompletionException error = assertThrows(CompletionException.class, () -> body.rootBody().buffer().join());
        assertInstanceOf(BufferLengthExceededException.class, error.getCause());
    }

    @Test
    void theKeptBytesFailAReaderThatIsHeldToTheLimit() {
        ByteBodyFactory.StreamingBody body = body(true);
        body.sharedBuffer().add(bytes(2 * LIMIT));
        body.sharedBuffer().complete();
        assertThrows(BufferLengthExceededException.class, () -> length(body.rootBody().toReadBufferPublisher()));
    }

    @Test
    void withoutKeepingThemAReaderThatStreamsTheBytesFails() {
        ByteBodyFactory.StreamingBody body = body(false);
        body.sharedBuffer().add(bytes(2 * LIMIT));
        body.sharedBuffer().complete();
        assertThrows(BufferLengthExceededException.class, () -> length(InternalByteBody.toUnbufferedReadBufferPublisher(body.rootBody())));
    }

    @Test
    void theOtherReadersOfASplitBodyAreHeldToTheLimit() {
        ByteBodyFactory.StreamingBody body = body(true);
        CloseableByteBody other = body.rootBody().split();
        body.sharedBuffer().add(bytes(2 * LIMIT));
        Publisher<ReadBuffer> publisher = InternalByteBody.toUnbufferedReadBufferPublisher(body.rootBody());
        body.sharedBuffer().add(bytes(10));
        body.sharedBuffer().complete();
        assertEquals(2 * LIMIT + 10, length(publisher));
        CompletionException error = assertThrows(CompletionException.class, () -> other.buffer().join());
        assertInstanceOf(BufferLengthExceededException.class, error.getCause());
    }

    private static final class CountingConsumer implements BufferConsumer {
        private final boolean unbuffered;
        private int length;
        private boolean completed;
        private Throwable error;

        CountingConsumer(boolean unbuffered) {
            this.unbuffered = unbuffered;
        }

        @Override
        public boolean isUnbuffered() {
            return unbuffered;
        }

        @Override
        public void add(ReadBuffer rb) {
            try (rb) {
                length += rb.readable();
            }
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void error(Throwable e) {
            error = e;
        }
    }

    @Test
    void aBodyThatIsAllThereButOverTheLimitIsStreamed() {
        BodySizeLimits limits = new BodySizeLimits(Long.MAX_VALUE, LIMIT);
        try (CloseableByteBody streamed = factory.createChecked(limits, bytes(2 * LIMIT))) {
            assertEquals(2 * LIMIT, length(InternalByteBody.toUnbufferedReadBufferPublisher(streamed)));
        }
        try (CloseableByteBody buffered = factory.createChecked(limits, bytes(2 * LIMIT))) {
            CompletionException error = assertThrows(CompletionException.class, () -> buffered.buffer().join());
            assertInstanceOf(BufferLengthExceededException.class, error.getCause());
        }
    }
}
