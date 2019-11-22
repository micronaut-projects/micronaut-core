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
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.Flowable;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Factory for {@link RxInstrumenter}.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Singleton
@Context
@Requires(classes = Flowable.class)
@Internal
class RxInstrumenterFactory {

    private final List<ReactiveInvocationInstrumenterFactory> invocationInstrumenterFactories;
    private final List<ReactiveInstrumenter> reactiveInstrumenters;
    private final RxInstrumenter invocationInstrumenter;

    /**
     * @param invocationInstrumenterFactories invocation instrumenters
     * @param reactiveInstrumenters           {@link Runnable} instrument wrappers
     */
    public RxInstrumenterFactory(List<ReactiveInvocationInstrumenterFactory> invocationInstrumenterFactories,
                                 List<ReactiveInstrumenter> reactiveInstrumenters) {
        this.invocationInstrumenterFactories = invocationInstrumenterFactories;
        this.reactiveInstrumenters = reactiveInstrumenters;
        List<InvocationInstrumenter> invocationInstrumenter = getInvocationInstrumenters();
        this.invocationInstrumenter = reactiveInstrumenters.isEmpty() && !invocationInstrumenter.isEmpty() ? new RxInstrumenter(InvocationInstrumenter.combine(invocationInstrumenter), Collections.emptyList()) : null;
    }

    /**
     * Check if there are any instumenters present.
     *
     * @return true if there are any instumenters present
     */
    public boolean hasInstrumenters() {
        return !invocationInstrumenterFactories.isEmpty() || hasReactiveInstrumenters();
    }

    /**
     * @return Whether any reactive instrumenters are configured.
     */
    public boolean hasReactiveInstrumenters() {
        return !reactiveInstrumenters.isEmpty();
    }

    /**
     * Created a new {@link RxInstrumenter}.
     *
     * @return new RxInstrumenter if instrumentation is required
     */
    public @Nullable RxInstrumenter create() {
        if (hasReactiveInstrumenters()) {
            List<RunnableInstrumenter> runnableInstrumenters = getRunnableInstrumenters();
            List<InvocationInstrumenter> invocationInstrumenter = getInvocationInstrumenters();
            if (!runnableInstrumenters.isEmpty() || !invocationInstrumenter.isEmpty()) {
                return new RxInstrumenter(InvocationInstrumenter.combine(invocationInstrumenter), runnableInstrumenters);
            } else {
                return null;
            }
        } else {
            // the singleton instance
            return invocationInstrumenter;
        }
    }

    /**
     * @return The runnable instrumenters
     */
    public List<RunnableInstrumenter> getRunnableInstrumenters() {
        return reactiveInstrumenters.stream()
                .map(ReactiveInstrumenter::newInstrumentation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * @return The invocation instrumenters
     */
    public List<InvocationInstrumenter> getInvocationInstrumenters() {
        return invocationInstrumenterFactories.stream()
                .map(ReactiveInvocationInstrumenterFactory::newReactiveInvocationInstrumenter)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

}
