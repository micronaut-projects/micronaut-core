/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.netty.reactive.HotObservable;
import io.micronaut.http.server.netty.FormRouteCompleter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * {@link MultiObjectBody} derived from a {@link StreamingByteBody}. Operations are lazy.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class StreamingMultiObjectBody extends ManagedBody<Publisher<?>> implements MultiObjectBody {
    StreamingMultiObjectBody(Publisher<?> publisher) {
        super(publisher);
    }

    @Override
    void release(Publisher<?> value) {
        if (value instanceof HotObservable<?> hot) {
            hot.closeIfNoSubscriber();
        }
    }

    @Override
    public InputStream coerceToInputStream(ByteBufAllocator alloc) {
        PublisherAsBlocking<ByteBuf> publisherAsBlocking = new PublisherAsBlocking<>();
        //noinspection unchecked
        ((Publisher<ByteBuf>) claim()).subscribe(publisherAsBlocking);
        return new PublisherAsStream(publisherAsBlocking);
    }

    @Override
    public Publisher<?> asPublisher() {
        return claim();
    }

    @Override
    public MultiObjectBody mapNotNull(Function<Object, Object> transform) {
        return next(new StreamingMultiObjectBody(Flux.from(prepareClaim()).mapNotNull(transform)));
    }

    @Override
    public void handleForm(FormRouteCompleter formRouteCompleter) {
        prepareClaim().subscribe(formRouteCompleter);
        next(formRouteCompleter);
    }

    /**
     * A subscriber that allows blocking reads from a publisher. Handles resource cleanup properly.
     *
     * @param <T> Stream type
     */
    private static final class PublisherAsBlocking<T> implements Subscriber<T>, Closeable {
        private final Lock lock = new ReentrantLock();
        private final Condition newDataCondition = lock.newCondition();
        /**
         * Set when {@link #take()} is called before {@link #onSubscribe}. {@link #onSubscribe} will
         * immediately request some input.
         */
        private boolean pendingDemand;
        /**
         * Pending object, this field is used to transfer from {@link #onNext} to {@link #take}.
         */
        private T swap;
        /**
         * The upstream subscription.
         */
        private Subscription subscription;
        /**
         * Set by {@link #onComplete} and {@link #onError}.
         */
        private boolean done;
        /**
         * Set by {@link #close}. Further objects will be discarded.
         */
        private boolean closed;
        /**
         * Failure from {@link #onError}.
         */
        private Throwable failure;

        @Override
        public void onSubscribe(Subscription s) {
            boolean pendingDemand;
            lock.lock();
            try {
                this.subscription = s;
                pendingDemand = this.pendingDemand;
            } finally {
                lock.unlock();
            }
            if (pendingDemand) {
                s.request(1);
            }
        }

        @Override
        public void onNext(T o) {
            lock.lock();
            try {
                if (closed) {
                    ReferenceCountUtil.release(o);
                    return;
                }
                swap = o;
                newDataCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onError(Throwable t) {
            lock.lock();
            try {
                if (swap != null) {
                    ReferenceCountUtil.release(swap);
                    swap = null;
                }
                failure = t;
                done = true;
                newDataCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onComplete() {
            lock.lock();
            try {
                done = true;
                newDataCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Get the next object.
         *
         * @return The next object, or {@code null} if the stream is done
         */
        @Nullable
        public T take() throws InterruptedException {
            boolean demanded = false;
            while (true) {
                Subscription subscription;
                lock.lock();
                try {
                    T swap = this.swap;
                    if (swap != null) {
                        this.swap = null;
                        return swap;
                    }
                    if (done) {
                        return null;
                    }
                    if (demanded) {
                        newDataCondition.await();
                    }
                    subscription = this.subscription;
                    if (subscription == null) {
                        pendingDemand = true;
                    }
                } finally {
                    lock.unlock();
                }
                if (!demanded) {
                    demanded = true;
                    if (subscription != null) {
                        subscription.request(1);
                    }
                }
            }
        }

        @Override
        public void close() {
            lock.lock();
            try {
                closed = true;
                if (swap != null) {
                    ReferenceCountUtil.release(swap);
                    swap = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class PublisherAsStream extends InputStream {
        private final PublisherAsBlocking<ByteBuf> publisherAsBlocking;
        private ByteBuf buffer;

        private PublisherAsStream(PublisherAsBlocking<ByteBuf> publisherAsBlocking) {
            this.publisherAsBlocking = publisherAsBlocking;
        }

        @Override
        public int read() throws IOException {
            byte[] arr = new byte[1];
            int n = read(arr);
            return n == -1 ? -1 : arr[0] & 0xff;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            while (buffer == null) {
                try {
                    ByteBuf o = publisherAsBlocking.take();
                    if (o == null) {
                        if (publisherAsBlocking.failure == null) {
                            return -1;
                        } else {
                            throw new IOException(publisherAsBlocking.failure);
                        }
                    }
                    if (!o.isReadable()) {
                        continue;
                    }
                    buffer = o;
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }

            int toRead = Math.min(len, buffer.readableBytes());
            buffer.readBytes(b, off, toRead);
            if (!buffer.isReadable()) {
                buffer.release();
                buffer = null;
            }
            return toRead;
        }

        @Override
        public void close() throws IOException {
            if (buffer != null) {
                buffer.release();
                buffer = null;
            }
            publisherAsBlocking.close();
        }
    }
}
