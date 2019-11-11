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
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.micronaut.tracing.instrument.TracingWrapper;
import io.opentracing.Span;
import io.opentracing.Tracer;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.Function;

/**
 * A function that instruments an existing {@link Runnable} and {@link java.util.concurrent.Callable} with {@link TracingWrapper}.
 *
 * @author graemerocher
 * @author dstepanov
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
@Requires(beans = TracingWrapper.class)
public class TracingRunnableInstrumenter implements Function<Runnable, Runnable>, RunnableInstrumenter, ReactiveInstrumenter {

    private final Tracer tracer;
    private final TracingWrapper tracingWrapper;

    /**
     * Create a function that wraps an existing Runnable.
     *
     * @param tracer For detecting tracing
     * @param tracingWrapper For wrapping runnable
     */
    public TracingRunnableInstrumenter(Tracer tracer, TracingWrapper tracingWrapper) {
        this.tracer = tracer;
        this.tracingWrapper = tracingWrapper;
    }

    @Override
    public Runnable apply(Runnable runnable) {
        return tracingWrapper.wrap(runnable);
    }

    @Override
    public Runnable instrument(Runnable command) {
        return tracingWrapper.wrap(command);
    }

    @Override
    public Optional<RunnableInstrumenter> newInstrumentation() {
        Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            return Optional.of(new RunnableInstrumenter() {
                @Override
                public Runnable instrument(Runnable command) {
                    return () -> tracingWrapper.wrap(command).run();
                }
            });
        }
        return Optional.empty();
    }
}
