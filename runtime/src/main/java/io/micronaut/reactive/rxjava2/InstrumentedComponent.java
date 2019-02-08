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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper methods for instrumented components.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
interface InstrumentedComponent {

    /**
     * Validate the throwable.
     * @param t The throwable
     */
    void onStateError(Throwable t);

    /**
     * Validates the subscriptions.
     *
     * @param upstream The upstream
     * @param subscription The downstream
     * @return True if they are valid
     */
    boolean validate(Subscription upstream, Subscription subscription);

    /**
     * Convert reactive to runnable instrumenters.
     * @param instrumentations The instrumentations
     * @return The runnable instrumenters
     */
    default List<RunnableInstrumenter> toRunnableInstrumenters(Collection<ReactiveInstrumenter> instrumentations) {
        if (CollectionUtils.isEmpty(instrumentations)) {
            return Collections.emptyList();
        }
        return instrumentations.stream()
                .map(ReactiveInstrumenter::newInstrumentation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}
