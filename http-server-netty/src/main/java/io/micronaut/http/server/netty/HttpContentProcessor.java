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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.netty.stream.StreamedHttpMessage;
import io.netty.buffer.ByteBufHolder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A reactive streams {@link org.reactivestreams.Processor} that processes incoming {@link ByteBufHolder} and
 * outputs a given type.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class HttpContentProcessor implements Toggleable {
    private final Queue<Object> out = new ArrayDeque<>(1);

    public abstract void add(ByteBufHolder data) throws Throwable;

    public void complete() throws Throwable {
    }

    public void cancel() throws Throwable {
    }

    protected final void offer(Object o) {
        out.add(o);
    }

    @Nullable
    public final Object poll() {
        return out.poll();
    }

    /**
     * Set the type of the values returned by this processor. Most processors do not respect this
     * setting, but e.g. the {@link io.micronaut.http.server.netty.jackson.JsonContentProcessor}
     * does.
     *
     * @param type The type produced by this processor
     */
    public HttpContentProcessor resultType(Argument<?> type) {
        return this;
    }

    @Internal
    public final Processor<ByteBufHolder, ?> asProcessor() {
        return new ProcessorImpl();
    }

    @Internal
    public final <T> Publisher<T> asPublisher(NettyHttpRequest<?> request) {
        StreamedHttpMessage streamed = (StreamedHttpMessage) request.getNativeRequest();
        Processor<ByteBufHolder, ?> processor = asProcessor();
        streamed.subscribe(processor);
        return (Publisher<T>) processor;
    }

    private abstract class SubscriberImpl implements Subscriber<ByteBufHolder> {
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
            try {
                add(holder);
            } catch (Throwable t) {
                failure = t;
            }
            notifyContentAvailable();
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
            notifyContentAvailable();
        }

        @Override
        public void onComplete() {
            try {
                complete();
            } catch (Throwable t) {
                failure = t;
            }
            done = true;
            notifyContentAvailable();
        }

        final void requestContent() {
            upstream.request(1);
        }

        abstract void notifyContentAvailable();
    }

    private class ProcessorImpl extends SubscriberImpl implements Processor<ByteBufHolder, Object> {
        private static final Object END = new Object();

        // we use this essentially like a lock, but it's *not* reentrant
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
        void notifyContentAvailable() {
            forwardLock.acquireUninterruptibly();
            try {
                while (true) {
                    Object item = poll();
                    if (item == null) {
                        break;
                    }
                    guardedQueue.add(item);
                }
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
