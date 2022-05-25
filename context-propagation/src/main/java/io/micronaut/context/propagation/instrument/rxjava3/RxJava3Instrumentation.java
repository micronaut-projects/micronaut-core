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
package io.micronaut.context.propagation.instrument.rxjava3;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * On scheduler hook for the thread to be aware of {@link PropagatedContext}.
 *
 * @author Denis Stepanov
 * @since 3.6.0
 */
@Requires(classes = {RxJavaPlugins.class})
@Context
@Internal
class RxJava3Instrumentation {

    Function<? super Runnable, ? extends Runnable> scheduleHandler;

    @PostConstruct
    void init() {
        scheduleHandler = RxJavaPlugins.getScheduleHandler();
        RxJavaPlugins.setScheduleHandler(runnable -> {
            if (scheduleHandler != null) {
                runnable = scheduleHandler.apply(runnable);
            }
            return PropagatedContext.getOrEmpty().propagate(runnable);
        });
    }

    @PreDestroy
    void removeInstrumentation() {
        RxJavaPlugins.setScheduleHandler(scheduleHandler);
    }

}

