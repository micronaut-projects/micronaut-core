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
package io.micronaut.tracing.brave.log;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId", "spanId" and "spanExportable"
 * when a {@link brave.Tracer#currentSpan() span is current}. These can be used in log
 * correlation. Supports backward compatibility of MDC entries by adding legacy "X-B3"
 * entries to MDC context "X-B3-TraceId", "X-B3-ParentSpanId", "X-B3-SpanId" and
 * "X-B3-Sampled"
 *
 * Forked from https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/log/Slf4jScopeDecorator.java
 *
 * @author Marcin Grzejszczak
 * @author Graeme Rocher
 * @since 1.0.0
 */
final class Slf4jScopeDecorator implements CurrentTraceContext.ScopeDecorator {

    // Backward compatibility for all logging patterns
    private static final String LEGACY_EXPORTABLE_NAME = "X-Span-Export";

    private static final String LEGACY_PARENT_ID_NAME = "X-B3-ParentSpanId";

    private static final String LEGACY_TRACE_ID_NAME = "X-B3-TraceId";

    private static final String LEGACY_SPAN_ID_NAME = "X-B3-SpanId";

    private static final Logger LOG = LoggerFactory.getLogger(Slf4jScopeDecorator.class);

    @Override
    public CurrentTraceContext.Scope decorateScope(TraceContext currentSpan,
                                                   CurrentTraceContext.Scope scope) {
        final String previousTraceId = MDC.get("traceId");
        final String previousParentId = MDC.get("parentId");
        final String previousSpanId = MDC.get("spanId");
        final String spanExportable = MDC.get("spanExportable");
        final String legacyPreviousTraceId = MDC.get(LEGACY_TRACE_ID_NAME);
        final String legacyPreviousParentId = MDC.get(LEGACY_PARENT_ID_NAME);
        final String legacyPreviousSpanId = MDC.get(LEGACY_SPAN_ID_NAME);
        final String legacySpanExportable = MDC.get(LEGACY_EXPORTABLE_NAME);

        if (currentSpan != null) {
            String traceIdString = currentSpan.traceIdString();
            MDC.put("traceId", traceIdString);
            MDC.put(LEGACY_TRACE_ID_NAME, traceIdString);
            String parentId = currentSpan.parentId() != null
                    ? HexCodec.toLowerHex(currentSpan.parentId()) : null;
            replace("parentId", parentId);
            replace(LEGACY_PARENT_ID_NAME, parentId);
            String spanId = HexCodec.toLowerHex(currentSpan.spanId());
            MDC.put("spanId", spanId);
            MDC.put(LEGACY_SPAN_ID_NAME, spanId);
            String sampled = String.valueOf(currentSpan.sampled());
            MDC.put("spanExportable", sampled);
            MDC.put(LEGACY_EXPORTABLE_NAME, sampled);
            log("Starting scope for span: {}", currentSpan);
            if (currentSpan.parentId() != null && LOG.isTraceEnabled()) {
                LOG.trace("With parent: {}", currentSpan.parentId());
            }
        } else {
            MDC.remove("traceId");
            MDC.remove("parentId");
            MDC.remove("spanId");
            MDC.remove("spanExportable");
            MDC.remove(LEGACY_TRACE_ID_NAME);
            MDC.remove(LEGACY_PARENT_ID_NAME);
            MDC.remove(LEGACY_SPAN_ID_NAME);
            MDC.remove(LEGACY_EXPORTABLE_NAME);
        }

        /**
         * Thread context scope.
         *
         * @author Adrian Cole
         */
        class ThreadContextCurrentTraceContextScope implements CurrentTraceContext.Scope {

            @Override
            public void close() {
                log("Closing scope for span: {}", currentSpan);
                scope.close();
                replace("traceId", previousTraceId);
                replace("parentId", previousParentId);
                replace("spanId", previousSpanId);
                replace("spanExportable", spanExportable);
                replace(LEGACY_TRACE_ID_NAME, legacyPreviousTraceId);
                replace(LEGACY_PARENT_ID_NAME, legacyPreviousParentId);
                replace(LEGACY_SPAN_ID_NAME, legacyPreviousSpanId);
                replace(LEGACY_EXPORTABLE_NAME, legacySpanExportable);
            }

        }

        return new ThreadContextCurrentTraceContextScope();
    }

    private void log(String text, TraceContext span) {
        if (span == null) {
            return;
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace(text, span);
        }
    }

    private static void replace(String key, @Nullable String value) {
        if (value != null) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }

}
