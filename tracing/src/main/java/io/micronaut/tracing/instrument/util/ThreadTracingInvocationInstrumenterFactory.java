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
package io.micronaut.tracing.instrument.util;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;

import javax.inject.Singleton;
import java.util.Optional;

import static io.micronaut.tracing.instrument.util.ThreadTracingInvocationInstrumenterFactory.PROPERTY_INSTRUMENT_THREADS;

/**
 * Enables threads tracing invocation instrumentation.
 */
@Requires(beans = TracingInvocationInstrumenterFactory.class)
@Requires(property = PROPERTY_INSTRUMENT_THREADS, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Singleton
@Internal
class ThreadTracingInvocationInstrumenterFactory implements InvocationInstrumenterFactory {

    public static final String PROPERTY_INSTRUMENT_THREADS = "tracing.instrument-threads";

    private final TracingInvocationInstrumenterFactory tracingInvocationInstrumenterFactory;

    /**
     * Creates new instance.
     *
     * @param tracingInvocationInstrumenterFactory factory to delegate to
     */
    ThreadTracingInvocationInstrumenterFactory(TracingInvocationInstrumenterFactory tracingInvocationInstrumenterFactory) {
        this.tracingInvocationInstrumenterFactory = tracingInvocationInstrumenterFactory;
    }

    /**
     * An optional invocation instrumentation.
     * @return An optional invocation instrumentation.
     */
    @Override
    public Optional<InvocationInstrumenter> newInvocationInstrumenter() {
        return tracingInvocationInstrumenterFactory.newTracingInvocationInstrumenter();
    }
}
