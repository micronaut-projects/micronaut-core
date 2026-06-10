package io.micronaut.buffer.netty;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyReadBufferTest extends AbstractReadBufferTest {
    public NettyReadBufferTest() {
        super(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT));
    }

    @Test
    void useFastHeapBufferReleasesHeapByteBuf() {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
        ReadBuffer readBuffer = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).adapt(byteBuf);
        try {
            assertEquals(3, readBuffer.useFastHeapBuffer(java.nio.ByteBuffer::remaining));
            assertEquals(0, byteBuf.refCnt());
            readBuffer.close();
        } finally {
            if (byteBuf.refCnt() > 0) {
                byteBuf.release(byteBuf.refCnt());
            }
        }
    }
}
