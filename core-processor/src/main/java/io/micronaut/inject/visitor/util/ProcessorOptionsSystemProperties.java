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

import javax.annotation.processing.Processor;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Exposes the Micronaut processor options of a compilation as system properties, for the visitors
 * that read them from there (in particular micronaut-openapi), and restores the previous values once
 * the compilation is over.
 *
 * <p>The properties are applied when a processor is initialized, and restored when the last processor
 * that {@link #acquire(Object, Object) acquired} them {@link #release(Object) releases} them at the end of
 * processing. Without that, in a long-lived JVM (a Gradle daemon, a test suite, an IDE) the options of
 * one compilation would be seen by every later compilation.</p>
 *
 * <p>A processor that is initialized but never asked to process anything never acquires the
 * properties, so it does not hold them. When a compilation applies its options while nothing holds the
 * properties of a previous compilation, those are restored first.</p>
 *
 * <p>A compilation can also end without a final round: javac stops the processing, without calling the
 * processors again, when it cannot read a class generated in a round. Its processors then never
 * release the properties. The processing of a compilation runs on a single thread, so when another
 * compilation starts on that thread, not from a processing round, the compilation is over and its
 * processors no longer hold the properties.</p>
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
    private static final Map<Object, Holder> HOLDERS = new IdentityHashMap<>();
    private static final String KSP_SYMBOL_PROCESSOR = "com.google.devtools.ksp.processing.SymbolProcessor";
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
        removeEndedCompilations(compilation);
        if (HOLDERS.isEmpty() && !isCurrentCompilation(compilation)) {
            // a previous compilation that never processed anything, or that ended without a final round
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
     * @param compilation The object identifying the compilation of the processor, as given to {@link #apply(Object, Map)}
     * @param processor   The processor
     */
    public static synchronized void acquire(Object compilation, Object processor) {
        hold(compilation, processor);
    }

    /**
     * Runs a processing round of the given processor while it holds the properties, and releases them
     * after the last round.
     *
     * @param compilation    The object identifying the compilation of the processor, as given to {@link #apply(Object, Map)}
     * @param processor      The processor
     * @param processingOver Whether this is the last round
     * @param round          The processing of the round
     * @return The result of the round
     */
    public static boolean process(Object compilation, Object processor, boolean processingOver, BooleanSupplier round) {
        acquire(compilation, processor);
        try {
            return round.getAsBoolean();
        } finally {
            if (processingOver) {
                release(processor);
            }
        }
    }

    /**
     * Releases the properties acquired by the given processor, restoring the previous values when no
     * other processor holds them.
     *
     * @param processor The processor
     */
    public static synchronized void release(Object processor) {
        if (HOLDERS.remove(processor) != null && HOLDERS.isEmpty()) {
            restore();
        }
    }

    /**
     * Stops holding the properties for the other compilations that ran on this thread: they are over, even
     * if they never had their final round, unless the compilation starts from one of their rounds.
     *
     * @param compilation The compilation that starts
     */
    private static void removeEndedCompilations(Object compilation) {
        Thread thread = Thread.currentThread();
        boolean ended = false;
        for (Holder holder : HOLDERS.values()) {
            if (holder.compilation != compilation && holder.thread == thread) {
                ended = true;
                break;
            }
        }
        if (ended && !isInProcessingRound()) {
            HOLDERS.values().removeIf(holder -> holder.compilation != compilation && holder.thread == thread);
        }
    }

    /**
     * Whether the compilation applying its options is started from a processing round of another one.
     *
     * @return True if the thread is in the round of an annotation processor (javac) or of a symbol processor (KSP)
     */
    private static boolean isInProcessingRound() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
            .dropWhile(frame -> frame.getDeclaringClass() == ProcessorOptionsSystemProperties.class)
            // the processor applying the options, which applies them from its round for KSP
            .skip(1)
            .anyMatch(frame -> "process".equals(frame.getMethodName()) && isProcessor(frame.getDeclaringClass())));
    }

    private static boolean isProcessor(Class<?> type) {
        if (Processor.class.isAssignableFrom(type)) {
            return true;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> anInterface : current.getInterfaces()) {
                if (KSP_SYMBOL_PROCESSOR.equals(anInterface.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void hold(Object compilation, Object processor) {
        Holder holder = HOLDERS.get(processor);
        if (holder == null || holder.compilation != compilation) {
            HOLDERS.put(processor, new Holder(compilation, Thread.currentThread()));
        } else {
            holder.thread = Thread.currentThread();
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

    /**
     * A processor holding the properties.
     */
    private static final class Holder {

        private final Object compilation;
        /**
         * The thread of the last round of the processor.
         */
        private Thread thread;

        private Holder(Object compilation, Thread thread) {
            this.compilation = compilation;
            this.thread = thread;
        }
    }
}
