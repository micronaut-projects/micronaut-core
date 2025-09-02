package io.micronaut.buffer.netty;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class AbstractReadBufferTest {
    final ReadBufferFactory factory;

    AbstractReadBufferTest(ReadBufferFactory factory) {
        this.factory = factory;
    }

    @Test
    public void readable() {
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            assertEquals(3, rb.readable());
            assertEquals(3, rb.readable());
            rb.close();
            assertThrows(IllegalStateException.class, rb::readable);
        }
    }

    @Test
    public void duplicate() {
        try (ReadBuffer rb1 = factory.copyOf("foo", UTF_8);
             ReadBuffer rb2 = rb1.duplicate()) {
            assertEquals("foo", rb1.toString(UTF_8));
            assertEquals("foo", rb2.toString(UTF_8));
        }
        try (ReadBuffer rb1 = factory.copyOf("foo", UTF_8);
             ReadBuffer rb2 = rb1.duplicate()) {
            rb2.close();
            assertEquals("foo", rb1.toString(UTF_8));
        }
        try (ReadBuffer rb1 = factory.copyOf("foo", UTF_8);
             ReadBuffer rb2 = rb1.duplicate()) {
            rb1.close();
            assertEquals("foo", rb2.toString(UTF_8));
        }
    }

    @Test
    public void split() {
        try (ReadBuffer rb1 = factory.copyOf("foo", UTF_8);
             ReadBuffer rb2 = rb1.split(1)) {
            assertEquals("oo", rb1.toString(UTF_8));
            assertEquals("f", rb2.toString(UTF_8));
        }
        try (ReadBuffer rb1 = factory.copyOf("foo", UTF_8);
             ReadBuffer rb2 = rb1.split(1)) {
            rb1.close();
            assertEquals("f", rb2.toString(UTF_8));
        }
        try (ReadBuffer rb1 = factory.copyOf("foo", UTF_8);
             ReadBuffer rb2 = rb1.split(1)) {
            rb2.close();
            assertEquals("oo", rb1.toString(UTF_8));
        }
        try (ReadBuffer rb = factory.copyOf("foo", UTF_8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> rb.split(4));
            assertEquals("foo", rb.toString(UTF_8));
        }
    }

    @Test
    public void move() {
        try (ReadBuffer rb1 = factory.copyOf("foo", UTF_8);
             ReadBuffer rb2 = rb1.move()) {
            assertThrows(IllegalStateException.class, rb1::toArray);
            rb1.close();
            assertEquals("foo", rb2.toString(UTF_8));
        }
    }

    @Test
    public void toArrayDest() {
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            byte[] arr = new byte[3];
            rb.toArray(arr, 0);
            assertArrayEquals(new byte[]{1, 2, 3}, arr);
        }
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            byte[] arr = new byte[4];
            rb.toArray(arr, 1);
            assertArrayEquals(new byte[]{0, 1, 2, 3}, arr);
        }
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            assertThrows(IndexOutOfBoundsException.class, () -> rb.toArray(new byte[2], 0));
            assertThrows(IllegalStateException.class, rb::toArray);
        }
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            assertThrows(IndexOutOfBoundsException.class, () -> rb.toArray(new byte[3], 1));
            assertThrows(IllegalStateException.class, rb::toArray);
        }
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            assertThrows(IndexOutOfBoundsException.class, () -> rb.toArray(new byte[4], -1));
            assertThrows(IllegalStateException.class, rb::toArray);
        }
        try (ReadBuffer rb = factory.createEmpty()) {
            assertThrows(IndexOutOfBoundsException.class, () -> rb.toArray(new byte[2], 3));
            assertThrows(IllegalStateException.class, rb::toArray);
        }
    }

    @Test
    public void toArray() {
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            assertArrayEquals(new byte[]{1, 2, 3}, rb.toArray());
        }
    }

    @Test
    public void toInputStream() throws IOException {
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3});
             InputStream is = rb.toInputStream()) {
            rb.close();
            assertArrayEquals(new byte[]{1, 2, 3}, is.readAllBytes());
        }
    }

    @Test
    public void transferTo() throws IOException {
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            rb.transferTo(baos);
            assertArrayEquals(new byte[]{1, 2, 3}, baos.toByteArray());
        }
    }

    @Test
    public void createEmpty() {
        try (ReadBuffer rb = factory.createEmpty()) {
            assertEquals(0, rb.readable());
            assertArrayEquals(new byte[0], rb.toArray());
        }
    }

    @Test
    public void copyOfString() {
        try (ReadBuffer rb = factory.copyOf("foo", UTF_8)) {
            assertEquals("foo", rb.toString(UTF_8));
        }
    }

    @Test
    public void copyOfStream() throws IOException {
        try (ReadBuffer rb = factory.copyOf(new ByteArrayInputStream(new byte[]{1, 2, 3}))) {
            assertArrayEquals(new byte[]{1, 2, 3}, rb.toArray());
        }
    }

    @Test
    public void copyOfBuffer() {
        try (ReadBuffer rb = factory.copyOf(ByteBuffer.wrap(new byte[]{1, 2, 3}))) {
            assertArrayEquals(new byte[]{1, 2, 3}, rb.toArray());
        }
    }

    @Test
    public void adaptBuffer() {
        try (ReadBuffer rb = factory.adapt(ByteBuffer.wrap(new byte[]{1, 2, 3}))) {
            assertArrayEquals(new byte[]{1, 2, 3}, rb.toArray());
        }
    }

    @Test
    public void adaptIoBuffer() {
        try (ReadBuffer rb = factory.adapt(ByteArrayBufferFactory.INSTANCE.wrap(new byte[]{1, 2, 3}))) {
            assertArrayEquals(new byte[]{1, 2, 3}, rb.toArray());
        }
    }

    @Test
    public void adaptArray() {
        try (ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3})) {
            assertArrayEquals(new byte[]{1, 2, 3}, rb.toArray());
        }
    }

    @Test
    public void buffer() throws IOException {
        try (ReadBuffer rb = factory.buffer(os -> os.write(new byte[]{1, 2, 3}))) {
            assertArrayEquals(new byte[]{1, 2, 3}, rb.toArray());
        }
    }

    @Test
    public void outputStreamBuffer() throws IOException {
        ReadBuffer body;
        try (ReadBufferFactory.BufferingOutputStream bos = factory.outputStreamBuffer()) {
            bos.stream().write("foo".getBytes());
            body = bos.finishBuffer();
        }
        assertEquals("foo", body.toString(UTF_8));
        body.close();
    }

    @Test
    public void toStringConsumed() {
        ReadBuffer rb = factory.adapt(new byte[]{1, 2, 3});
        rb.close();
        assertEquals(rb.getClass().getSimpleName() + "[consumed]", rb.toString());
    }

    @Test
    public void toStringNormal() {
        try (ReadBuffer rb = factory.adapt(new byte[]{
            1, 2, 3,
            0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x57, 0x6f, 0x72, 0x6c, 0x64, // Hello World
            0x7e, 0x7f, (byte) 0x80
        })) {
            assertEquals(rb.getClass().getSimpleName() + "[len=17, data='\\x01\\x02\\x03Hello World~\\x7f\\x80']", rb.toString());
        }
    }

    @Test
    public void toStringLong() {
        byte[] array = new byte[45];
        Arrays.fill(array, (byte) '.');
        try (ReadBuffer rb = factory.adapt(array)) {
            assertEquals(rb.getClass().getSimpleName() + "[len=45, data='................................'…]", rb.toString());
        }
    }

    @Test
    public void compose() {
        ReadBuffer a = factory.adapt(new byte[]{1, 2, 3});
        ReadBuffer b = factory.adapt(new byte[]{4, 5, 6});
        try (ReadBuffer composed = factory.compose(List.of(a, b))) {
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, composed.toArray());
        }
    }
}
