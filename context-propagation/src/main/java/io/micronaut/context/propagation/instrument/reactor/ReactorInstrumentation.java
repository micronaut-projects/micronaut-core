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
package io.micronaut.context.propagation.instrument.reactor;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * On scheduler hook for the thread to be aware of {@link PropagatedContext}.
 *
 * @author Denis Stepanov
 * @since 3.6.0
 */
@Requires(classes = {Flux.class, Schedulers.Factory.class})
@Context
@Internal
class ReactorInstrumentation {

    private static final String KEY = "MICRONAUT_CONTEXT_PROPAGATION";

    @PostConstruct
    void init() {
        Schedulers.onScheduleHook(KEY, runnable -> PropagatedContext.getOrEmpty().propagate(runnable));
    }

    @PreDestroy
    void removeInstrumentation() {
        Schedulers.removeExecutorServiceDecorator(KEY);
    }

}

