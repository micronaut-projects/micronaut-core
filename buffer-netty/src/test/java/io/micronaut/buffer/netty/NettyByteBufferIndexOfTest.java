package io.micronaut.buffer.netty;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NettyByteBufferIndexOfTest {

    private static final byte[] DATA = "ab:cd:ef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void indexOfIsAbsoluteLikeByteArrayByteBuffer() {
        ByteBuffer<?> array = ByteArrayBufferFactory.INSTANCE.wrap(DATA.clone());
        ByteBuffer<?> netty = new NettyByteBuffer(Unpooled.wrappedBuffer(DATA.clone()));
        for (ByteBuffer<?> buffer : new ByteBuffer<?>[] {array, netty}) {
            String name = buffer.getClass().getSimpleName();
            assertEquals(2, buffer.indexOf((byte) ':'), name);
            buffer.readerIndex(3);
            assertEquals(5, buffer.indexOf((byte) ':'), name);
            assertEquals((byte) ':', buffer.getByte(buffer.indexOf((byte) ':')), name);
            assertEquals(-1, buffer.indexOf((byte) 'a'), name);
            assertEquals(-1, buffer.indexOf((byte) 'z'), name);
        }
    }
}
