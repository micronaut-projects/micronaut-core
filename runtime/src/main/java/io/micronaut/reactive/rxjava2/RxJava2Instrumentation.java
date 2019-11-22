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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.*;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Function;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import io.reactivex.plugins.RxJavaPlugins;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Provides a single point of entry for all instrumentations for RxJava 2.x.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Context
@Requires(classes = Flowable.class)
@Internal
@TypeHint(
    value = {
            Completable.class,
            Single.class,
            Flowable.class,
            Maybe.class,
            Observable.class
    }
)
class RxJava2Instrumentation implements Function<Runnable, Runnable>, AutoCloseable {
    private final List<RunnableInstrumenter> runnableInstrumenters;
    private final RxInstrumenterFactory instrumenterFactory;
    private Function<? super Completable, ? extends Completable> oldCompletableHook;
    private Function<? super Maybe, ? extends Maybe> oldMaybeHook;
    private Function<? super Single, ? extends Single> oldSingleHook;
    private Function<? super Observable, ? extends Observable> oldObservableHook;
    private Function<? super Flowable, ? extends Flowable> oldFlowableHook;
    private Function<? super ConnectableFlowable, ? extends ConnectableFlowable> oldConnectableFlowableHook;
    private Function<? super ConnectableObservable, ? extends ConnectableObservable> oldConnectableObservableHook;
    private Function<? super ParallelFlowable, ? extends ParallelFlowable> oldParallelFlowableHook;

    /**
     * Creates a new instance.
     *
     * @param instrumenterFactory             to instrument rx calls
     */
    @Inject
    public RxJava2Instrumentation(RxInstrumenterFactory instrumenterFactory) {
        this.runnableInstrumenters = instrumenterFactory.getRunnableInstrumenters();
        this.instrumenterFactory = instrumenterFactory;
    }

    /**
     * Initialize RxJava2 instrumentation.
     */
    @PostConstruct
    void init() {
        if (instrumenterFactory.hasInstrumenters()) {
            oldCompletableHook =
                    RxJavaPlugins.getOnCompletableAssembly();
            oldMaybeHook = RxJavaPlugins.getOnMaybeAssembly();
            oldSingleHook = RxJavaPlugins.getOnSingleAssembly();
            oldObservableHook =
                    RxJavaPlugins.getOnObservableAssembly();
            oldFlowableHook =
                    RxJavaPlugins.getOnFlowableAssembly();
            oldConnectableFlowableHook =
                    RxJavaPlugins.getOnConnectableFlowableAssembly();
            oldConnectableObservableHook =
                    RxJavaPlugins.getOnConnectableObservableAssembly();
            oldParallelFlowableHook =
                    RxJavaPlugins.getOnParallelAssembly();

            RxJavaPlugins.setOnCompletableAssembly(completable -> {
                final Completable wrapped = RxInstrumentedWrappers.wrap(completable, instrumenterFactory);
                if (oldCompletableHook != null) {
                    return oldCompletableHook.apply(wrapped);
                }
                return wrapped;
            });

            RxJavaPlugins.setOnMaybeAssembly(maybe -> {
                final Maybe wrapped = RxInstrumentedWrappers.wrap(maybe, instrumenterFactory);
                if (oldMaybeHook != null) {
                    return oldMaybeHook.apply(wrapped);
                }
                return wrapped;
            });

            RxJavaPlugins.setOnSingleAssembly(single -> {
                final Single wrapped = RxInstrumentedWrappers.wrap(single, instrumenterFactory);
                if (oldSingleHook != null) {
                    return oldSingleHook.apply(wrapped);
                }
                return wrapped;
            });

            RxJavaPlugins.setOnObservableAssembly(observable -> {
                final Observable wrapped = RxInstrumentedWrappers.wrap(observable, instrumenterFactory);
                if (oldObservableHook != null) {
                    return oldObservableHook.apply(wrapped);
                }
                return wrapped;
            });

            RxJavaPlugins.setOnFlowableAssembly(flowable -> {
                final Flowable wrapped = RxInstrumentedWrappers.wrap(flowable, instrumenterFactory);
                if (oldFlowableHook != null) {
                    return oldFlowableHook.apply(wrapped);
                }
                return wrapped;
            });

            RxJavaPlugins.setOnConnectableFlowableAssembly(connectableFlowable -> {
                final ConnectableFlowable wrapped = RxInstrumentedWrappers.wrap(connectableFlowable, instrumenterFactory);
                if (oldConnectableFlowableHook != null) {
                    return oldConnectableFlowableHook.apply(wrapped);
                }
                return wrapped;
            });

            RxJavaPlugins.setOnConnectableObservableAssembly(connectableObservable -> {
                final ConnectableObservable wrapped = RxInstrumentedWrappers.wrap(connectableObservable, instrumenterFactory);
                if (oldConnectableObservableHook != null) {
                    return oldConnectableObservableHook.apply(connectableObservable);
                }
                return wrapped;
            });

            RxJavaPlugins.setOnParallelAssembly(parallelFlowable -> {
                final ParallelFlowable wrapped = RxInstrumentedWrappers.wrap(parallelFlowable, instrumenterFactory);
                if (oldParallelFlowableHook != null) {
                    return oldParallelFlowableHook.apply(wrapped);
                }
                return wrapped;
            });

        }

        if (!runnableInstrumenters.isEmpty() || instrumenterFactory.hasInstrumenters()) {
            Function<? super Runnable, ? extends Runnable> existing = RxJavaPlugins.getScheduleHandler();
            if (existing != null && !(existing instanceof RxJava2Instrumentation)) {
                RxJavaPlugins.setScheduleHandler(runnable -> this.apply(existing.apply(runnable)));
            } else {
                RxJavaPlugins.setScheduleHandler(this);
            }
        }
    }

    @Override
    public Runnable apply(Runnable runnable) {
        Runnable newRunnable = InvocationInstrumenter.instrument(runnable, getInvocationInstrumenter());
        for (RunnableInstrumenter instrumenter : runnableInstrumenters) {
            newRunnable = instrumenter.instrument(newRunnable);
        }
        return newRunnable;
    }

    private List<InvocationInstrumenter> getInvocationInstrumenter() {
        return instrumenterFactory.getInvocationInstrumenters();
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            RxJavaPlugins.setOnCompletableAssembly(oldCompletableHook);
            RxJavaPlugins.setOnSingleAssembly(oldSingleHook);
            RxJavaPlugins.setOnMaybeAssembly(oldMaybeHook);
            RxJavaPlugins.setOnObservableAssembly(oldObservableHook);
            RxJavaPlugins.setOnFlowableAssembly(oldFlowableHook);
            RxJavaPlugins.setOnConnectableObservableAssembly(oldConnectableObservableHook);
            RxJavaPlugins.setOnConnectableFlowableAssembly(oldConnectableFlowableHook);
            RxJavaPlugins.setOnParallelAssembly(oldParallelFlowableHook);
        } catch (Exception e) {
            // ignore
        }
    }
}
