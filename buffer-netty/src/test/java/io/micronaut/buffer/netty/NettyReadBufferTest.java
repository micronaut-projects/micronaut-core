package io.micronaut.buffer.netty;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
     * A composite created with the allocator default of 16 components consolidates (copies all the
     * pieces into one freshly allocated buffer) as soon as there are more than 16 of them, so a
     * body of a few hundred pieces was copied once more before it could be read. Up to
     * {@link NettyReadBufferFactory#MAX_COMPOSITE_COMPONENTS} pieces the composite must keep the
     * pieces in place: no large allocation.
     */
    @Test
    void composeKeepsPiecesInPlace() {
        int count = 1280; // a 10 MiB body in 8 KiB pieces
        int chunk = 100;
        CountingAllocator allocator = new CountingAllocator(chunk + 1);
        NettyReadBufferFactory factory = NettyReadBufferFactory.of(allocator);
        List<ReadBuffer> parts = new ArrayList<>(count);
        List<ByteBuf> pieces = new ArrayList<>(count);
        byte[] expected = new byte[count * chunk];
        for (int i = 0; i < count; i++) {
            byte[] piece = new byte[chunk];
            Arrays.fill(piece, (byte) i);
            System.arraycopy(piece, 0, expected, i * chunk, chunk);
            ByteBuf byteBuf = Unpooled.wrappedBuffer(piece);
            pieces.add(byteBuf);
            parts.add(factory.adapt(byteBuf));
        }
        ReadBuffer composed = factory.compose(parts);
        try {
            assertEquals(0, allocator.largeAllocations);
            assertEquals(count * chunk, composed.readable());
            assertArrayEquals(expected, composed.toArray());
        } finally {
            composed.close();
        }
        for (ByteBuf piece : pieces) {
            assertEquals(0, piece.refCnt());
        }
    }

    /**
     * {@link io.netty.buffer.CompositeByteBuf#addComponent} calls {@code consolidateIfNeeded()}
     * after every single component, and each consolidation copies everything accumulated so far
     * into a freshly allocated buffer. Beyond {@link NettyReadBufferFactory#MAX_COMPOSITE_COMPONENTS}
     * pieces, adding all components at once must consolidate only once, i.e. there must be exactly
     * one large allocation instead of {@code ceil(n / max)}, and the pieces must be released.
     */
    @Test
    void composeConsolidatesOnlyOnce() {
        int count = NettyReadBufferFactory.MAX_COMPOSITE_COMPONENTS + 1;
        int chunk = 10;
        CountingAllocator allocator = new CountingAllocator(chunk + 1);
        NettyReadBufferFactory factory = NettyReadBufferFactory.of(allocator);
        List<ReadBuffer> parts = new ArrayList<>(count);
        List<ByteBuf> pieces = new ArrayList<>(count);
        byte[] expected = new byte[count * chunk];
        for (int i = 0; i < count; i++) {
            byte[] piece = new byte[chunk];
            Arrays.fill(piece, (byte) i);
            System.arraycopy(piece, 0, expected, i * chunk, chunk);
            ByteBuf byteBuf = Unpooled.wrappedBuffer(piece);
            pieces.add(byteBuf);
            parts.add(factory.adapt(byteBuf));
        }
        ReadBuffer composed = factory.compose(parts);
        try {
            assertEquals(1, allocator.largeAllocations);
            assertEquals(count * chunk, composed.readable());
            for (ByteBuf piece : pieces) {
                assertEquals(0, piece.refCnt());
            }
            assertArrayEquals(expected, composed.toArray());
        } finally {
            composed.close();
        }
    }

    /**
     * The size hint is passed to the allocator as the initial capacity, so a writer that produces
     * up to that many bytes never grows the buffer.
     */
    @Test
    void bufferWithExpectedSizeRequestsThatCapacity() throws IOException {
        CountingAllocator allocator = new CountingAllocator(1);
        NettyReadBufferFactory factory = NettyReadBufferFactory.of(allocator);
        byte[] data = new byte[5000];
        try (ReadBuffer rb = factory.buffer(8192, os -> os.write(data))) {
            assertEquals(List.of(8192), allocator.requestedCapacities);
            assertEquals(1, allocator.largeAllocations);
            assertArrayEquals(data, rb.toArray());
        }
    }
    @Test
    void composeWithNonCollectionIterable() {
        NettyReadBufferFactory factory = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);
        List<ReadBuffer> parts = List.of(
            factory.adapt(new byte[]{1, 2, 3}),
            factory.adapt(new byte[]{4, 5, 6})
        );
        Iterable<ReadBuffer> iterable = () -> parts.iterator();
        try (ReadBuffer composed = factory.compose(iterable)) {
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, composed.toArray());
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
        final List<Integer> requestedCapacities = new ArrayList<>();

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
            requestedCapacities.add(initialCapacity);
            if (initialCapacity >= threshold) {
                largeAllocations++;
            }
        }
    }
}
