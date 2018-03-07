/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package io.micronaut.core.async.subscriber;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Subscriber} that tracks completion state using a {@link AtomicBoolean}
 *
 * @param <T> the type of element signaled.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class CompletionAwareSubscriber<T> implements Subscriber<T>, Emitter<T> {
    private final AtomicBoolean complete = new AtomicBoolean(false);

    protected Subscription subscription;

    @Override
    public final void onSubscribe(Subscription s) {
        subscription = s;
        doOnSubscribe(subscription);
    }

    public boolean isComplete() {
        return complete.get();
    }

    @Override
    public final void onNext(T t) {
        if(!complete.get()) {
            try {
                doOnNext(t);
            } catch (Throwable e) {
                onError(e);
            }
        }
    }

    @Override
    public final void onError(Throwable t) {
        if(subscription != null && complete.compareAndSet(false, true)) {
            subscription.cancel();
            doOnError(t);
        }
    }

    @Override
    public final void onComplete() {
        if(complete.compareAndSet(false, true)) {
            try {
                doOnComplete();
            } catch (Exception e) {
                doOnError(e);
            }
        }
    }
    /**
     * Implement {@link Subscriber#onSubscribe(Subscription)}
     */
    protected abstract void doOnSubscribe(Subscription subscription);

    /**
     * Implement {@link Subscriber#onNext(Object)}
     */
    protected abstract void doOnNext(T message);

    /**
     * Implement {@link Subscriber#onError(Throwable)}
     */
    protected abstract void doOnError(Throwable t);

    /**
     * Implement {@link Subscriber#onComplete()}
     */
    protected abstract void doOnComplete();
}
