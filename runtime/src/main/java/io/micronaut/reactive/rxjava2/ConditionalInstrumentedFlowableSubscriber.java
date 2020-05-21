/*
 * Copyright 2017-2020 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.reactivex.FlowableSubscriber;

/**
 * Wrapper subscriber to instrument each {@link FlowableSubscriber} methods with the given
 * {@link ConditionalInstrumenter}.
 * Mainly used in within similar instrumented wrappers i.e. {@link ConditionalInstrumentedPublisher}.
 *
 * @param <T> type of the subscription element
 * @author lgathy
 * @since 2.0
 */
public class ConditionalInstrumentedFlowableSubscriber<T> extends ConditionalInstrumentedSubscriber<T> implements FlowableSubscriber<T> {

    /**
     * Default constructor.
     *
     * @param subscriber   The source subscriber
     * @param instrumenter The instrumenter
     */
    public ConditionalInstrumentedFlowableSubscriber(
            @NonNull FlowableSubscriber<T> subscriber, @NonNull ConditionalInstrumenter instrumenter) {
        super(subscriber, instrumenter);
    }
}
