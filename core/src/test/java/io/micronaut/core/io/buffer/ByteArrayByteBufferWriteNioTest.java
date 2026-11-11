package io.micronaut.core.io.buffer;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteArrayByteBufferWriteNioTest {

    @Test
    void writesOnlyTheRemainingBytesOfAHeapBuffer() {
        ByteBuffer source = ByteBuffer.wrap("abcdef".getBytes(StandardCharsets.UTF_8));
        source.position(1).limit(5);
        ByteArrayByteBuffer buffer = new ByteArrayByteBuffer(new byte[4]);

        buffer.write(source);

        assertArrayEquals("bcde".getBytes(StandardCharsets.UTF_8), buffer.toByteArray());
        assertEquals(4, buffer.writerIndex());
    }

    @Test
    void writesASliceWithAnArrayOffset() {
        ByteBuffer source = ByteBuffer.wrap("abcdef".getBytes(StandardCharsets.UTF_8), 2, 3).slice();
        ByteArrayByteBuffer buffer = new ByteArrayByteBuffer(new byte[3]);

        buffer.write(source);

        assertArrayEquals("cde".getBytes(StandardCharsets.UTF_8), buffer.toByteArray());
    }

    @Test
    void writesADirectBuffer() {
        ByteBuffer source = ByteBuffer.allocateDirect(3);
        source.put("abc".getBytes(StandardCharsets.UTF_8)).flip();
        ByteArrayByteBuffer buffer = new ByteArrayByteBuffer(new byte[3]);

        buffer.write(source);

        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), buffer.toByteArray());
    }

    @Test
    void writesAReadOnlyBuffer() {
        ByteBuffer source = ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        ByteArrayByteBuffer buffer = new ByteArrayByteBuffer(new byte[3]);

        buffer.write(source);

        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), buffer.toByteArray());
    }
}
