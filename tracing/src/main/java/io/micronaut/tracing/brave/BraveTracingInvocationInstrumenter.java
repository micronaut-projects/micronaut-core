/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.tracing.brave;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import io.micronaut.tracing.instrument.util.TracingInvocationInstrumenterFactory;

import javax.inject.Singleton;

/**
 * Tracing invocation instrument for Brave.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Singleton
@Requires(beans = Tracing.class)
@Internal
public final class BraveTracingInvocationInstrumenter implements ReactiveInvocationInstrumenterFactory, TracingInvocationInstrumenterFactory {

    private final CurrentTraceContext currentTraceContext;

    /**
     * Create a tracing invocation instrumenter.
     *
     * @param tracing invocation tracer
     */
    public BraveTracingInvocationInstrumenter(Tracing tracing) {
        this.currentTraceContext = tracing.currentTraceContext();
    }

    @Override
    public InvocationInstrumenter newReactiveInvocationInstrumenter() {
        return newTracingInvocationInstrumenter();
    }

    @Override
    public InvocationInstrumenter newTracingInvocationInstrumenter() {
        final TraceContext invocationContext = currentTraceContext.get();
        if (invocationContext != null) {
            return () -> {
                CurrentTraceContext.Scope activeScope = currentTraceContext.maybeScope(invocationContext);
                return cleanup -> activeScope.close();
            };
        }
        return null;
    }
}
