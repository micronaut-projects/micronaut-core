/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.visitor.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Exposes the Micronaut processor options of a compilation as system properties, for the visitors
 * that read them from there (in particular micronaut-openapi), and restores the previous values once
 * the compilation is over.
 *
 * <p>The properties are applied when a processor is initialized, and restored when the last processor
 * that {@link #acquire(Object) acquired} them {@link #release(Object) releases} them at the end of
 * processing. Without that, in a long-lived JVM (a Gradle daemon, a test suite, an IDE) the options of
 * one compilation would be seen by every later compilation.</p>
 *
 * <p>A processor that is initialized but never asked to process anything never acquires the
 * properties, so it does not hold them. When a compilation applies its options while nothing holds the
 * properties of a previous compilation, those are restored first.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.16
 */
@Internal
public final class ProcessorOptionsSystemProperties {

    /**
     * The values the properties had before they were applied, {@code null} for an absent property.
     */
    private static final Map<String, @Nullable String> PREVIOUS_VALUES = new HashMap<>();
    private static final Set<Object> HOLDERS = Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable
    private static WeakReference<Object> compilation;

    private ProcessorOptionsSystemProperties() {
    }

    /**
     * Sets the Micronaut options of a compilation as system properties.
     *
     * @param compilation An object identifying the compilation, shared by its processors
     * @param options     The processor options
     */
    public static synchronized void apply(Object compilation, Map<String, String> options) {
        if (HOLDERS.isEmpty() && !isCurrentCompilation(compilation)) {
            // a previous compilation that never processed anything
            restore();
        }
        ProcessorOptionsSystemProperties.compilation = new WeakReference<>(compilation);
        options.forEach((key, value) -> {
            if (key != null && key.startsWith(VisitorContext.MICRONAUT_BASE_OPTION_NAME)) {
                if (!PREVIOUS_VALUES.containsKey(key)) {
                    PREVIOUS_VALUES.put(key, System.getProperty(key));
                }
                System.setProperty(key, value == null ? "" : value);
            }
        });
    }

    /**
     * Marks the properties as used by the given processor until it {@link #release(Object) releases} them.
     *
     * @param processor The processor
     */
    public static synchronized void acquire(Object processor) {
        HOLDERS.add(processor);
    }

    /**
     * Releases the properties acquired by the given processor, restoring the previous values when no
     * other processor holds them.
     *
     * @param processor The processor
     */
    public static synchronized void release(Object processor) {
        if (HOLDERS.remove(processor) && HOLDERS.isEmpty()) {
            restore();
        }
    }

    private static boolean isCurrentCompilation(Object compilation) {
        WeakReference<Object> current = ProcessorOptionsSystemProperties.compilation;
        return current != null && current.get() == compilation;
    }

    private static void restore() {
        PREVIOUS_VALUES.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
        PREVIOUS_VALUES.clear();
        compilation = null;
    }
}
