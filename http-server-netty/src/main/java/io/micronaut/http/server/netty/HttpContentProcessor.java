/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.netty.stream.StreamedHttpMessage;
import io.netty.buffer.ByteBufHolder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class represents the first step of the HTTP body parsing pipeline. It transforms
 * {@link ByteBufHolder} instances that come from a
 * {@link io.micronaut.http.netty.stream.StreamedHttpRequest} into parsed objects, e.g. json nodes
 * or form data fragments.<br>
 * Processors are stateful. They can receive repeated calls to {@link #add} with more data,
 * followed by a call to {@link #complete} to finish up. Both of these methods accept a
 * {@link Collection} {@code out} parameter that is populated with the processed items.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class HttpContentProcessor implements Toggleable {
    /**
     * Process more data.
     *
     * @param data The input data
     * @param out The collection to add output items to
     */
    public abstract void add(ByteBufHolder data, Collection<Object> out) throws Throwable;

    /**
     * Finish processing data.
     *
     * @param out The collection to add remaining output items to
     */
    public void complete(Collection<Object> out) throws Throwable {
    }

    /**
     * Cancel processing, clean up any data. After this, there should be no more calls to
     * {@link #add} and {@link #complete}.
     */
    public void cancel() throws Throwable {
    }

    /**
     * Set the type of the values returned by this processor. Most processors do not respect this
     * setting, but e.g. the {@link io.micronaut.http.server.netty.jackson.JsonContentProcessor}
     * does.
     *
     * @param type The type produced by this processor
     * @return This processor, for chaining
     */
    public HttpContentProcessor resultType(Argument<?> type) {
        return this;
    }

    @Internal
    final Processor<ByteBufHolder, ?> asProcessor() {
        return new ProcessorImpl();
    }

    /**
     * Subscribe to the {@link StreamedHttpMessage} in the given request, and return a
     * {@link Publisher} that will produce the processed items.<br>
     * This exists mostly for compatibility with the old {@link HttpContentProcessor}, which was a
     * {@link Processor}.
     *
     * @param request The request to subscribe to
     * @return The publisher producing output data
     * @param <T> The output element type
     */
    @SuppressWarnings("unchecked")
    @Internal
    public final <T> Publisher<T> asPublisher(NettyHttpRequest<?> request) {
        StreamedHttpMessage streamed = (StreamedHttpMessage) request.getNativeRequest();
        Processor<ByteBufHolder, ?> processor = asProcessor();
        streamed.subscribe(processor);
        return (Publisher<T>) processor;
    }

    /**
     * {@link Subscriber} that processes the incoming bytes, and calls
     * {@link #notifyContentAvailable} with any new output.
     */
    private abstract class SubscriberImpl implements Subscriber<ByteBufHolder> {
        final List<Object> outBuffer = new ArrayList<>(1);

        Subscription upstream;
        Throwable failure;
        boolean done;

        @Override
        public void onSubscribe(Subscription s) {
            if (upstream != null) {
                throw new IllegalStateException("Only one upstream subscription allowed");
            }
            upstream = s;
        }

        @Override
        public void onNext(ByteBufHolder holder) {
            outBuffer.clear();
            try {
                add(holder, outBuffer);
            } catch (Throwable t) {
                failure = t;
            }
            notifyContentAvailable(outBuffer);
        }

        @Override
        public void onError(Throwable t) {
            try {
                cancel();
            } catch (Throwable other) {
                t.addSuppressed(other);
            }
            failure = t;
            done = true;
            // cancel does not produce data, but we still need to do this call to process the
            // failure
            notifyContentAvailable(Collections.emptyList());
        }

        @Override
        public void onComplete() {
            outBuffer.clear();
            try {
                complete(outBuffer);
            } catch (Throwable t) {
                failure = t;
            }
            done = true;
            notifyContentAvailable(outBuffer);
        }

        final void requestContent() {
            upstream.request(1);
        }

        abstract void notifyContentAvailable(Collection<Object> out);
    }

    /**
     * Extension of {@link SubscriberImpl} that is also a {@link Publisher}. This class is a bit
     * more complicated because it has to deal with downstream demand, possibly concurrently.
     */
    private class ProcessorImpl extends SubscriberImpl implements Processor<ByteBufHolder, Object> {
        private static final Object END = new Object();

        // we use this essentially like a lock, except it's *not* reentrant
        private final Semaphore forwardLock = new Semaphore(1);
        private volatile boolean dirty;

        private final Queue<Object> guardedQueue = new ArrayDeque<>(1);

        private Subscriber<? super Object> downstream;
        private final AtomicLong downstreamDemand = new AtomicLong(0);
        private volatile boolean downstreamCancelled = false;
        private boolean upstreamDemanded = false;

        private void forward() {
            // pattern: multiple threads can call forward() concurrently. One thread will acquire
            // the forwardLock, and keep working until other threads stop calling forward().
            dirty = true;
            boolean weDemandUpstream = false;
            while (dirty && !downstreamCancelled) {
                if (!forwardLock.tryAcquire()) {
                    break;
                }
                try {
                    dirty = false;
                    if (downstreamCancelled) {
                        break;
                    }
                    while (downstreamDemand.get() > 0 || guardedQueue.peek() == END) {
                        Object item = guardedQueue.poll();
                        if (item == null) {
                            weDemandUpstream = upstream != null;
                            if (!upstreamDemanded && weDemandUpstream) {
                                upstreamDemanded = true;
                            }
                            break;
                        }
                        downstreamDemand.decrementAndGet();
                        if (item == END) {
                            if (failure == null) {
                                downstream.onComplete();
                            } else {
                                // failure is not volatile, but we can piggyback off the queue
                                downstream.onError(failure);
                            }
                            downstreamCancelled = true;
                            break;
                        } else {
                            downstream.onNext(item);
                        }
                    }
                } finally {
                    forwardLock.release();
                }
            }
            if (weDemandUpstream) {
                // have to do this without the lock, it might trigger onNext which also needs
                // the lock
                requestContent();
            }
        }

        @Override
        public void subscribe(Subscriber<? super Object> s) {
            if (downstream != null) {
                throw new IllegalStateException("Only one downstream subscription allowed");
            }
            downstream = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    downstreamDemand.updateAndGet(old -> {
                        if (old + n < 0) {
                            return Long.MAX_VALUE;
                        } else {
                            return old + n;
                        }
                    });
                    forward();
                }

                @Override
                public void cancel() {
                    downstreamCancelled = true;
                }
            });
        }

        @Override
        public void onSubscribe(Subscription s) {
            super.onSubscribe(s);
            forward();
        }

        @Override
        void notifyContentAvailable(Collection<Object> out) {
            forwardLock.acquireUninterruptibly();
            try {
                guardedQueue.addAll(out);
                if (done) {
                    guardedQueue.add(END);
                }
                upstreamDemanded = false;
            } finally {
                forwardLock.release();
            }
            forward();
        }
    }
}
