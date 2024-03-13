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
package io.micronaut.core.async.publisher;

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * This is a {@link Processor} that does not change the stream, but allows the upstream
 * {@link org.reactivestreams.Publisher} and downstream {@link Subscriber} to be set independently
 * in any order. If the upstream is set first, this class makes sure that any early completion
 * is held back until the downstream has finished {@code onSubscribe}. If the downstream is set
 * first, this class makes sure any demand is stored until the upstream becomes available.
 *
 * @param <T> The forwarded type
 * @since 4.4.0
 * @author Jonas Konrad
 */
@Internal
public final class DelayedSubscriber<T> implements Processor<T, T>, Subscription {
    private static final Object COMPLETE = new Object();

    private boolean wip;

    private Subscription upstream;
    private Subscriber<? super T> downstream;
    private Object completion;
    private long demand;
    private boolean cancel;

    @Override
    public void subscribe(Subscriber<? super T> s) {
        s.onSubscribe(this);
        synchronized (this) {
            this.downstream = s;
        }
        work();
    }

    @Override
    public void onSubscribe(Subscription s) {
        synchronized (this) {
            this.upstream = s;
        }
        work();
    }

    @Override
    public void onNext(T t) {
        Subscriber<? super T> downstream = this.downstream;
        if (downstream == null) {
            throw new IllegalStateException("onNext before legitimate request");
        }
        downstream.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        synchronized (this) {
            completion = t;
        }
        work();
    }

    @Override
    public void onComplete() {
        synchronized (this) {
            completion = COMPLETE;
        }
        work();
    }

    @Override
    public void request(long n) {
        synchronized (this) {
            demand = Math.max(demand + n, demand);
        }
        work();
    }

    @Override
    public void cancel() {
        synchronized (this) {
            cancel = true;
        }
        work();
    }

    private void work() {
        boolean holdingWip = false;
        while (true) {
            Object completion = null;
            boolean cancel = false;
            long demand = 0;
            synchronized (this) {
                if (!holdingWip) {
                    if (wip) {
                        return;
                    }
                    wip = true;
                    holdingWip = true;
                }

                if (this.completion != null && downstream != null) {
                    completion = this.completion;
                    this.completion = null;
                } else if (this.demand != 0 && upstream != null && downstream != null) {
                    demand = this.demand;
                    this.demand = 0;
                } else if (this.cancel && upstream != null) {
                    cancel = true;
                    this.cancel = false;
                } else {
                    // nothing to do
                    wip = false;
                    return;
                }
            }

            if (completion != null) {
                if (completion == COMPLETE) {
                    downstream.onComplete();
                } else {
                    downstream.onError((Throwable) completion);
                }
            } else if (demand != 0) {
                upstream.request(demand);
            } else {
                assert cancel;
                upstream.cancel();
            }
        }
    }
}
