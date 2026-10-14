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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The buffers of a body read as they arrive, whatever the demand of the subscriber, with the
 * bytes that wait for the subscriber limited: more bytes waiting than the limit fail the
 * buffers with a {@link BufferLengthExceededException}, as the bytes of the publisher streams
 * of the Netty client are limited by {@code max-content-length}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class BufferLimitedPublisher implements Publisher<ReadBuffer> {

    private final Publisher<ReadBuffer> source;
    private final long limit;

    /**
     * @param source The buffers, which the subscriber takes over
     * @param limit  The largest number of bytes that wait for the subscriber
     */
    BufferLimitedPublisher(Publisher<ReadBuffer> source, long limit) {
        this.source = source;
        this.limit = limit;
    }

    @Override
    public void subscribe(Subscriber<? super ReadBuffer> subscriber) {
        source.subscribe(new LimitedSubscriber(subscriber, limit));
    }

    private static final class LimitedSubscriber implements Subscriber<ReadBuffer>, Subscription {
        private final Subscriber<? super ReadBuffer> downstream;
        private final long limit;
        private final AtomicLong requested = new AtomicLong();
        private final AtomicInteger wip = new AtomicInteger();
        private final AtomicReference<@Nullable Subscription> upstream = new AtomicReference<>();

        // guarded by this
        private final ArrayDeque<ReadBuffer> queue = new ArrayDeque<>();
        // guarded by this
        private long queued;
        // guarded by this
        private boolean done;
        // guarded by this
        private @Nullable Throwable error;
        // guarded by this
        private boolean cancelled;
        // only accessed by the drain
        private boolean terminated;

        LimitedSubscriber(Subscriber<? super ReadBuffer> downstream, long limit) {
            this.downstream = downstream;
            this.limit = limit;
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream.set(s);
            downstream.onSubscribe(this);
            // the bytes are read as they arrive, the subscriber takes them when it asks for them
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ReadBuffer buffer) {
            boolean exceeded = false;
            synchronized (this) {
                if (done || cancelled) {
                    buffer.close();
                    return;
                }
                queue.add(buffer);
                queued += buffer.readable();
                if (queued > limit) {
                    exceeded = true;
                    done = true;
                    error = new BufferLengthExceededException(limit, queued);
                }
            }
            if (exceeded) {
                Subscription s = upstream.get();
                if (s != null) {
                    s.cancel();
                }
            }
            drain();
        }

        @Override
        public void onError(Throwable t) {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                error = t;
            }
            drain();
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
            }
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    // the subscriber gets the failure in place of what is left
                    done = true;
                    error = new IllegalArgumentException("§3.9: the number of requested buffers must be positive: " + n);
                }
                Subscription s = upstream.get();
                if (s != null) {
                    s.cancel();
                }
                drain();
                return;
            }
            requested.getAndUpdate(r -> r + n < 0 ? Long.MAX_VALUE : r + n);
            drain();
        }

        @Override
        public void cancel() {
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
            }
            Subscription s = upstream.get();
            if (s != null) {
                s.cancel();
            }
            drain();
        }

        private void drain() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            while (true) {
                while (!terminated) {
                    ReadBuffer next = null;
                    Throwable failure = null;
                    boolean complete = false;
                    synchronized (this) {
                        if (cancelled || error != null) {
                            // the waiting bytes are of nobody now
                            ReadBuffer waiting;
                            while ((waiting = queue.poll()) != null) {
                                waiting.close();
                            }
                            queued = 0;
                            if (cancelled) {
                                terminated = true;
                                break;
                            }
                            failure = error;
                        } else if (!queue.isEmpty()) {
                            if (requested.get() == 0) {
                                break;
                            }
                            next = queue.removeFirst();
                            queued -= next.readable();
                        } else if (done) {
                            complete = true;
                        } else {
                            break;
                        }
                    }
                    if (failure != null) {
                        terminated = true;
                        downstream.onError(failure);
                    } else if (complete) {
                        terminated = true;
                        downstream.onComplete();
                    } else if (next != null) {
                        if (requested.get() != Long.MAX_VALUE) {
                            requested.decrementAndGet();
                        }
                        downstream.onNext(next);
                    }
                }
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    return;
                }
            }
        }
    }
}
