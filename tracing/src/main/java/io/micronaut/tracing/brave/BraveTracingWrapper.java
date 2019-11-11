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
package io.micronaut.tracing.brave;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.tracing.instrument.TracingWrapper;

import javax.inject.Singleton;
import java.util.concurrent.Callable;

/**
 * Builds a {@link TracingWrapper} for Brave using {@link CurrentTraceContext#wrap}.
 *
 * @author dstepanov
 * @since 1.3
 */
@Singleton
@Requires(beans = Tracing.class)
public class BraveTracingWrapper implements TracingWrapper {

    private final CurrentTraceContext currentTraceContext;

    /**
     * Create a tracing wrapper for Brave.
     *
     * @param tracing For wrapping Runnable and Callable
     */
    public BraveTracingWrapper(Tracing tracing) {
        this.currentTraceContext = tracing.currentTraceContext();
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        return currentTraceContext.wrap(runnable);
    }

    @Override
    public <V> Callable<V> wrap(Callable<V> callable) {
        return currentTraceContext.wrap(callable);
    }
}
