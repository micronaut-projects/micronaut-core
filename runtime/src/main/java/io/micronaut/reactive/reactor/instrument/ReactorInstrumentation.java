/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.reactive.reactor.instrument;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * Instruments Reactor such that the thread factory used by Micronaut is used and instrumentations can be applied to the {@link java.util.concurrent.ScheduledExecutorService}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(sdk = Requires.Sdk.MICRONAUT, version = "2.0.0")
@Requires(classes = {Flux.class, Schedulers.Factory.class})
@Context
@Internal
class ReactorInstrumentation {
    /**
     * Initialize instrumentation for reactor with the tracer and factory.
     *
     * @param instrumenterFactory The instrumenter factory
     */
    @SuppressWarnings("unchecked")
    @PostConstruct
    void init(ReactorInstrumenterFactory instrumenterFactory) {
        if (instrumenterFactory.hasInstrumenters()) {
            Schedulers.onScheduleHook(Environment.MICRONAUT, runnable -> {
                InvocationInstrumenter instrumenter = instrumenterFactory.create();
                if (instrumenter != null) {
                    return () -> {
                        try (Instrumentation ignored = instrumenter.newInstrumentation()) {
                            runnable.run();
                        }
                    };
                }
                return runnable;
            });
            Hooks.onEachOperator(Environment.MICRONAUT, Operators.lift((scannable, coreSubscriber) -> {
                if (coreSubscriber instanceof ReactorSubscriber) {
                    return coreSubscriber;
                }
                InvocationInstrumenter instrumenter = instrumenterFactory.create();
                if (instrumenter != null) {
                    return new ReactorSubscriber<>(instrumenter, coreSubscriber);
                }
                return coreSubscriber;
            }));
        }
    }

    /**
     * Removes the registered instrumentation.
     */
    @PreDestroy
    void removeInstrumentation() {
        Schedulers.removeExecutorServiceDecorator(Environment.MICRONAUT);
        Hooks.resetOnEachOperator(Environment.MICRONAUT);
    }


    @Context
    @Requires(classes = Flux.class)
    @Internal
    static final class ReactorInstrumenterFactory {

        private final List<ReactiveInvocationInstrumenterFactory> reactiveInvocationInstrumenterFactories;

        /**
         * @param reactiveInvocationInstrumenterFactories invocation instrumenters
         */
        ReactorInstrumenterFactory(List<ReactiveInvocationInstrumenterFactory> reactiveInvocationInstrumenterFactories) {
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
        @Nullable
        public InvocationInstrumenter create() {
            List<InvocationInstrumenter> invocationInstrumenter = getReactiveInvocationInstrumenters();
            if (CollectionUtils.isNotEmpty(invocationInstrumenter)) {
                return InvocationInstrumenter.combine(invocationInstrumenter);
            }
            return null;
        }

        /**
         * @return The invocation instrumenters
         */
        private List<InvocationInstrumenter> getReactiveInvocationInstrumenters() {
            List<InvocationInstrumenter> instrumenters = new ArrayList<>(reactiveInvocationInstrumenterFactories.size());
            for (ReactiveInvocationInstrumenterFactory instrumenterFactory : reactiveInvocationInstrumenterFactories) {
                final InvocationInstrumenter instrumenter = instrumenterFactory.newReactiveInvocationInstrumenter();
                if (instrumenter != null) {
                    instrumenters.add(instrumenter);
                }
            }
            return instrumenters;
        }

    }
}
