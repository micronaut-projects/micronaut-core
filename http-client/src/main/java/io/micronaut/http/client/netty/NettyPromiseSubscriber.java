package io.micronaut.http.client.netty;

import io.netty.util.concurrent.Promise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

final class NettyPromiseSubscriber<T> implements Subscriber<T> {
    private final Promise<? super T> promise;
    private T value;

    public NettyPromiseSubscriber(Promise<? super T> promise) {
        this.promise = promise;
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {
        this.value = t;
    }

    @Override
    public void onError(Throwable t) {
        promise.tryFailure(t);
    }

    @Override
    public void onComplete() {
        promise.trySuccess(value);
    }
}
