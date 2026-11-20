package io.micronaut.http.server.netty.multipart;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An unpooled allocator that remembers every buffer it hands out, so a test can tell whether all
 * of them were released.
 */
public final class TrackingAllocator extends AbstractByteBufAllocator {
    final List<ByteBuf> allocated = new CopyOnWriteArrayList<>();

    public TrackingAllocator() {
        super(false);
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.heapBuffer(initialCapacity, maxCapacity);
        allocated.add(buf);
        return buf;
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity);
        allocated.add(buf);
        return buf;
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }

    /**
     * @return The bytes of capacity of the buffers that are still referenced
     */
    public long retainedBytes() {
        return allocated.stream().filter(b -> b.refCnt() > 0).mapToLong(ByteBuf::capacity).sum();
    }

    /**
     * Waits up to the given time for all buffers to be released.
     *
     * @param millis The time to wait
     * @return The bytes still retained after waiting
     * @throws InterruptedException if interrupted
     */
    public long awaitReleased(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        long retained;
        while ((retained = retainedBytes()) > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        return retained;
    }
}
