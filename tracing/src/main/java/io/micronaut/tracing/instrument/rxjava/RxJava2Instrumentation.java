/*
 * Copyright 2018 original authors
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
package io.micronaut.tracing.instrument.rxjava;


import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

/**
 * Enables RxJava 2 instrumentation
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Context
@Requires(beans = RxJavaRunnableInstrumenter.class)
public class RxJava2Instrumentation {

    @PostConstruct
    void init(RxJavaRunnableInstrumenter instrumenter) {
        if(instrumenter != null) {
            Function<? super Runnable, ? extends Runnable> existing = RxJavaPlugins.getScheduleHandler();
            if(existing != null && !(existing instanceof RxJavaRunnableInstrumenter)) {
                RxJavaPlugins.setScheduleHandler(runnable -> instrumenter.apply(existing.apply(runnable)));
            }
            else {
                RxJavaPlugins.setScheduleHandler(instrumenter::apply);
            }
        }
    }

}
