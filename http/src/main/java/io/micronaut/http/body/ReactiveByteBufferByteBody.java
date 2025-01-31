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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.body.stream.PublisherAsBlocking;
import io.micronaut.http.body.stream.UpstreamBalancer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streaming {@link io.micronaut.http.body.ByteBody} implementation based on NIO {@link ByteBuffer}s.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public final class ReactiveByteBufferByteBody implements CloseableByteBody, InternalByteBody {
    private final SharedBuffer sharedBuffer;
    private BufferConsumer.Upstream upstream;

    public ReactiveByteBufferByteBody(SharedBuffer sharedBuffer) {
        this(sharedBuffer, sharedBuffer.getRootUpstream());
    }

    private ReactiveByteBufferByteBody(SharedBuffer sharedBuffer, BufferConsumer.Upstream upstream) {
        this.sharedBuffer = sharedBuffer;
        this.upstream = upstream;
    }

    BufferConsumer.Upstream primary(ByteBufferConsumer primary) {
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
    public @NonNull OptionalLong expectedLength() {
        return sharedBuffer.getExpectedLength();
    }

    private Flux<ByteBuffer> toNioBufferPublisher() {
        AsFlux asFlux = new AsFlux(sharedBuffer);
        BufferConsumer.Upstream upstream = primary(asFlux);
        return asFlux.asFlux(upstream);
    }

    @Override
    public @NonNull InputStream toInputStream() {
        PublisherAsBlocking<ByteBuffer> publisherAsBlocking = new PublisherAsBlocking<>();
        toNioBufferPublisher().subscribe(publisherAsBlocking);
        return new InputStream() {
            private ByteBuffer buffer;

            @Override
            public int read() throws IOException {
                byte[] arr = new byte[1];
                int n = read(arr);
                return n == -1 ? -1 : arr[0] & 0xff;
            }

            @Override
            public int read(byte @NonNull [] b, int off, int len) throws IOException {
                while (buffer == null) {
                    try {
                        ByteBuffer o = publisherAsBlocking.take();
                        if (o == null) {
                            Throwable failure = publisherAsBlocking.getFailure();
                            if (failure == null) {
                                return -1;
                            } else {
                                throw new IOException(failure);
                            }
                        }
                        if (!o.hasRemaining()) {
                            continue;
                        }
                        buffer = o;
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException();
                    }
                }

                int toRead = Math.min(len, buffer.remaining());
                buffer.get(b, off, toRead);
                if (buffer.remaining() == 0) {
                    buffer = null;
                }
                return toRead;
            }

            @Override
            public void close() {
                publisherAsBlocking.close();
            }
        };
    }

    @Override
    public @NonNull Flux<byte[]> toByteArrayPublisher() {
        return toNioBufferPublisher().map(ReactiveByteBufferByteBody::toByteArray);
    }

    private static byte @NonNull [] toByteArray(ByteBuffer bb) {
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        return bytes;
    }

    @Override
    public @NonNull Publisher<io.micronaut.core.io.buffer.ByteBuffer<?>> toByteBufferPublisher() {
        return toByteArrayPublisher().map(ByteArrayBufferFactory.INSTANCE::wrap);
    }

    @Override
    public @NonNull CloseableByteBody move() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        this.upstream = null;
        return new ReactiveByteBufferByteBody(sharedBuffer, upstream);
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
        return sharedBuffer.subscribeFull(upstream)
            .map(bb -> AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, ReactiveByteBufferByteBody.toByteArray(bb)));
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

    @Override
    public @NonNull CloseableByteBody allowDiscard() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        upstream.allowDiscard();
        return this;
    }

    interface ByteBufferConsumer extends BufferConsumer {
        void add(@NonNull ByteBuffer buffer);
    }

    private static final class AsFlux extends BaseSharedBuffer.AsFlux<ByteBuffer> implements ByteBufferConsumer {
        AsFlux(BaseSharedBuffer<?, ?> sharedBuffer) {
            super(sharedBuffer);
        }

        @Override
        protected int size(ByteBuffer buf) {
            return buf.remaining();
        }

        @Override
        public void add(ByteBuffer buffer) {
            add0(buffer);
        }
    }

    /**
     * Simple implementation of {@link BaseSharedBuffer} that consumes {@link ByteBuffer}s.<br>
     * Buffering is done using a {@link ByteArrayOutputStream}. Concurrency control is done through
     * a non-reentrant lock based on {@link AtomicReference}.
     */
    public static final class SharedBuffer extends BaseSharedBuffer<ByteBufferConsumer, ByteBuffer> implements ByteBufferConsumer {
        // fields for concurrency control, see #submit
        private final AtomicReference<WorkState> workState = new AtomicReference<>(WorkState.CLEAN);
        private final ConcurrentLinkedQueue<Runnable> workQueue = new ConcurrentLinkedQueue<>();

        private SnapshotByteArrayOutputStream buffer;
        private ByteBuffer adding;

        public SharedBuffer(BodySizeLimits limits, Upstream rootUpstream) {
            super(limits, rootUpstream);
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
         *     delay the second task until the first task is complete.</li>
         *     <li>There is no executor to run tasks. This ensures good locality when submissions
         *     have low contention (i.e. tasks are usually run immediately on the submitting
         *     thread).</li>
         * </ul>
         *
         * @param task The task to run
         */
        private void submit(Runnable task) {
            /*
             * This implementation is fairly simple: A work queue that contains all the tasks, and
             * an atomic field to control concurrency.
             */

            workQueue.add(task);
            if (workState.getAndUpdate(ws -> ws == WorkState.CLEAN ? WorkState.WORKING_THEN_CLEAN : WorkState.WORKING_THEN_DIRTY) != WorkState.CLEAN) {
                // another thread is working and will pick up our task
                return;
            }

            // it's our turn
            while (true) {
                while (true) {
                    task = workQueue.poll();
                    if (task == null) {
                        break;
                    }
                    task.run();
                }

                if (workState.compareAndSet(WorkState.WORKING_THEN_CLEAN, WorkState.CLEAN)) {
                    // done!
                    break;
                }

                // some other thread added a task in the meantime, run it.
                workState.set(WorkState.WORKING_THEN_CLEAN);
            }
        }

        void reserve() {
            submit(this::reserve0);
        }

        void subscribe(@Nullable ByteBufferConsumer consumer, Upstream upstream) {
            submit(() -> subscribe0(consumer, upstream));
        }

        public DelayedExecutionFlow<ByteBuffer> subscribeFull(Upstream specificUpstream) {
            DelayedExecutionFlow<ByteBuffer> flow = DelayedExecutionFlow.create();
            submit(() -> subscribeFull0(flow, specificUpstream, false));
            return flow;
        }

        @Override
        protected void forwardInitialBuffer(@Nullable ByteBufferConsumer subscriber, boolean last) {
            if (buffer != null) {
                if (subscriber != null) {
                    subscriber.add(buffer.snapshot());
                }
                if (last) {
                    buffer = null;
                }
            }
        }

        @Override
        protected ByteBuffer subscribeFullResult(boolean last) {
            if (buffer == null) {
                return ByteBuffer.allocate(0);
            } else {
                ByteBuffer snapshot = buffer.snapshot();
                if (last) {
                    buffer = null;
                }
                return snapshot;
            }
        }

        @Override
        protected void addForward(List<ByteBufferConsumer> consumers) {
            for (ByteBufferConsumer consumer : consumers) {
                consumer.add(adding.asReadOnlyBuffer()); // we want independent positions
            }
        }

        @Override
        protected void addBuffer() {
            if (buffer == null) {
                buffer = new SnapshotByteArrayOutputStream();
            }
            buffer.write(adding);
        }

        @Override
        protected void discardBuffer() {
            buffer = null;
        }

        @Override
        public void add(ByteBuffer buffer) {
            submit(() -> {
                adding = buffer;
                add(buffer.remaining());
                adding = null;
            });
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

    /**
     * {@link ByteArrayOutputStream} implementations that allows taking an efficient snapshot of
     * the current data.
     */
    private static final class SnapshotByteArrayOutputStream extends ByteArrayOutputStream {
        public ByteBuffer snapshot() {
            return ByteBuffer.wrap(buf, 0, count).asReadOnlyBuffer();
        }

        public void write(ByteBuffer buffer) {
            if (buffer.hasArray()) {
                write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } else {
                byte[] b = new byte[buffer.remaining()];
                buffer.get(buffer.position(), b);
                write(b, 0, b.length);
            }
        }
    }

    private enum WorkState {
        CLEAN,
        WORKING_THEN_CLEAN,
        WORKING_THEN_DIRTY
    }
}
