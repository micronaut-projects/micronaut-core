/*
 * Copyright 2018 original authors
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
package io.micronaut.tracing.instrument.http;

/**
 * Constants used to store Span objects within instrumented request attributes
 *
 * @author graemerocher
 * @since 1.0
 */
public interface TraceRequestAttributes {

    /**
     * The prefix used for Micronaut tracing request attributes
     */
    String PREFIX = "micronaut.tracing";
    /**
     * The attribute used to store the current span
     */
    String CURRENT_SPAN = PREFIX + ".currentSpan";

    /**
     * The attribute used to store the current span context
     */
    String CURRENT_SPAN_CONTEXT = PREFIX + ".currentSpanContext";
    /**
     * The attribute used to store the current scope
     */
    String CURRENT_SCOPE = PREFIX + ".currentScope";
}
