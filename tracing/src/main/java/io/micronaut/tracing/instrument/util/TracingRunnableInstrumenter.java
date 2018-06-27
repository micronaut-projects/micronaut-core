/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.opentracing.Tracer;

import javax.inject.Singleton;
import java.util.function.Function;

/**
 * A function that instruments an existing Runnable with {@link TracingRunnable}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
public class TracingRunnableInstrumenter implements Function<Runnable, Runnable>, RunnableInstrumenter {

    private final Tracer tracer;

    /**
     * Create a function that instrument an existing Runnable.
     *
     * @param tracer For span creation and propagation across arbitrary transports
     */
    public TracingRunnableInstrumenter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Runnable apply(Runnable runnable) {
        return new TracingRunnable(runnable, tracer);
    }

    @Override
    public Runnable instrument(Runnable command) {
        return apply(command);
    }
}
