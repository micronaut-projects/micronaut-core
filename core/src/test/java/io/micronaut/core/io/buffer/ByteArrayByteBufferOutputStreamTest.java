package io.micronaut.core.io.buffer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteArrayByteBufferOutputStreamTest {

    @Test
    void writeThroughStreamThenRead() throws IOException {
        ByteArrayByteBuffer buffer = ByteArrayBufferFactory.INSTANCE.buffer();
        try (OutputStream out = buffer.toOutputStream()) {
            out.write('a');
            out.write("bcd".getBytes(StandardCharsets.UTF_8));
            out.write("xefx".getBytes(StandardCharsets.UTF_8), 1, 2);
        }

        assertEquals(6, buffer.writerIndex());
        assertEquals(6, buffer.readableBytes());
        assertEquals("abcdef", buffer.toString(StandardCharsets.UTF_8));
        assertArrayEquals("abcdef".getBytes(StandardCharsets.UTF_8), buffer.toByteArray());
    }

    @Test
    void writeWithinCapacityDoesNotGrow() throws IOException {
        ByteArrayByteBuffer buffer = ByteArrayBufferFactory.INSTANCE.buffer(4);
        byte[] array = buffer.asNativeBuffer();
        buffer.toOutputStream().write(new byte[] {1, 2, 3, 4});

        assertEquals(array, buffer.asNativeBuffer());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, buffer.toByteArray());
    }

    @Test
    void growsPastInitialCapacity() throws IOException {
        ByteArrayByteBuffer buffer = ByteArrayBufferFactory.INSTANCE.buffer(2);
        OutputStream out = buffer.toOutputStream();
        byte[] expected = new byte[1000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
            out.write(i);
        }

        assertEquals(1000, buffer.writerIndex());
        assertEquals(1000, buffer.readableBytes());
        assertArrayEquals(expected, buffer.toByteArray());
    }

    @Test
    void appendsAfterExistingWrites() throws IOException {
        ByteArrayByteBuffer buffer = ByteArrayBufferFactory.INSTANCE.buffer(3);
        buffer.write("abc".getBytes(StandardCharsets.UTF_8));
        buffer.toOutputStream().write("def".getBytes(StandardCharsets.UTF_8));

        assertEquals("abcdef", buffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void invalidRangeIsRejected() {
        OutputStream out = ByteArrayBufferFactory.INSTANCE.buffer().toOutputStream();
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[2], 1, 2));
    }
}
