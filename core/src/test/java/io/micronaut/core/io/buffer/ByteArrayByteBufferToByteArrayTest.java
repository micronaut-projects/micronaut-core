package io.micronaut.core.io.buffer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ByteArrayByteBufferToByteArrayTest {

    @Test
    void toByteArrayAfterPartialRead() {
        ByteArrayByteBuffer buffer = ByteArrayBufferFactory.INSTANCE.wrap("abcdef".getBytes(StandardCharsets.UTF_8));
        buffer.read();
        buffer.read();

        assertArrayEquals("cdef".getBytes(StandardCharsets.UTF_8), buffer.toByteArray());
    }

    @Test
    void toByteArrayAfterReadingMoreThanHalf() {
        ByteArrayByteBuffer buffer = ByteArrayBufferFactory.INSTANCE.wrap("abcdef".getBytes(StandardCharsets.UTF_8));
        buffer.readerIndex(4);

        assertArrayEquals("ef".getBytes(StandardCharsets.UTF_8), buffer.toByteArray());
    }

    @Test
    void toByteArrayAfterReadingEverything() {
        ByteArrayByteBuffer buffer = ByteArrayBufferFactory.INSTANCE.wrap("abc".getBytes(StandardCharsets.UTF_8));
        buffer.readerIndex(3);

        assertArrayEquals(new byte[0], buffer.toByteArray());
    }
}
