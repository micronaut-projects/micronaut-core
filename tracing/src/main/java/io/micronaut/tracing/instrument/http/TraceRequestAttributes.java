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
package io.micronaut.tracing.instrument.http;

/**
 * Constants used to store Span objects within instrumented request attributes.
 *
 * @author graemerocher
 * @since 1.0
 */
public enum TraceRequestAttributes implements CharSequence {
    /**
     * The attribute used to store the current span.
     */
    CURRENT_SPAN("micronaut.tracing.currentSpan"),

    /**
     * The attribute used to store the current scope.
     */
    CURRENT_SCOPE("micronaut.tracing.currentScope"),

    /**
     * The attribute used to store the current span context.
     */
    CURRENT_SPAN_CONTEXT("micronaut.tracing.currentSpanContext");

    private final String attr;

    /**
     * @param attr request attribute
     */
    TraceRequestAttributes(java.lang.String attr) {
        this.attr = attr;
    }

    @Override
    public int length() {
        return attr.length();
    }

    @Override
    public char charAt(int index) {
        return attr.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return attr.subSequence(start, end);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return attr;
    }
}
