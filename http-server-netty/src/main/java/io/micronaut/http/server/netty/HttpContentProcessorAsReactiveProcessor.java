/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.http.netty.stream.StreamedHttpMessage;
import io.netty.buffer.ByteBufHolder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Extension of {@link HttpContentProcessorAsReactiveSubscriber} that is also a {@link Publisher}.
 * This class is a bit more complicated because it has to deal with downstream demand, possibly
 * concurrently.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class HttpContentProcessorAsReactiveProcessor extends HttpContentProcessorAsReactiveSubscriber implements Processor<ByteBufHolder, Object> {
    private static final Object END = new Object();

    // we use this essentially like a lock, except it's *not* reentrant
    private final Semaphore forwardLock = new Semaphore(1);
    private volatile boolean dirty;

    private final Queue<Object> guardedQueue = new ArrayDeque<>(1);

    private Subscriber<? super Object> downstream;
    private final AtomicLong downstreamDemand = new AtomicLong(0);
    private volatile boolean downstreamCancelled = false;
    private boolean upstreamDemanded = false;

    HttpContentProcessorAsReactiveProcessor(HttpContentProcessor processor) {
        super(processor);
    }

    /**
     * Subscribe to the {@link StreamedHttpMessage} in the given request, and return a
     * {@link Publisher} that will produce the processed items.<br>
     * This exists mostly for compatibility with the old {@link HttpContentProcessor}, which was a
     * {@link Processor}.
     *
     * @param processor The content processor to use
     * @param request The request to subscribe to
     * @return The publisher producing output data
     * @param <T> The output element type
     */
    @SuppressWarnings("unchecked")
    public static <T> Publisher<T> asPublisher(HttpContentProcessor processor, NettyHttpRequest<?> request) {
        StreamedHttpMessage streamed = (StreamedHttpMessage) request.getNativeRequest();
        Processor<ByteBufHolder, ?> reactiveProcessor = new HttpContentProcessorAsReactiveProcessor(processor);
        streamed.subscribe(reactiveProcessor);
        return (Publisher<T>) reactiveProcessor;
    }

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
