package io.micronaut.buffer.netty;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * {@link io.netty.buffer.CompositeByteBuf#addComponent} calls {@code consolidateIfNeeded()}
     * after every single component, and each consolidation copies everything accumulated so far
     * into a freshly allocated buffer. Adding all components at once must consolidate only once,
     * i.e. there must be exactly one large allocation instead of {@code ceil(n / 16)}.
     */
    @Test
    void composeConsolidatesOnlyOnce() {
        int count = 50;
        int chunk = 100;
        CountingAllocator allocator = new CountingAllocator(chunk + 1);
        NettyReadBufferFactory factory = NettyReadBufferFactory.of(allocator);
        List<ReadBuffer> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parts.add(factory.adapt(Unpooled.wrappedBuffer(new byte[chunk])));
        }
        ReadBuffer composed = factory.compose(parts);
        try {
            assertEquals(1, allocator.largeAllocations);
            assertEquals(count * chunk, composed.readable());
        } finally {
            composed.close();
        }
    }

    @Test
    void composeReleasesAllInputsOnFailure() {
        NettyReadBufferFactory factory = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);
        ByteBuf first = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
        ByteBuf broken = Unpooled.wrappedBuffer(new byte[]{4, 5, 6});
        ByteBuf last = Unpooled.wrappedBuffer(new byte[]{7, 8, 9});
        ReadBuffer brokenBuffer = factory.adapt(broken);
        // consume the middle buffer, so that toByteBuf throws for it
        brokenBuffer.close();
        List<ReadBuffer> parts = List.of(factory.adapt(first), brokenBuffer, factory.adapt(last));
        try {
            assertThrows(IllegalStateException.class, () -> factory.compose(parts));
            assertEquals(0, first.refCnt());
            assertEquals(0, broken.refCnt());
            assertEquals(0, last.refCnt());
        } finally {
            for (ByteBuf byteBuf : List.of(first, broken, last)) {
                if (byteBuf.refCnt() > 0) {
                    byteBuf.release(byteBuf.refCnt());
                }
            }
        }
    }

    /**
     * Allocator that counts the allocations that are larger than a single composed input, i.e. the
     * allocations done by {@code CompositeByteBuf.consolidate0}.
     */
    private static final class CountingAllocator extends AbstractByteBufAllocator {
        final int threshold;
        int largeAllocations;

        CountingAllocator(int threshold) {
            super(false);
            this.threshold = threshold;
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            count(initialCapacity);
            return UnpooledByteBufAllocator.DEFAULT.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            count(initialCapacity);
            return UnpooledByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }

        private void count(int initialCapacity) {
            if (initialCapacity >= threshold) {
                largeAllocations++;
            }
        }
    }
}
