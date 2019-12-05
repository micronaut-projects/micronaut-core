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
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import io.reactivex.Flowable;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Factory of {@link InvocationInstrumenter} for reactive calls instrumentation.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Singleton
@Context
@Requires(classes = Flowable.class)
@Internal
class RxInstrumenterFactory {

    private final List<ReactiveInvocationInstrumenterFactory> reactiveInvocationInstrumenterFactories;

    /**
     * @param reactiveInvocationInstrumenterFactories invocation instrumenters
     */
    public RxInstrumenterFactory(List<ReactiveInvocationInstrumenterFactory> reactiveInvocationInstrumenterFactories) {
        this.reactiveInvocationInstrumenterFactories = reactiveInvocationInstrumenterFactories;
    }

    /**
     * Check if there are any instumenters present.
     *
     * @return true if there are any instumenters present
     */
    public boolean hasInstrumenters() {
        return !reactiveInvocationInstrumenterFactories.isEmpty();
    }

    /**
     * Created a new {@link InvocationInstrumenter}.
     *
     * @return new {@link InvocationInstrumenter} if instrumentation is required
     */
    public @Nullable InvocationInstrumenter create() {
        List<InvocationInstrumenter> invocationInstrumenter = getReactiveInvocationInstrumenters();
        if (!invocationInstrumenter.isEmpty()) {
            return InvocationInstrumenter.combine(invocationInstrumenter);
        }
        return null;
    }

    /**
     * @return The invocation instrumenters
     */
    private List<InvocationInstrumenter> getReactiveInvocationInstrumenters() {
        return reactiveInvocationInstrumenterFactories.stream()
                .map(ReactiveInvocationInstrumenterFactory::newReactiveInvocationInstrumenter)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

}
