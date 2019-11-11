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
import io.micronaut.tracing.instrument.TracingWrapper;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;

import javax.inject.Singleton;
import java.util.concurrent.Callable;

/**
 * Builds a {@link TracingWrapper} for OpenTracing.
 *
 * @author dstepanov
 * @since 1.3
 */
@Singleton
@Requires(beans = Tracer.class)
@Requires(missingBeans = TracingWrapper.class)
@Requires(missingBeans = NoopTracer.class)
public class OpenTracingWrapper implements TracingWrapper {

    private final Tracer tracer;

    /**
     * Create enhanced {@link Runnable} and {@link Callable} with tracing.
     *
     * @param tracer For span creation and propagation across arbitrary transports
     */
    public OpenTracingWrapper(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        Span span = tracer.scopeManager().activeSpan();
        return () -> {
            if (span != null) {
                final ScopeManager scopeManager = tracer.scopeManager();
                if (scopeManager.activeSpan() != span) {
                    try (Scope ignored = scopeManager.activate(span)) {
                        runnable.run();
                    }
                } else {
                    runnable.run();
                }
            } else {
                runnable.run();
            }
        };
    }

    @Override
    public <V> Callable<V> wrap(Callable<V> callable) {
        Span span = tracer.scopeManager().activeSpan();
        return () -> {
            if (span == null || span == tracer.scopeManager().activeSpan()) {
                return callable.call();
            } else {
                try (Scope ignored = tracer.scopeManager().activate(span)) {
                    return callable.call();
                }
            }
        };
    }
}
