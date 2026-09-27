package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * {@link ByteBodyFactory#limit(CloseableByteBody, long)} fails a body once more bytes than the
 * limit arrive.
 */
@Timeout(10)
class ByteBodyLimitTest {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private static CloseableByteBody streamed(String... parts) {
        return FACTORY.adapt(Flux.fromArray(parts).map(p -> (ReadBuffer) FACTORY.readBufferFactory().copyOf(p, StandardCharsets.UTF_8)));
    }

    private static String read(CloseableByteBody body) throws Exception {
        try (CloseableAvailableByteBody available = body.buffer().get(5, TimeUnit.SECONDS)) {
            return available.toString(StandardCharsets.UTF_8);
        }
    }

    @Test
    void aStreamedBodyWithinTheLimitPasses() throws Exception {
        Assertions.assertEquals("abcabcabc", read(FACTORY.limit(streamed("abc", "abc", "abc"), 9)));
    }

    @Test
    void aStreamedBodyOverTheLimitFails() {
        CloseableByteBody limited = FACTORY.limit(streamed("abc", "abc", "abc"), 5);
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> read(limited));
        Assertions.assertInstanceOf(ContentLengthExceededException.class, e.getCause());
    }

    @Test
    void aBodyWhoseKnownLengthIsOverTheLimitFails() {
        CloseableByteBody limited = FACTORY.limit(FACTORY.adapt("abcdef".getBytes(StandardCharsets.UTF_8)), 3);
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> read(limited));
        Assertions.assertInstanceOf(ContentLengthExceededException.class, e.getCause());
    }

    @Test
    void aBodyWhoseKnownLengthIsWithinTheLimitKeepsItsLength() throws Exception {
        CloseableByteBody limited = FACTORY.limit(FACTORY.adapt("abcdef".getBytes(StandardCharsets.UTF_8)), 6);
        Assertions.assertEquals(OptionalLong.of(6), limited.expectedLength());
        Assertions.assertEquals("abcdef", read(limited));
    }

    @Test
    void aStreamedBodyWithAKnownLengthOverTheLimitFailsBeforeItsBytes() {
        CloseableByteBody limited = FACTORY.limit(FACTORY.adapt(Flux.just((ReadBuffer) FACTORY.readBufferFactory().copyOf("abcdef", StandardCharsets.UTF_8)), OptionalLong.of(6)), 3);
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> read(limited));
        Assertions.assertInstanceOf(ContentLengthExceededException.class, e.getCause());
    }
}
