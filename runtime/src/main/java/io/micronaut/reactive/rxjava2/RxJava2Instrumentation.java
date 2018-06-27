/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

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
class RxJava2Instrumentation implements Function<Runnable, Runnable> {

    private final RunnableInstrumenter[] instrumenters;

    /**
     * Creates a new instance.
     *
     * @param instrumenters The instrumenters for the {@link Runnable} interface
     */
    public RxJava2Instrumentation(RunnableInstrumenter... instrumenters) {
        this.instrumenters = instrumenters;
    }

    /**
     * Initialize RxJava2 instrumentation.
     */
    @PostConstruct
    void init() {
        Function<? super Runnable, ? extends Runnable> existing = RxJavaPlugins.getScheduleHandler();
        if (existing != null && !(existing instanceof RxJava2Instrumentation)) {
            RxJavaPlugins.setScheduleHandler(runnable -> this.apply(existing.apply(runnable)));
        } else {
            RxJavaPlugins.setScheduleHandler(this);
        }
    }

    @Override
    public Runnable apply(Runnable runnable) throws Exception {
        Runnable newRunnable = runnable;
        for (RunnableInstrumenter instrumenter : instrumenters) {
            newRunnable = instrumenter.instrument(newRunnable);
        }
        return newRunnable;
    }
}
