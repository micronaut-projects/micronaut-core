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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.scheduler.NonBlocking;

import java.io.Closeable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A subscriber that allows blocking reads from a publisher. Handles resource cleanup properly.
 *
 * @param <T> Stream type
 * @since 4.2.0
 * @author Jonas Konrad
 */
@Internal
public class PublisherAsBlocking<T> implements Subscriber<T>, Closeable {
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

    protected void release(T item) {
        // optional resource management for subclasses
    }

    /**
     * The failure from {@link #onError(Throwable)}. When {@link #take()} returns {@code null}, this
     * may be set if the reactive stream ended in failure.
     *
     * @return The failure, or {@code null} if either the stream is not done, or the stream
     * completed successfully.
     */
    @Nullable
    public Throwable getFailure() {
        return failure;
    }

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
                release(o);
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
                release(swap);
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
                    if (Thread.currentThread() instanceof NonBlocking) {
                        throw new IllegalStateException("Attempted to do blocking operation on a thread marked as NonBlocking. (Maybe the netty event loop?) Please only run blocking operations on IO or virtual threads, for example by marking your controller with @ExecuteOn(TaskExecutors.BLOCKING).");
                    }
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
                release(swap);
                swap = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
