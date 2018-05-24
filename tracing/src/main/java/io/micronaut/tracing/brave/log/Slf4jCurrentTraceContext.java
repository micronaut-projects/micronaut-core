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

package io.micronaut.tracing.brave.log;

import brave.internal.HexCodec;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.annotation.Nullable;

/**
 * A Slf4jCurrentTraceContext based on Spring Sleuth.
 *
 * @author graemerocher
 * @since 1.0
 */

class Slf4jCurrentTraceContext extends CurrentTraceContext {

    public static final String TRACE_ID = "traceId";
    public static final String PARENT_ID = "parentId";
    public static final String SPAN_ID = "spanId";
    public static final String SPAN_EXPORTABLE = "spanExportable";

    private static final Logger LOG = LoggerFactory
            .getLogger(Slf4jCurrentTraceContext.class);

    private final CurrentTraceContext delegate;

    /**
     * Create Slf4j trace context with default implementation.
     */
    Slf4jCurrentTraceContext() {
        this.delegate = CurrentTraceContext.Default.create();
    }

    /**
     * Create Slf4j trace context object with existing implementation.
     *
     * @param delegate The current trace context object
     */
    Slf4jCurrentTraceContext(CurrentTraceContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public TraceContext get() {
        return this.delegate.get();
    }

    @Override
    public Scope newScope(TraceContext currentSpan) {
        final String previousTraceId = MDC.get(TRACE_ID);
        final String previousParentId = MDC.get(PARENT_ID);
        final String previousSpanId = MDC.get(SPAN_ID);
        final String spanExportable = MDC.get(SPAN_EXPORTABLE);

        if (currentSpan != null) {
            String traceIdString = currentSpan.traceIdString();
            MDC.put(TRACE_ID, traceIdString);
            String parentId = currentSpan.parentId() != null ?
                    HexCodec.toLowerHex(currentSpan.parentId()) :
                    null;
            set(PARENT_ID, parentId);
            String spanId = HexCodec.toLowerHex(currentSpan.spanId());
            MDC.put(SPAN_ID, spanId);
            String sampled = String.valueOf(currentSpan.sampled());
            MDC.put(SPAN_EXPORTABLE, sampled);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Starting scope for span: {}", currentSpan);
            }

            if (currentSpan.parentId() != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("With parent: {}", currentSpan.parentId());
                }
            }
        } else {
            MDC.remove(TRACE_ID);
            MDC.remove(PARENT_ID);
            MDC.remove(SPAN_ID);
            MDC.remove(SPAN_EXPORTABLE);
        }

        Scope scope = this.delegate.newScope(currentSpan);

        /**
         * A span remains in the thread context scope it was bound to until close is called.
         */
        class ThreadContextCurrentTraceContextScope implements Scope {
            @Override public void close() {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Closing scope for span: {}", currentSpan);
                }
                scope.close();
                set(TRACE_ID, previousTraceId);
                set(PARENT_ID, previousParentId);
                set(SPAN_ID, previousSpanId);
                set(SPAN_EXPORTABLE, spanExportable);
            }
        }

        return new ThreadContextCurrentTraceContextScope();
    }

    private static void set(String key, @Nullable String value) {
        if (value != null) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }
}
