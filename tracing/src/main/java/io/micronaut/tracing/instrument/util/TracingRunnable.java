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

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;

/**
 * Instruments a Runnable.
 *
 * @author graemerocher
 * @since 1.0
 */
public class TracingRunnable implements Runnable {

    private final Runnable runnable;
    private final Tracer tracer;
    private final Span span;

    /**
     * Create enhanced {@link Runnable} with tracing.
     *
     * @param runnable Runnable
     * @param tracer For span creation and propagation across arbitrary transports
     */
    public TracingRunnable(Runnable runnable, Tracer tracer) {
        this.runnable = runnable;
        this.tracer = tracer;
        this.span = getSpan(tracer);
    }

    @Override
    public void run() {
        Scope scope = null;
        if (span != null) {
            scope = tracer.scopeManager().activate(span, false);
        }
        try {
            runnable.run();
        } finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    private Span getSpan(Tracer tracer) {
        Scope active = tracer.scopeManager().active();
        if (active != null) {
            return active.span();
        } else {
            return tracer.activeSpan();
        }
    }
}
