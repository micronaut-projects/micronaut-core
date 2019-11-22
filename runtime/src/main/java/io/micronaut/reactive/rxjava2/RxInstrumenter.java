/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.reactive.rxjava2;

import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;
import io.reactivex.MaybeObserver;
import io.reactivex.MaybeSource;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.List;

/**
 * Instruments all RX calls if needed.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
public class RxInstrumenter {

    private final InvocationInstrumenter invocationInstrumenter;
    private final List<RunnableInstrumenter> runnableInstrumenters;

    /**
     * Creates new RX Java instrumenter.
     *
     * @param invocationInstrumenter {@link InvocationInstrumenter}
     * @param runnableInstrumenters collection of {@link RunnableInstrumenter}
     */
    public RxInstrumenter(InvocationInstrumenter invocationInstrumenter, List<RunnableInstrumenter> runnableInstrumenters) {
        this.invocationInstrumenter = invocationInstrumenter;
        this.runnableInstrumenters = runnableInstrumenters;
    }

    /**
     * Instrumented method.
     *
     * @param subscriber instrumented method parameter
     * @param next  instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onNext(Subscriber<T> subscriber, T next) {
        try {

            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                subscriber.onNext(next);
            } else {
                invoke(() -> subscriber.onNext(next));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param subscriber instrumented method parameter
     * @param error instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onError(Subscriber<T> subscriber, Throwable error) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                subscriber.onError(error);
            } else {
                invoke(() -> subscriber.onError(error));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param subscriber instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onComplete(Subscriber<T> subscriber) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                subscriber.onComplete();
            } else {
                invoke(subscriber::onComplete);
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param completableObserver instrumented method parameter
     * @param error instrumented method parameter
     */
    public void onError(CompletableObserver completableObserver, Throwable error) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                completableObserver.onError(error);
            } else {
                invoke(() -> completableObserver.onError(error));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param completableObserver instrumented method parameter
     */
    public void onComplete(CompletableObserver completableObserver) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                completableObserver.onComplete();
            } else {
                invoke(completableObserver::onComplete);
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param maybeObserver instrumented method parameter
     * @param t instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onError(MaybeObserver<T> maybeObserver, Throwable t) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                maybeObserver.onError(t);
            } else {
                invoke(() -> maybeObserver.onError(t));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param maybeObserver instrumented method parameter
     * @param value instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onSuccess(MaybeObserver<T> maybeObserver, T value) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                maybeObserver.onSuccess(value);
            } else {
                invoke(() -> maybeObserver.onSuccess(value));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param maybeObserver instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onComplete(MaybeObserver<T> maybeObserver) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                maybeObserver.onComplete();
            } else {
                invoke(maybeObserver::onComplete);
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param singleObserver instrumented method parameter
     * @param t instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onError(SingleObserver<T> singleObserver, Throwable t) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                singleObserver.onError(t);
            } else {
                invoke(() -> singleObserver.onError(t));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param singleObserver instrumented method parameter
     * @param value instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onSuccess(SingleObserver<T> singleObserver, T value) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                singleObserver.onSuccess(value);
            } else {
                invoke(() -> singleObserver.onSuccess(value));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param observer instrumented method parameter
     * @param value instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onNext(Observer<T> observer, T value) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                observer.onNext(value);
            } else {
                invoke(() -> observer.onNext(value));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param observer instrumented method parameter
     * @param t instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onError(Observer<T> observer, Throwable t) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                observer.onError(t);
            } else {
                invoke(() -> observer.onError(t));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param observer instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void onComplete(Observer<T> observer) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                observer.onComplete();
            } else {
                invoke(observer::onComplete);
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param connectableObservable instrumented method parameter
     * @param connection instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void connect(ConnectableObservable<T> connectableObservable, Consumer<? super Disposable> connection) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                connectableObservable.connect(connection);
            } else {
                invoke(() -> connectableObservable.connect(connection));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param connectableFlowable instrumented method parameter
     * @param connection instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void connect(ConnectableFlowable<T> connectableFlowable, Consumer<? super Disposable> connection) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                connectableFlowable.connect(connection);
            } else {
                invoke(() -> connectableFlowable.connect(connection));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param source instrumented method parameter
     * @param o instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void subscribe(SingleSource<T> source, SingleObserver<? super T> o) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                source.subscribe(o);
            } else {
                invoke(() -> source.subscribe(o));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param source instrumented method parameter
     * @param o instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void subscribe(MaybeSource<T> source, MaybeObserver<? super T> o) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                source.subscribe(o);
            } else {
                invoke(() -> source.subscribe(o));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param source instrumented method parameter
     * @param observer instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void subscribe(ConnectableObservable<T> source, Observer<? super T> observer) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                source.subscribe(observer);
            } else {
                invoke(() -> source.subscribe(observer));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param source instrumented method parameter
     * @param observer instrumented method parameter
     */
    public void subscribe(CompletableSource source, CompletableObserver observer) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                source.subscribe(observer);
            } else {
                invoke(() -> source.subscribe(observer));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param source instrumented method parameter
     * @param subscriber instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void subscribe(Publisher<T> source, Subscriber<? super T> subscriber) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                source.subscribe(subscriber);
            } else {
                invoke(() -> source.subscribe(subscriber));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param source instrumented method parameter
     * @param observer instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void subscribe(ObservableSource<T> source, Observer observer) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                source.subscribe(observer);
            } else {
                invoke(() -> source.subscribe(observer));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    /**
     * Instrumented method.
     *
     * @param source instrumented method parameter
     * @param subscribers instrumented method parameter
     * @param <T> instrumented method parameter
     */
    public <T> void subscribe(ParallelFlowable<T> source, Subscriber<? super T>[] subscribers) {
        try {
            invocationInstrumenter.beforeInvocation();
            if (runnableInstrumenters.isEmpty()) {
                source.subscribe(subscribers);
            } else {
                invoke(() -> source.subscribe(subscribers));
            }
        } finally {
            invocationInstrumenter.afterInvocation();
        }
    }

    private void invoke(Runnable runnable) {
        for (RunnableInstrumenter instrumentation : runnableInstrumenters) {
            runnable = instrumentation.instrument(runnable);
        }
        runnable.run();
    }

}


