/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jackson.core.util;

import io.micronaut.core.annotation.Internal;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.core.util.RecyclerPool;

import java.io.Serial;

/**
 * A Jackson {@link BufferRecycler} pool that keeps one recycler per Netty event loop thread.
 *
 * <p>Jackson's default pool is a concurrent deque: every parser and generator polls a recycler from it and
 * offers it back, with a node allocation and atomic operations each time. On a Netty
 * {@link FastThreadLocalThread}, such as an event loop thread, this pool hands out the recycler held in a
 * {@link FastThreadLocal} instead, which needs no synchronization and is never released to a shared pool. A
 * {@link BufferRecycler} tolerates several parsers and generators of the same thread using it at the same time,
 * and buffers released from another thread, since it swaps its buffers atomically.</p>
 *
 * <p>Every other thread, including virtual threads, uses the fallback pool, so no thread keeps a recycler that
 * could outlive its work. Without Netty on the class path every thread uses the fallback pool.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class EventLoopBufferRecyclerPool implements RecyclerPool<BufferRecycler> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final @Nullable Object THREAD_LOCAL;

    static {
        Object threadLocal;
        try {
            threadLocal = new FastThreadLocal<ThreadBufferRecycler>() {
                @Override
                protected ThreadBufferRecycler initialValue() {
                    return new ThreadBufferRecycler();
                }
            };
        } catch (NoClassDefFoundError e) {
            threadLocal = null;
        }
        THREAD_LOCAL = threadLocal;
    }

    private final RecyclerPool<BufferRecycler> fallback;

    /**
     * Creates a pool falling back to a new {@link JsonRecyclerPools#newConcurrentDequePool() concurrent deque pool},
     * the Jackson default.
     */
    public EventLoopBufferRecyclerPool() {
        this(JsonRecyclerPools.newConcurrentDequePool());
    }

    /**
     * @param fallback The pool used on threads other than Netty event loop threads
     */
    public EventLoopBufferRecyclerPool(RecyclerPool<BufferRecycler> fallback) {
        this.fallback = fallback;
    }

    /**
     * @return Whether Netty is available, without it this pool always uses the fallback pool
     */
    public static boolean isSupported() {
        return THREAD_LOCAL != null;
    }

    @Override
    public BufferRecycler acquireAndLinkPooled() {
        BufferRecycler recycler = threadRecycler();
        if (recycler != null) {
            // not linked: releasing a parser or generator leaves it with the thread
            return recycler;
        }
        // linked to the fallback pool, which then takes it back directly
        return fallback.acquireAndLinkPooled();
    }

    @Override
    public BufferRecycler acquirePooled() {
        BufferRecycler recycler = threadRecycler();
        return recycler != null ? recycler : fallback.acquirePooled();
    }

    @Override
    public void releasePooled(BufferRecycler pooled) {
        if (!(pooled instanceof ThreadBufferRecycler)) {
            fallback.releasePooled(pooled);
        }
    }

    @Override
    public int pooledCount() {
        return fallback.pooledCount();
    }

    @Override
    public boolean clear() {
        return fallback.clear();
    }

    @SuppressWarnings("unchecked")
    private static @Nullable BufferRecycler threadRecycler() {
        if (THREAD_LOCAL != null && Thread.currentThread() instanceof FastThreadLocalThread) {
            return ((FastThreadLocal<ThreadBufferRecycler>) THREAD_LOCAL).get();
        }
        return null;
    }

    /**
     * The recycler of an event loop thread, never handed to the fallback pool.
     */
    private static final class ThreadBufferRecycler extends BufferRecycler {
    }
}
