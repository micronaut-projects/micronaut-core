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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Version of {@link Sinks#one()} where cancellation of the {@link Mono} will make future emit
 * calls fail.
 *
 * @param <T> Element type
 */
@Internal
final class CancellableMonoSink<T> implements Publisher<T>, Sinks.One<T>, Subscription, PoolSink<T> {
    private static final Object EMPTY = new Object();

    private final ReentrantLock lock = new ReentrantLock();

    @Nullable
    private final BlockHint blockHint;

    private T value;
    private Throwable failure;
    private boolean complete = false;
    private boolean cancelled = false;
    private Subscriber<? super T> subscriber = null;
    private boolean subscriberWaiting = false;

    CancellableMonoSink(@Nullable BlockHint blockHint) {
        this.blockHint = blockHint;
    }

    @Override
    @Nullable
    public BlockHint getBlockHint() {
        return blockHint;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        lock.lock();
        try {
            if (this.subscriber != null) {
                s.onError(new IllegalStateException("Only one subscriber allowed"));
            }
            subscriber = s;
            subscriber.onSubscribe(this);
        } finally {
            lock.unlock();
        }
    }

    private void tryForward() {
        if (subscriberWaiting && complete && !cancelled) {
            if (failure == null) {
                if (value != EMPTY) {
                    subscriber.onNext(value);
                }
                subscriber.onComplete();
            } else {
                subscriber.onError(failure);
            }
        }
    }

    @NonNull
    @Override
    public Sinks.EmitResult tryEmitValue(T value) {
        lock.lock();
        try {
            if (complete) {
                return Sinks.EmitResult.FAIL_OVERFLOW;
            } else {
                this.value = value;
                complete = true;
                tryForward();
                return Sinks.EmitResult.OK;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void emitValue(T value, @NonNull Sinks.EmitFailureHandler failureHandler) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public Sinks.EmitResult tryEmitEmpty() {
        return tryEmitValue((T) EMPTY);
    }

    @NonNull
    @Override
    public Sinks.EmitResult tryEmitError(@NonNull Throwable error) {
        lock.lock();
        try {
            if (complete) {
                return Sinks.EmitResult.FAIL_OVERFLOW;
            } else {
                this.failure = error;
                complete = true;
                tryForward();
                return Sinks.EmitResult.OK;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void emitEmpty(@NonNull Sinks.EmitFailureHandler failureHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void emitError(@NonNull Throwable error, @NonNull Sinks.EmitFailureHandler failureHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int currentSubscriberCount() {
        lock.lock();
        try {
            return subscriber == null ? 0 : 1;
        } finally {
            lock.unlock();
        }
    }

    @NonNull
    @Override
    public Mono<T> asMono() {
        return Mono.from(this);
    }

    @Override
    public Object scanUnsafe(@NonNull Attr key) {
        return null;
    }

    @Override
    public void request(long n) {
        lock.lock();
        try {
            if (n > 0 && !subscriberWaiting) {
                subscriberWaiting = true;
                tryForward();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void cancel() {
        lock.lock();
        try {
            complete = true;
            cancelled = true;
        } finally {
            lock.unlock();
        }
    }
}
