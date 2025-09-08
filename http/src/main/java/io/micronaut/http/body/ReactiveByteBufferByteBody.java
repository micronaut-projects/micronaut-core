/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.body.stream.UpstreamBalancer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Streaming {@link io.micronaut.http.body.ByteBody} implementation based on NIO {@link ByteBuffer}s.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public final class ReactiveByteBufferByteBody extends BaseStreamingByteBody<ReactiveByteBufferByteBody.SharedBuffer> implements CloseableByteBody {
    public ReactiveByteBufferByteBody(SharedBuffer sharedBuffer) {
        this(sharedBuffer, sharedBuffer.getRootUpstream());
    }

    private ReactiveByteBufferByteBody(SharedBuffer sharedBuffer, BufferConsumer.Upstream upstream) {
        super(sharedBuffer, upstream);
    }

    @Override
    public BufferConsumer.Upstream primary(BufferConsumer primary) {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        this.upstream = null;
        BaseSharedBuffer.logClaim();
        sharedBuffer.subscribe(primary, upstream);
        return upstream;
    }

    @Override
    protected ReactiveByteBufferByteBody derive(BufferConsumer.Upstream upstream) {
        return new ReactiveByteBufferByteBody(sharedBuffer, upstream);
    }

    @Override
    public @NonNull CloseableByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        UpstreamBalancer.UpstreamPair pair = UpstreamBalancer.balancer(upstream, backpressureMode);
        this.upstream = pair.left();
        this.sharedBuffer.reserve();
        return new ReactiveByteBufferByteBody(sharedBuffer, pair.right());
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        this.upstream = null;
        BaseSharedBuffer.logClaim();
        upstream.start();
        upstream.onBytesConsumed(Long.MAX_VALUE);
        return sharedBuffer.subscribeFull(upstream).map(AvailableByteArrayBody::create);
    }

    @Override
    public void close() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            return;
        }
        this.upstream = null;
        BaseSharedBuffer.logClaim();
        upstream.allowDiscard();
        upstream.disregardBackpressure();
        upstream.start();
        sharedBuffer.subscribe(null, upstream);
    }

    /**
     * Simple implementation of {@link BaseSharedBuffer} that consumes {@link ByteBuffer}s.<br>
     * Buffering is done using a {@link ByteArrayOutputStream}. Concurrency control is done through
     * a non-reentrant lock based on {@link AtomicReference}.
     */
    public static final class SharedBuffer extends BaseSharedBuffer implements BufferConsumer {
        // fields for concurrency control, see #submit
        private final ReentrantLock lock = new ReentrantLock();
        private final ConcurrentLinkedQueue<Runnable> workQueue = new ConcurrentLinkedQueue<>();

        SharedBuffer(ReadBufferFactory readBufferFactory, BodySizeLimits limits, Upstream rootUpstream) {
            super(readBufferFactory, limits, rootUpstream);
        }

        /**
         * Run a task non-concurrently with other submitted tasks. This method fulfills multiple
         * constraints:<br>
         * <ul>
         *     <li>It does not block (like a simple lock would) when another thread is already
         *     working. Instead, the submitted task will be run at a later time on the other
         *     thread.</li>
         *     <li>Tasks submitted on one thread will not be reordered (local order). This is
         *     similar to {@code EventLoopFlow} semantics.</li>
         *     <li>Reentrant calls (calls to {@code submit} from inside a submitted task) will
         *     run the task immediately (required by servlet).</li>
         *     <li>There is no executor to run tasks. This ensures good locality when submissions
         *     have low contention (i.e. tasks are usually run immediately on the submitting
         *     thread).</li>
         * </ul>
         *
         * @param task The task to run
         */
        private void submit(Runnable task) {
            workQueue.add(task);

            while (!workQueue.isEmpty()) {
                if (!lock.tryLock()) {
                    break;
                }
                try {
                    Runnable todo = workQueue.poll();
                    if (todo != null) {
                        todo.run();
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        void reserve() {
            submit(this::reserve0);
        }

        void subscribe(@Nullable BufferConsumer consumer, Upstream upstream) {
            submit(() -> subscribe0(consumer, upstream));
        }

        public DelayedExecutionFlow<ReadBuffer> subscribeFull(Upstream specificUpstream) {
            DelayedExecutionFlow<ReadBuffer> flow = DelayedExecutionFlow.create();
            submit(() -> subscribeFull0(flow, specificUpstream, false));
            return flow;
        }

        @Override
        public void add(ReadBuffer rb) {
            submit(() -> super.add(rb));
        }

        @Override
        public void error(Throwable e) {
            submit(() -> super.error(e));
        }

        @Override
        public void complete() {
            submit(super::complete);
        }
    }
}
