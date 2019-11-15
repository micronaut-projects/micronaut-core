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
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import io.opentracing.Scope;
import io.opentracing.Tracer;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Tracing invocation instrument for OpenTracing.
 *
 * @author graemerocher
 * @author dstepanov
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
@Requires(missingBeans = TracingInvocationInstrumenterFactory.class)
public class OpenTracingInvocationInstrumenter implements TracingInvocationInstrumenterFactory, ReactiveInvocationInstrumenterFactory {

    private final Tracer tracer;

    /**
     * Create a tracing invocation instrumenter.
     *
     * @param tracer invocation tracer
     */
    public OpenTracingInvocationInstrumenter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Optional<InvocationInstrumenter> newReactiveInvocationInstrumenter() {
        return newTracingInvocationInstrumenter();
    }

    @Override
    public Optional<InvocationInstrumenter> newTracingInvocationInstrumenter() {
        return Optional.ofNullable(tracer.activeSpan()).map(activeSpan -> new InvocationInstrumenter() {

            Scope activeScope;

            @Override
            public void beforeInvocation() {
                activeScope = tracer.scopeManager().activate(activeSpan);
            }

            @Override
            public void afterInvocation() {
                activeScope.close();
            }

        });
    }
}
