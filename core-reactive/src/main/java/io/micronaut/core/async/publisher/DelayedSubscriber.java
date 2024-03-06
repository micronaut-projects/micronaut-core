package io.micronaut.core.async.publisher;

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@Internal
public final class DelayedSubscriber<T> implements Processor<T, T> {
    private static final Object COMPLETE = new Object();

    private boolean wip;

    private Subscription upstream;
    private Subscriber<? super T> downstream;
    private Object completion;
    private long demand;
    private boolean cancel;

    @Override
    public void subscribe(Subscriber<? super T> s) {
        s.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                synchronized (DelayedSubscriber.this) {
                    demand = Math.max(demand + n, demand);
                }
                work();
            }

            @Override
            public void cancel() {
                synchronized (DelayedSubscriber.this) {
                    cancel = true;
                }
                work();
            }
        });
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
