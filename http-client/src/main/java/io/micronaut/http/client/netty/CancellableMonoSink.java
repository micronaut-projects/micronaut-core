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

/**
 * Version of {@link Sinks#one()} where cancellation of the {@link Mono} will make future emit
 * calls fail.
 *
 * @param <T> Element type
 */
@Internal
final class CancellableMonoSink<T> implements Publisher<T>, Sinks.One<T>, Subscription, PoolSink<T> {
    private static final Object EMPTY = new Object();

    @Nullable
    private final BlockHint blockHint;

    private T value;
    private Throwable failure;
    private boolean complete = false;
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
    public synchronized void subscribe(Subscriber<? super T> s) {
        if (this.subscriber != null) {
            s.onError(new IllegalStateException("Only one subscriber allowed"));
        }
        subscriber = s;
        subscriber.onSubscribe(this);
    }

    private void tryForward() {
        if (subscriberWaiting && complete) {
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
    public synchronized Sinks.EmitResult tryEmitValue(T value) {
        if (complete) {
            return Sinks.EmitResult.FAIL_OVERFLOW;
        } else {
            this.value = value;
            complete = true;
            tryForward();
            return Sinks.EmitResult.OK;
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
    public synchronized Sinks.EmitResult tryEmitError(@NonNull Throwable error) {
        if (complete) {
            return Sinks.EmitResult.FAIL_OVERFLOW;
        } else {
            this.failure = error;
            complete = true;
            tryForward();
            return Sinks.EmitResult.OK;
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
    public synchronized int currentSubscriberCount() {
        return subscriber == null ? 0 : 1;
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
    public synchronized void request(long n) {
        if (n > 0 && !subscriberWaiting) {
            subscriberWaiting = true;
            tryForward();
        }
    }

    @Override
    public synchronized void cancel() {
        complete = true;
    }
}
